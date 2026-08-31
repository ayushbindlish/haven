package org.havenapp.main.sensors;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.Utils;
import org.havenapp.main.model.EventTrigger;

/**
 * Owns every non-camera sensor monitor and decides which ones are running based on the
 * user's {@link PreferenceManager#POWER_MODE} and the current charging state.
 *
 * <ul>
 *   <li><b>CONTINUOUS</b> (also forced whenever charging): every enabled monitor runs all
 *       the time and a partial wake lock is held — this is Haven's classic behaviour.</li>
 *   <li><b>ADAPTIVE / BATTERY_SAVER</b>: only the hardware Significant Motion trigger
 *       (and, in ADAPTIVE, the ambient light sensor) stay armed while idle, with no wake
 *       lock. A trigger promotes to the ACTIVE tier for {@link #ACTIVE_LINGER_MS}, which
 *       brings the accelerometer / barometer / microphone online to catch and record the
 *       rest of the event, then falls back to idle.</li>
 * </ul>
 *
 * The camera is still driven by the monitor Activity in this phase; Phase 3 moves it here.
 */
public class SensingCoordinator implements SensorTriggerSink {

    private static final String TAG = "SensingCoordinator";

    private static final long ACTIVE_LINGER_MS = 12_000L;
    private static final int LOW_BATTERY_PCT = 15;

    private enum Tier { OFF, IDLE, ACTIVE }

    private final Context context;
    private final SensorTriggerSink outSink;
    private final PreferenceManager prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PowerManager.WakeLock wakeLock;

    private Tier tier = Tier.OFF;
    private String effectiveMode = PreferenceManager.POWER_MODE_ADAPTIVE;
    private boolean charging;

    private AccelerometerMonitor accel;
    private BumpMonitor bump;
    private BarometerMonitor baro;
    private AmbientLightMonitor light;
    private MicrophoneMonitor mic;

    private final Runnable lingerDown = this::enterIdle;

    public SensingCoordinator(Context context, SensorTriggerSink outSink) {
        this.context = context.getApplicationContext();
        this.outSink = outSink;
        this.prefs = new PreferenceManager(this.context);

        PowerManager pm = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haven:SensingCoordinator");
        wakeLock.setReferenceCounted(false);
    }

    /* ------------------------------------------------------------------ lifecycle */

    public synchronized void start() {
        charging = Utils.isCharging(context);
        apply();
    }

    public synchronized void stop() {
        handler.removeCallbacks(lingerDown);
        teardownAll();
        releaseWakeLock();
        tier = Tier.OFF;
    }

    /** Called by MonitorService when a power-connected / -disconnected broadcast arrives. */
    public synchronized void onPowerConnectivityChanged(boolean nowCharging) {
        if (tier == Tier.OFF) return;
        if (nowCharging == this.charging) return;
        this.charging = nowCharging;
        String want = resolveMode();
        if (!want.equals(effectiveMode)) {
            Log.i(TAG, "power change -> mode " + effectiveMode + " => " + want);
            teardownAll();
            handler.removeCallbacks(lingerDown);
            apply();
        }
    }

    /** Applies {@link #effectiveMode} from the current {@link #charging} field (never re-reads it). */
    private void apply() {
        effectiveMode = resolveMode();
        Log.i(TAG, "apply(): powerMode=" + prefs.getPowerMode()
                + " charging=" + charging + " -> effective=" + effectiveMode);
        if (PreferenceManager.POWER_MODE_CONTINUOUS.equals(effectiveMode)) {
            enterContinuous();
        } else {
            enterIdle();
        }
    }

    private String resolveMode() {
        if (charging) return PreferenceManager.POWER_MODE_CONTINUOUS;
        int pct = Utils.getBatteryPercentage(context);
        if (pct > 0 && pct <= LOW_BATTERY_PCT) return PreferenceManager.POWER_MODE_BATTERY_SAVER;
        return prefs.getPowerMode();
    }

    /* ---------------------------------------------------------------------- tiers */

    private void enterContinuous() {
        tier = Tier.ACTIVE; // "everything on", but no linger-down
        handler.removeCallbacks(lingerDown);
        acquireWakeLock();
        startBump();
        startAmbient();
        startCheapMotion();
        startMic();
    }

    private void enterIdle() {
        tier = Tier.IDLE;
        handler.removeCallbacks(lingerDown);
        stopCheapMotion();
        stopMic();
        if (!PreferenceManager.POWER_MODE_ADAPTIVE.equals(effectiveMode)) {
            stopAmbient();          // BATTERY_SAVER: nothing but the wake-up trigger
        } else {
            startAmbient();         // ADAPTIVE: light sensor is nearly free and catches doors/lights fast
        }
        startBump();
        releaseWakeLock();
        Log.i(TAG, "-> IDLE");
    }

    private void enterActive(int triggerType, String value) {
        boolean wasIdle = tier != Tier.ACTIVE;
        tier = Tier.ACTIVE;
        acquireWakeLock();
        startAmbient();
        startCheapMotion();
        startMic();
        // re-arm the fall-back timer on every fresh trigger
        handler.removeCallbacks(lingerDown);
        handler.postDelayed(lingerDown, ACTIVE_LINGER_MS);
        if (wasIdle) Log.i(TAG, "-> ACTIVE (" + triggerType + ")");
    }

    /* ------------------------------------------------------------- monitor set-up */

    private void startCheapMotion() {
        if (accel == null && !PreferenceManager.OFF.equals(prefs.getAccelerometerSensitivity())) {
            accel = new AccelerometerMonitor(context, this);
        }
        if (baro == null && prefs.getBarometerActive() && BarometerMonitor.isAvailable(context)) {
            baro = new BarometerMonitor(context, this);
        }
    }

    private void stopCheapMotion() {
        if (accel != null) { accel.stop(context); accel = null; }
        if (baro != null) { baro.stop(context); baro = null; }
    }

    private void startBump() {
        if (bump == null) bump = new BumpMonitor(context, this);
    }

    private void startAmbient() {
        if (light == null && prefs.getAmbientLightActive()) {
            light = new AmbientLightMonitor(context, this);
        }
    }

    private void stopAmbient() {
        if (light != null) { light.stop(context); light = null; }
    }

    private void startMic() {
        if (mic == null && !PreferenceManager.OFF.equals(prefs.getMicrophoneSensitivity())) {
            mic = new MicrophoneMonitor(context, this);
        }
    }

    private void stopMic() {
        if (mic != null) { mic.stop(context); mic = null; }
    }

    private void teardownAll() {
        stopCheapMotion();
        stopAmbient();
        stopMic();
        if (bump != null) { bump.stop(context); bump = null; }
    }

    /* ------------------------------------------------------------------ wake lock */

    private void acquireWakeLock() {
        if (!wakeLock.isHeld()) wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock.isHeld()) wakeLock.release();
    }

    /* ------------------------------------------------------- SensorTriggerSink in */

    @Override
    public void onSensorTrigger(int eventTriggerType, String value) {
        // Always forward so the event is logged / alerted.
        if (outSink != null) outSink.onSensorTrigger(eventTriggerType, value);

        synchronized (this) {
            if (tier == Tier.OFF) return;
            if (PreferenceManager.POWER_MODE_CONTINUOUS.equals(effectiveMode)) return;

            if (tier == Tier.IDLE) {
                enterActive(eventTriggerType, value);
            } else if (eventTriggerType != EventTrigger.MICROPHONE || value == null) {
                // fresh detection while active -> extend the window
                handler.removeCallbacks(lingerDown);
                handler.postDelayed(lingerDown, ACTIVE_LINGER_MS);
            }
        }
    }
}
