package org.havenapp.main.security;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.location.LocationTracker;
import org.havenapp.main.alerts.AlertManager;

/**
 * Runs when the duress PIN is entered: silently notify a trusted channel (with location)
 * and optionally wipe Haven's data, while the UI unlocks normally so a coercer sees
 * nothing unusual.
 */
public final class DuressAction {

    private DuressAction() {}

    public static void fire(Context context) {
        Context app = context.getApplicationContext();
        try {
            AlertManager alerts = new AlertManager(app);
            alerts.sendAlert("⚠ Duress PIN entered on " + Build.MODEL, null, -1);
            new LocationTracker(app).requestOneShot(loc ->
                    alerts.sendAlert("Duress location: " + LocationTracker.format(loc), null, -1));

            if (new PreferenceManager(app).getDuressWipe()) {
                ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && am != null) {
                    // small delay so the alert HTTP call has a chance to leave first
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(am::clearApplicationUserData, 4000);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
