package org.havenapp.main.autoarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.havenapp.main.PreferenceManager;

/**
 * Keeps a single exact alarm pointed at the next schedule edge (arm or disarm). The
 * receiver re-evaluates auto-arm and calls {@link #sync} again to roll forward.
 */
public final class AutoArmScheduler {

    private static final String TAG = "AutoArm";
    static final String ACTION_EDGE = "org.havenapp.main.AUTO_ARM_EDGE";
    private static final int REQ = 4310;

    private AutoArmScheduler() {}

    public static void sync(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = PendingIntent.getBroadcast(app, REQ,
                new Intent(app, ScheduleReceiver.class).setAction(ACTION_EDGE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PreferenceManager p = new PreferenceManager(app);
        if (!p.getAutoArmEnabled() || !AutoArmSchedule.hasAny(p)) {
            am.cancel(pi);
            return;
        }

        long next = AutoArmSchedule.nextEdgeMillis(p, System.currentTimeMillis() + 1000L);
        if (next <= 0) {
            am.cancel(pi);
            return;
        }

        try {
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            }
            Log.i(TAG, "next schedule edge in " + ((next - System.currentTimeMillis()) / 60000) + " min"
                    + (exact ? "" : " (inexact - no exact-alarm permission)"));
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }
}
