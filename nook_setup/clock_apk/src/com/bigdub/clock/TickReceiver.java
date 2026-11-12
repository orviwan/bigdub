package com.bigdub.clock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * Hardware RTC_WAKEUP minute boundary alarm receiver.
 * Wakes the OMAP3 CPU briefly on exact :00 minute boundaries, triggers a dirty-rect redraw,
 * schedules the next :00 wakeup, and immediately releases its short WakeLock.
 */
public class TickReceiver extends BroadcastReceiver {

    public static final String ACTION_MINUTE_TICK = "com.bigdub.clock.ACTION_MINUTE_TICK";

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = null;
        if (pm != null) {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "91DubTick");
            wl.setReferenceCounted(false);
            wl.acquire(400);
        }

        try {
            ClockActivity.onAlarmTick();
            scheduleNextMinute(context);
        } finally {
            if (wl != null && wl.isHeld()) {
                try { wl.release(); } catch (Throwable ignored) {}
            }
        }
    }

    public static void scheduleNextMinute(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(context, TickReceiver.class);
        i.setAction(ACTION_MINUTE_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(
            context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        long now = System.currentTimeMillis();
        long nextMinute = ((now / 60000L) + 1L) * 60000L + 50L;
        am.set(AlarmManager.RTC_WAKEUP, nextMinute, pi);
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, TickReceiver.class);
        i.setAction(ACTION_MINUTE_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(
            context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
    }
}
