package org.havenapp.main.autoarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import org.havenapp.main.PreferenceManager;

/**
 * "Phone left undisturbed" detector for auto-arm. With no service running while disarmed
 * there's nothing to observe the screen continuously, so instead a low-frequency alarm
 * samples {@link PowerManager#isInteractive()}: once the screen has been off (and the
 * device not otherwise in a "safe" context) for the configured number of minutes, it asks
 * {@link AutoArmController} to arm.
 */
public final class AwayWatcher {

    private static final String TAG = "AutoArm";
    static final String ACTION_CHECK = "org.havenapp.main.AUTO_ARM_AWAY_CHECK";
    private static final int REQ = 4311;

    private AwayWatcher() {}

    private static long checkIntervalMs(PreferenceManager p) {
        long m = Math.min(p.getAutoArmAwayMinutes(), 10);
        return Math.max(2, m) * 60_000L;
    }

    public static void sync(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = PendingIntent.getBroadcast(app, REQ,
                new Intent(app, AwayCheckReceiver.class).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PreferenceManager p = new PreferenceManager(app);
        if (!p.getAutoArmEnabled() || !p.getAutoArmAwayEnabled()) {
            am.cancel(pi);
            p.setScreenOffTs(0);
            return;
        }

        long next = SystemClock.elapsedRealtime() + checkIntervalMs(p);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, next, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, next, pi);
            }
        } catch (SecurityException ignored) {
        }
    }

    /** Called by {@link AwayCheckReceiver}. */
    static void check(Context c) {
        Context app = c.getApplicationContext();
        PreferenceManager p = new PreferenceManager(app);
        if (!p.getAutoArmEnabled() || !p.getAutoArmAwayEnabled()) return;

        PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        boolean interactive = pm == null || pm.isInteractive();
        long now = System.currentTimeMillis();

        if (interactive) {
            p.setScreenOffTs(0);
            return;
        }
        long off = p.getScreenOffTs();
        if (off == 0) {
            p.setScreenOffTs(now);
            return;
        }
        long neededMs = p.getAutoArmAwayMinutes() * 60_000L;
        if (now - off >= neededMs) {
            Log.i(TAG, "away dwell reached (" + ((now - off) / 60000) + " min screen-off)");
            AutoArmController.evaluate(app, "away-dwell");
        }
    }
}
