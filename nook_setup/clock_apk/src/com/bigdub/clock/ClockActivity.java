package com.bigdub.clock;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fullscreen E-Ink 91Dub Home Launcher & Appliance Activity for Android 2.1 (API Level 7).
 *
 * Features:
 * - Registers as CATEGORY_HOME so the Nook boots straight into the watchface with zero launcher UI.
 * - Uses hardware AlarmManager.RTC_WAKEUP aligned to :00 minute boundaries (no 1-second JS polling).
 * - Reads real Nook fuel-gauge battery % (default) or remaining day % countdown.
 * - Partial dirty-rect screen redraws via ClockView.invalidate(Rect).
 * - Built-in high-contrast E-Ink settings drawer (tap top 80px or press Menu) including one-tap
 *   Wi-Fi + Wireless ADB toggle and Android Settings / ReLaunch escape buttons.
 */
public class ClockActivity extends Activity implements ClockView.TouchZoneListener {

    public static final String PREFS_NAME = "pebble_91dub_native_prefs";

    private static ClockActivity activeInstance;

    private ClockView clockView;
    private LinearLayout settingsDrawer;
    private KeyguardManager.KeyguardLock keyguardLock;

    // Persisted preferences
    private boolean prefIs24h = true;
    private boolean prefRealBattery = true;
    private boolean prefInvert = false;
    private boolean prefHideBranding = false;
    private int prefOffsetY = -14;
    private int prefGhostMinutes = 360;
    private boolean prefWifiMaint = false;

    // Settings UI controls
    private Button btn12h;
    private Button btn24h;
    private Button btnBatReal;
    private Button btnBatDay;
    private Button btnThemeNormal;
    private Button btnThemeInvert;
    private Button btnLogoShow;
    private Button btnLogoHide;
    private TextView lblOffset;
    private Button btnPowerSaver;
    private Button btnWifiAdb;
    private TextView lblWifiStatus;

