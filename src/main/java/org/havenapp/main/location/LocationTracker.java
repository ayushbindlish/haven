package org.havenapp.main.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.havenapp.main.PowerPolicy;
import org.havenapp.main.PreferenceManager;
import org.havenapp.main.alerts.AlertManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Battery-first location: fused provider at BALANCED_POWER, update interval taken from
 * {@link PowerPolicy}, with batching so the app processor wakes rarely. Geofences are
 * evaluated in-process on each fix (cheap at 15-60 min cadence). High-accuracy single
 * fixes only on explicit request (remote LOCATE command).
 */
public class LocationTracker {

    private static final String TAG = "LocationTracker";

    private final Context context;
    private final PreferenceManager prefs;
    private final FusedLocationProviderClient client;
    private final Set<String> inside = new HashSet<>();

    private LocationCallback callback;
    private boolean running;

    public LocationTracker(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = new PreferenceManager(this.context);
        this.client = LocationServices.getFusedLocationProviderClient(this.context);
    }

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    public void start() {
        if (running || !prefs.getLocationTrackingEnabled() || !hasPermission(context)) return;
        running = true;

        long interval = PowerPolicy.locationIntervalMs(PowerPolicy.current(context));
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, interval)
                .setMinUpdateIntervalMillis(interval / 2)
                .setMaxUpdateDelayMillis(interval * 2)   // batch: AP can sleep between
                .setWaitForAccurateLocation(false)
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) onFix(loc);
            }
        };
        try {
            client.requestLocationUpdates(req, callback, Looper.getMainLooper());
            Log.i(TAG, "location updates started, interval=" + interval + "ms");
        } catch (Exception e) {
            Log.e(TAG, "requestLocationUpdates failed", e);
            running = false;
        }
    }

    public void stop() {
        running = false;
        if (callback != null) {
            client.removeLocationUpdates(callback);
            callback = null;
        }
    }

    /** Re-apply the PowerPolicy interval (call on charge / thermal change). */
    public void applyPowerPolicy() {
        if (running) {
            stop();
            start();
        }
    }

    /** One high-accuracy fix for a remote LOCATE request. */
    @SuppressWarnings("MissingPermission")
    public void requestOneShot(@Nullable OnLocation cb) {
        if (!hasPermission(context)) {
            if (cb != null) cb.location(null);
            return;
        }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (cb != null) cb.location(loc);
                })
                .addOnFailureListener(e -> {
                    if (cb != null) cb.location(null);
                });
    }

    public interface OnLocation {
        void location(@Nullable Location loc);
    }

    /**
     * Cheap cached fix (no active request) for auto-arm evaluation. Calls back with null
     * if there's no permission or no cached location — callers should treat that as
     * "not in a trusted place".
     */
    @SuppressWarnings("MissingPermission")
    public static void lastKnown(Context c, OnLocation cb) {
        if (!hasPermission(c)) {
            cb.location(null);
            return;
        }
        try {
            LocationServices.getFusedLocationProviderClient(c.getApplicationContext())
                    .getLastLocation()
                    .addOnSuccessListener(cb::location)
                    .addOnFailureListener(e -> cb.location(null));
        } catch (Exception e) {
            cb.location(null);
        }
    }

    /* --------------------------------------------------------------- geofencing */

    private void onFix(Location loc) {
        if (!prefs.getGeofenceAlertsEnabled()) return;
        AlertManager alerts = new AlertManager(context);
        for (GeofenceStore.Place p : new GeofenceStore(context).all()) {
            float[] d = new float[1];
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), p.lat, p.lng, d);
            boolean now = d[0] <= p.radiusM;
            boolean was = inside.contains(p.name);
            if (now && !was) {
                inside.add(p.name);
                alerts.sendAlert("Arrived at " + p.name, null, -1);
            } else if (!now && was) {
                inside.remove(p.name);
                alerts.sendAlert("Left " + p.name, null, -1);
            }
        }
    }

    public static String format(@Nullable Location loc) {
        if (loc == null) return "location unavailable";
        return String.format(Locale.US, "%.5f, %.5f (±%.0fm)  https://maps.google.com/?q=%.5f,%.5f",
                loc.getLatitude(), loc.getLongitude(), loc.getAccuracy(),
                loc.getLatitude(), loc.getLongitude());
    }
}
