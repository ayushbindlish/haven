package org.havenapp.main;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

/**
 * Single source of truth for "how hard should Haven work right now". Every periodic task,
 * the camera pipeline, location, ML and the network layer read their knobs from here so
 * the device stays cool and lasts as long as possible.
 *
 * Tier is derived from: charging state, battery %, thermal status, and the user's
 * {@link PreferenceManager#getPowerMode() power mode}.
 */
public final class PowerPolicy {

    public enum Tier { FULL, BALANCED, SAVER, CRITICAL }

    private PowerPolicy() {}

    public static Tier current(Context context) {
        Context app = context.getApplicationContext();
        boolean charging = Utils.isCharging(app);
        int pct = Utils.getBatteryPercentage(app);
        int thermal = thermalStatus(app);          // 0 none .. 6 shutdown
        String mode = new PreferenceManager(app).getPowerMode();

        if (thermal >= 4 /* CRITICAL */ || (!charging && pct >= 0 && pct <= 5)) {
            return Tier.CRITICAL;
        }
        if (thermal == 3 /* SEVERE */
                || (!charging && pct > 0 && pct <= 15)
                || PreferenceManager.POWER_MODE_BATTERY_SAVER.equals(mode)) {
            return Tier.SAVER;
        }
        if (charging && thermal <= 1 && PreferenceManager.POWER_MODE_CONTINUOUS.equals(mode)) {
            return Tier.FULL;
        }
        if (charging && thermal <= 2) {
            return Tier.FULL;
        }
        return Tier.BALANCED;
    }

    /** 0 = NONE, 2 = MODERATE, 3 = SEVERE, 4 = CRITICAL … (API 29+). Falls back to battery temp. */
    public static int thermalStatus(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                try {
                    return pm.getCurrentThermalStatus();
                } catch (Exception ignored) {
                }
            }
        }
        int tempDeci = batteryTempDeciC(context); // tenths of a degree C
        if (tempDeci >= 460) return 4;
        if (tempDeci >= 440) return 3;
        if (tempDeci >= 400) return 2;
        return 0;
    }

    private static int batteryTempDeciC(Context context) {
        Intent s = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return s == null ? 0 : s.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
    }

    /* ---------------------------------------------------------------- feature knobs */

    /** Background location fix interval. Geofences stay armed in the OS regardless. */
    public static long locationIntervalMs(Tier t) {
        switch (t) {
            case FULL:     return 5  * 60_000L;
            case BALANCED: return 15 * 60_000L;
            case SAVER:    return 30 * 60_000L;
            default:       return 60 * 60_000L;
        }
    }

    /** Camera analysis frame rate. */
    public static int cameraAnalysisFps(Tier t) {
        switch (t) {
            case FULL:     return 5;
            case BALANCED: return 3;
            case SAVER:    return 2;
            default:       return 1;
        }
    }

    public static boolean allowVideoCapture(Tier t) {
        return t == Tier.FULL || t == Tier.BALANCED;
    }

    /** On-device ML (person / sound classification) only when there's thermal headroom. */
    public static boolean allowMl(Tier t) {
        return t == Tier.FULL || t == Tier.BALANCED;
    }

    /** One coalesced housekeeping tick (audit + usage + mesh heartbeat + location flush). */
    public static long housekeepingIntervalMs(Tier t) {
        switch (t) {
            case FULL:     return 3  * 3_600_000L;
            case BALANCED: return 6  * 3_600_000L;
            case SAVER:    return 12 * 3_600_000L;
            default:       return 24 * 3_600_000L;
        }
    }

    public static long housekeepingIntervalMs(Context c) {
        return housekeepingIntervalMs(current(c));
    }
}