    private final BroadcastReceiver systemEventsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (clockView != null) {
                clockView.refreshClock(false);
            }
            updateWifiStatusLabel();
        }
    };

    public static void onAlarmTick() {
        final ClockActivity inst = activeInstance;
        if (inst != null) {
            inst.runOnUiThread(new Runnable() {
                public void run() {
                    if (inst.clockView != null) {
                        inst.clockView.refreshClock(false);
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                keyguardLock = km.newKeyguardLock("91DubClockKeyguard");
                keyguardLock.disableKeyguard();
            }
        } catch (Throwable ignored) {
        }

        loadPrefs();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        clockView = new ClockView(this);
        clockView.setTouchZoneListener(this);
        clockView.applySettings(
            prefIs24h, prefRealBattery, prefInvert,
            prefHideBranding, prefOffsetY, prefGhostMinutes);

        root.addView(clockView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.FILL_PARENT,
            ViewGroup.LayoutParams.FILL_PARENT));

        settingsDrawer = buildSettingsDrawer();
        settingsDrawer.setVisibility(View.GONE);
        root.addView(settingsDrawer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.FILL_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP));

        setContentView(root);

        activeInstance = this;

        if (prefWifiMaint) {
            PowerOptimizer.enableWifiMaintenanceMode(this);
        } else {
            PowerOptimizer.enableBatterySaverMode(this);
        }

        TickReceiver.scheduleNextMinute(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeInstance = this;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(systemEventsReceiver, filter);

        if (clockView != null) {
            clockView.refreshClock(false);
        }
        TickReceiver.scheduleNextMinute(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(systemEventsReceiver);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        if (keyguardLock != null) {
            try { keyguardLock.reenableKeyguard(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    public void onTopBarTapped() {
        toggleSettingsDrawer();
    }

    private void toggleSettingsDrawer() {
        if (settingsDrawer.getVisibility() == View.VISIBLE) {
            closeAndSaveSettings();
        } else {
            syncSettingsButtons();
            updateWifiStatusLabel();
            settingsDrawer.setVisibility(View.VISIBLE);
        }
    }

    private void closeAndSaveSettings() {
        savePrefs();
        settingsDrawer.setVisibility(View.GONE);
        clockView.applySettings(
            prefIs24h, prefRealBattery, prefInvert,
            prefHideBranding, prefOffsetY, prefGhostMinutes);
        clockView.invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleSettingsDrawer();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (settingsDrawer != null && settingsDrawer.getVisibility() == View.VISIBLE) {
                closeAndSaveSettings();
            }
            // As a HOME launcher, consume BACK so Android doesn't exit the home screen
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefIs24h = p.getBoolean("is_24h", true);
        prefRealBattery = p.getBoolean("real_battery", true);
        prefInvert = p.getBoolean("invert", false);
        prefHideBranding = p.getBoolean("hide_branding", false);
        prefOffsetY = p.getInt("offset_y", -14);
        prefGhostMinutes = p.getInt("ghost_minutes", 360);
        prefWifiMaint = p.getBoolean("wifi_maintenance", false);
    }

    private void savePrefs() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        e.putBoolean("is_24h", prefIs24h);
        e.putBoolean("real_battery", prefRealBattery);
        e.putBoolean("invert", prefInvert);
        e.putBoolean("hide_branding", prefHideBranding);
        e.putInt("offset_y", prefOffsetY);
        e.putInt("ghost_minutes", prefGhostMinutes);
        e.putBoolean("wifi_maintenance", prefWifiMaint);
        e.commit();
    }

    private LinearLayout buildSettingsDrawer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);
        panel.setPadding(18, 16, 18, 16);
        panel.setClickable(true);

        TextView title = new TextView(this);
        title.setText("91Dub Clock Settings");
        title.setTextColor(Color.BLACK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 10);
        panel.addView(title);

        panel.addView(makeDivider(2));

        // Row 1: Time Format
        btn12h = makeOptionButton("12H");
        btn24h = makeOptionButton("24H");
        btn12h.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefIs24h = false;
                syncSettingsButtons();
            }
        });
        btn24h.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefIs24h = true;
                syncSettingsButtons();
            }
        });
        panel.addView(makeToggleRow("Time Format:", btn12h, btn24h));

        // Row 2: Battery Gauge Mode
        btnBatReal = makeOptionButton("Real %");
        btnBatDay = makeOptionButton("Day %");
        btnBatReal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefRealBattery = true;
                syncSettingsButtons();
            }
        });
        btnBatDay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefRealBattery = false;
                syncSettingsButtons();
            }
        });
        panel.addView(makeToggleRow("Battery Gauge:", btnBatReal, btnBatDay));

        // Row 3: Outer Theme
        btnThemeNormal = makeOptionButton("Normal");
        btnThemeInvert = makeOptionButton("Invert");
        btnThemeNormal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefInvert = false;
                syncSettingsButtons();
            }
        });
        btnThemeInvert.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefInvert = true;
                syncSettingsButtons();
            }
        });
        panel.addView(makeToggleRow("Outer Bezel:", btnThemeNormal, btnThemeInvert));

        // Row 4: Pebble Branding
        btnLogoShow = makeOptionButton("Show");
        btnLogoHide = makeOptionButton("Hide");
        btnLogoShow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefHideBranding = false;
                syncSettingsButtons();
            }
        });
        btnLogoHide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefHideBranding = true;
                syncSettingsButtons();
            }
        });
        panel.addView(makeToggleRow("Pebble Logo:", btnLogoShow, btnLogoHide));

        // Row 5: Vertical Shift
        LinearLayout shiftRow = makeBaseRow("Vertical Shift:");
        LinearLayout shiftGroup = new LinearLayout(this);
        shiftGroup.setOrientation(LinearLayout.HORIZONTAL);
        shiftGroup.setGravity(Gravity.CENTER_VERTICAL);
        Button btnUp = makeOptionButton("Up");
        lblOffset = new TextView(this);
        lblOffset.setTextColor(Color.BLACK);
        lblOffset.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18);
        lblOffset.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        lblOffset.setPadding(12, 0, 12, 0);
        Button btnDown = makeOptionButton("Down");
        btnUp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefOffsetY -= 4;
                syncSettingsButtons();
            }
        });
        btnDown.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefOffsetY += 4;
                syncSettingsButtons();
            }
        });
        shiftGroup.addView(btnUp);
        shiftGroup.addView(lblOffset);
        shiftGroup.addView(btnDown);
        shiftRow.addView(shiftGroup);
        panel.addView(shiftRow);

        // Row 6: Radio / Power Mode (Battery Saver vs Wi-Fi + ADB Maintenance)
        btnPowerSaver = makeOptionButton("Wi-Fi OFF");
        btnWifiAdb = makeOptionButton("Wi-Fi+ADB");
        btnPowerSaver.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefWifiMaint = false;
                savePrefs();
                PowerOptimizer.enableBatterySaverMode(ClockActivity.this);
                syncSettingsButtons();
                lblWifiStatus.setText("Radio: Disabling Wi-Fi for max battery...");
            }
        });
        btnWifiAdb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefWifiMaint = true;
                savePrefs();
                PowerOptimizer.enableWifiMaintenanceMode(ClockActivity.this);
                syncSettingsButtons();
                lblWifiStatus.setText("Radio: Enabling Wi-Fi & ADB :5555...");
                settingsDrawer.postDelayed(new Runnable() {
                    public void run() {
                        updateWifiStatusLabel();
                    }
                }, 4000);
            }
        });
        panel.addView(makeToggleRow("Power / ADB:", btnPowerSaver, btnWifiAdb));

        lblWifiStatus = new TextView(this);
        lblWifiStatus.setTextColor(Color.BLACK);
        lblWifiStatus.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14);
        lblWifiStatus.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        lblWifiStatus.setPadding(0, 0, 0, 8);
        panel.addView(lblWifiStatus);

        // Row 7: Maintenance Utilities (Clear Ghosting / ReLaunch / System Settings)
        LinearLayout utilRow = new LinearLayout(this);
        utilRow.setOrientation(LinearLayout.HORIZONTAL);
        utilRow.setPadding(0, 4, 0, 8);

        Button btnClear = makeOptionButton("Clear E-Ink");
        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                settingsDrawer.setVisibility(View.GONE);
                clockView.triggerGhostClear();
            }
        });

        Button btnApps = makeOptionButton("Launcher");
        btnApps.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openExternalLauncherOrSettings(true);
            }
        });

        Button btnSys = makeOptionButton("Settings");
        btnSys.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openExternalLauncherOrSettings(false);
            }
        });

        LinearLayout.LayoutParams utilLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        utilLp.setMargins(4, 0, 4, 0);
        utilRow.addView(btnClear, utilLp);
        utilRow.addView(btnApps, utilLp);
        utilRow.addView(btnSys, utilLp);
        panel.addView(utilRow);

        // Close / Save Button
        Button btnDone = new Button(this);
        btnDone.setText("Done / Save");
        btnDone.setBackgroundColor(Color.BLACK);
        btnDone.setTextColor(Color.WHITE);
        btnDone.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20);
        btnDone.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnDone.setPadding(0, 12, 0, 12);
        btnDone.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeAndSaveSettings();
            }
        });
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneLp.setMargins(0, 6, 0, 4);
        panel.addView(btnDone, doneLp);

        panel.addView(makeDivider(4));
        syncSettingsButtons();
        return panel;
    }

    private void openExternalLauncherOrSettings(boolean preferLauncher) {
        if (preferLauncher) {
            String[][] candidates = new String[][] {
                { "com.harasoft.relaunch", "com.harasoft.relaunch.ReLaunch" },
                { "com.gacode.relaunchx", "com.gacode.relaunchx.Main" }
            };
            for (int i = 0; i < candidates.length; i++) {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setComponent(new ComponentName(candidates[i][0], candidates[i][1]));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    settingsDrawer.setVisibility(View.GONE);
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            Intent sys = new Intent(Settings.ACTION_SETTINGS);
            sys.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(sys);
            settingsDrawer.setVisibility(View.GONE);
        } catch (Throwable ignored) {
        }
    }

    private LinearLayout makeBaseRow(String labelText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 6, 0, 6);

        TextView lbl = new TextView(this);
        lbl.setText(labelText);
        lbl.setTextColor(Color.BLACK);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18);
        lbl.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(lbl, lp);
        return row;
    }

    private LinearLayout makeToggleRow(String labelText, Button a, Button b) {
        LinearLayout row = makeBaseRow(labelText);
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(6, 0, 0, 0);
        group.addView(a, bp);
        group.addView(b, bp);
        row.addView(group);
        return row;
    }

    private Button makeOptionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setPadding(14, 8, 14, 8);
        return b;
    }

    private View makeDivider(int heightPx) {
        View v = new View(this);
        v.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.FILL_PARENT, heightPx);
        lp.setMargins(0, 4, 0, 6);
        v.setLayoutParams(lp);
        return v;
    }

    private void styleButton(Button b, boolean active) {
        if (b == null) return;
        if (active) {
            b.setBackgroundColor(Color.BLACK);
            b.setTextColor(Color.WHITE);
        } else {
            b.setBackgroundColor(0xFFE0E0E0);
            b.setTextColor(Color.BLACK);
        }
    }

    private void syncSettingsButtons() {
        styleButton(btn12h, !prefIs24h);
        styleButton(btn24h, prefIs24h);
        styleButton(btnBatReal, prefRealBattery);
        styleButton(btnBatDay, !prefRealBattery);
        styleButton(btnThemeNormal, !prefInvert);
        styleButton(btnThemeInvert, prefInvert);
        styleButton(btnLogoShow, !prefHideBranding);
        styleButton(btnLogoHide, prefHideBranding);
        styleButton(btnPowerSaver, !prefWifiMaint);
        styleButton(btnWifiAdb, prefWifiMaint);
        if (lblOffset != null) {
            lblOffset.setText(prefOffsetY + "px");
        }
    }

    private void updateWifiStatusLabel() {
        if (lblWifiStatus != null) {
            lblWifiStatus.setText("Radio: " + PowerOptimizer.getWifiStatusSummary(this));
        }
    }
}
