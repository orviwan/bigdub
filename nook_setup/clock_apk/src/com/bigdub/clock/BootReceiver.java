package com.bigdub.clock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Starts the 91Dub watchface immediately on boot and applies battery-saving radio settings.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences(
                ClockActivity.PREFS_NAME, Context.MODE_PRIVATE);
            boolean wifiMaint = prefs.getBoolean("wifi_maintenance", false);
            if (wifiMaint) {
                PowerOptimizer.enableWifiMaintenanceMode(context);
            } else {
                PowerOptimizer.enableBatterySaverMode(context);
            }

            TickReceiver.scheduleNextMinute(context);

            Intent launch = new Intent(context, ClockActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(launch);
        }
    }
}
