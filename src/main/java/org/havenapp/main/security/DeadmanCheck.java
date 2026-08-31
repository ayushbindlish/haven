package org.havenapp.main.security;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.alerts.AlertManager;
import org.havenapp.main.location.LocationTracker;

/**
 * Dead-man's switch: if the user hasn't opened / unlocked Haven within
 * {@link PreferenceManager#getDeadmanHours()} hours, send an alert with the last known
 * location and optionally wipe.
 */
public final class DeadmanCheck {

    private DeadmanCheck() {}

    public static void run(Context context) {
        Context app = context.getApplicationContext();
        PreferenceManager prefs = new PreferenceManager(app);
        int hrs = prefs.getDeadmanHours();
        if (hrs <= 0) return;

        long last = prefs.getDeadmanCheckin();
        if (last == 0) {
            prefs.markCheckin();
            return;
        }
        long elapsed = System.currentTimeMillis() - last;
        if (elapsed <= hrs * 3_600_000L || prefs.getDeadmanFired()) return;

        prefs.setDeadmanFired(true);
        AlertManager alerts = new AlertManager(app);
        alerts.sendAlert("Dead-man's switch: no check-in for " + hrs + "h on " + Build.MODEL, null, -1);
        new LocationTracker(app).requestOneShot(loc ->
                alerts.sendAlert("Last location: " + LocationTracker.format(loc), null, -1));

        if (prefs.getDeadmanWipe()) {
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && am != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(am::clearApplicationUserData, 5000);
            }
        }
    }
}
