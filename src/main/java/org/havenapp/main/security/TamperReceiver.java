package org.havenapp.main.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.alerts.AlertManager;

import java.util.Calendar;

/**
 * Lightweight tamper tripwires that don't need a running service:
 *  - location services switched off while Haven's location tracking is on
 *  - airplane mode turned on during "quiet hours" (22:00-06:00)
 * Gated by {@link PreferenceManager#getTamperAlertsEnabled()} (default on).
 */
public class TamperReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        PreferenceManager prefs = new PreferenceManager(context);
        if (!prefs.getTamperAlertsEnabled()) return;

        String msg = null;

        if (LocationManager.MODE_CHANGED_ACTION.equals(action) && prefs.getLocationTrackingEnabled()) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null && !lm.isLocationEnabled()) {
                msg = "Location services were turned off";
            }
        } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
            boolean on = intent.getBooleanExtra("state", false);
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (on && (hour >= 22 || hour < 6)) {
                msg = "Airplane mode turned on at " + hour + ":00";
            }
        }

        if (msg != null) {
            try {
                new AlertManager(context.getApplicationContext()).sendAlert(msg, null, -1);
            } catch (Exception ignored) {
            }
        }
    }
}
