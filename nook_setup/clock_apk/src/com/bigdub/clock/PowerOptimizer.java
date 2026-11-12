package com.bigdub.clock;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

/**
 * Hardware battery reader and root power-saving controller for Nook Simple Touch (BNRV300).
 * Strictly compatible with Android 2.1 (API Level 7).
 */
public final class PowerOptimizer {

    public static final class BatteryState {
        public final int percent;
        public final boolean charging;

        public BatteryState(int percent, boolean charging) {
            this.percent = percent;
            this.charging = charging;
        }
    }

    private static final String[] CAPACITY_PATHS = new String[] {
        "/sys/class/power_supply/max17042-0/capacity",
        "/sys/class/power_supply/battery/capacity",
        "/sys/class/power_supply/bq27510-0/capacity",
        "/sys/class/power_supply/twl4030_bci_battery/capacity"
    };

    private static final String[] STATUS_PATHS = new String[] {
        "/sys/class/power_supply/bq27510-0/status",
        "/sys/class/power_supply/max17042-0/status",
        "/sys/class/power_supply/battery/status",
        "/sys/class/power_supply/bq27510-usb/online",
        "/sys/class/power_supply/bq27510-wall/online",
        "/sys/class/power_supply/usb/online",
        "/sys/class/power_supply/ac/online"
    };

    private PowerOptimizer() {
    }

    /**
     * Reads real Nook hardware battery percentage without maintaining an always-on listener.
     */
    public static BatteryState readHardwareBattery(Context context) {
        int pct = -1;
        boolean charging = false;

        try {
            Intent bat = context.getApplicationContext().registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = bat.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
                int plugged = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                if (level >= 0 && scale > 0) {
                    pct = Math.round((level * 100.0f) / (float) scale);
                }
                charging = (status == BatteryManager.BATTERY_STATUS_CHARGING)
                    || (plugged != 0 && status == BatteryManager.BATTERY_STATUS_FULL && pct < 100);
            }
        } catch (Throwable ignored) {
        }

        if (pct < 0) {
            for (int i = 0; i < CAPACITY_PATHS.length; i++) {
                String line = readFirstLine(CAPACITY_PATHS[i]);
                if (line != null) {
                    try {
                        pct = Integer.parseInt(line.trim());
                        break;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            for (int i = 0; i < STATUS_PATHS.length; i++) {
                String line = readFirstLine(STATUS_PATHS[i]);
                if (line != null) {
                    String s = line.trim().toLowerCase();
                    if ("charging".equals(s) || "1".equals(s)) {
                        charging = true;
                        break;
                    }
                }
            }
        }

        if (pct < 0) pct = 100;
        if (pct > 100) pct = 100;
        return new BatteryState(pct, charging);
    }

    /**
     * Applies ultra-low-power offline settings: disables Wi-Fi radio and clamps CPU max frequency
     * to 600 MHz (stepping down to 300 MHz idle) if rooted.
     */
    public static void enableBatterySaverMode(final Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                wm.setWifiEnabled(false);
            }
        } catch (Throwable ignored) {
        }

        String curMax = readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
        if (curMax != null && "600000".equals(curMax.trim())) {
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                runRootCommands(new String[] {
                    "if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq ]; then " +
                        "echo 300000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null || true; " +
                    "fi",
                    "if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq ]; then " +
                        "echo 600000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null || true; " +
                    "fi"
                });
            }
        }).start();
    }

    /**
     * Enables Wi-Fi and Wireless ADB (port 5555) on demand for remote maintenance.
     * Avoids restarting adbd if port 5555 is already active so existing ADB sessions stay live.
     */
    public static void enableWifiMaintenanceMode(final Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && !wm.isWifiEnabled()) {
                wm.setWifiEnabled(true);
            }
        } catch (Throwable ignored) {
        }

        new Thread(new Runnable() {
            public void run() {
                runRootCommands(new String[] {
                    "if [ \"$(getprop service.adb.tcp.port)\" != \"5555\" ]; then " +
                        "setprop service.adb.tcp.port 5555; " +
                        "stop adbd; " +
                        "start adbd; " +
                    "fi"
                });
            }
        }).start();
    }

    public static String getWifiStatusSummary(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) {
                return "OFF (Battery Saver)";
            }
            WifiInfo info = wm.getConnectionInfo();
            if (info != null) {
                int ip = info.getIpAddress();
                if (ip != 0) {
                    String ipStr = (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                        + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
                    return "ON (" + ipStr + ":5555)";
                }
            }
            return "ON (Connecting...)";
        } catch (Throwable t) {
            return "Unknown";
        }
    }

    /**
     * Triggers hardware E-Ink Pearl waveform clear via sysfs if accessible.
     */
    public static void triggerHardwareEpdClear() {
        final File epd = new File("/sys/class/graphics/fb0/epd_refresh");
        if (epd.canWrite()) {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(epd);
                fos.write(new byte[] { '1', '\n' });
                fos.flush();
                return;
            } catch (Throwable ignored) {
            } finally {
                if (fos != null) {
                    try { fos.close(); } catch (Throwable ignored) {}
                }
            }
        }
        new Thread(new Runnable() {
            public void run() {
                runRootCommands(new String[] {
                    "echo 1 > /sys/class/graphics/fb0/epd_refresh 2>/dev/null || true"
                });
            }
        }).start();
    }

    private static String readFirstLine(String path) {
        File f = new File(path);
        if (!f.exists() || !f.canRead()) return null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f), 64);
            return br.readLine();
        } catch (Throwable t) {
            return null;
        } finally {
            if (br != null) {
                try { br.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void runRootCommands(String[] cmds) {
        Process proc = null;
        DataOutputStream os = null;
        try {
            proc = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(proc.getOutputStream());
            for (int i = 0; i < cmds.length; i++) {
                os.writeBytes(cmds[i] + "\n");
            }
            os.writeBytes("exit\n");
            os.flush();
            proc.waitFor();
        } catch (Throwable ignored) {
        } finally {
            if (os != null) {
                try { os.close(); } catch (Throwable ignored) {}
            }
            if (proc != null) {
                try { proc.destroy(); } catch (Throwable ignored) {}
            }
        }
    }
}
