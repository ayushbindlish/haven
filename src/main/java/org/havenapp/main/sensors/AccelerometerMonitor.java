package org.havenapp.main.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.model.EventTrigger;

/**
 * Created by n8fr8 on 3/10/17.
 *
 * Refactored 2026: reports via {@link SensorTriggerSink} instead of binding to the
 * service; the monitor no longer knows about MonitorService at all.
 */
public class AccelerometerMonitor implements SensorEventListener {

    private final SensorManager sensorMgr;
    private final Sensor accelerometer;
    private final SensorTriggerSink sink;

    private long lastUpdate = -1;
    private float[] accel_values;
    private float[] last_accel_values;

    private int shakeThreshold = -1;

    private float mAccelCurrent = SensorManager.GRAVITY_EARTH;
    private float mAccelLast = SensorManager.GRAVITY_EARTH;
    private float mAccel = 0.00f;

    private final int maxAlertPeriod = 30;
    private int remainingAlertPeriod = 0;
    private boolean alert = false;
    private final static int CHECK_INTERVAL = 100;

    public AccelerometerMonitor(Context context, SensorTriggerSink sink) {
        this.sink = sink;
        PreferenceManager prefs = new PreferenceManager(context);

        try {
            shakeThreshold = Integer.parseInt(prefs.getAccelerometerSensitivity());
        } catch (Exception e) {
            shakeThreshold = 50;
        }

        sensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelerometer == null) {
            Log.i("AccelerometerMonitor", "Warning: no accelerometer");
        } else {
            sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Safe not to implement
    }

    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((curTime - lastUpdate) > CHECK_INTERVAL) {
                lastUpdate = curTime;

                accel_values = event.values.clone();

                if (alert && remainingAlertPeriod > 0) {
                    remainingAlertPeriod = remainingAlertPeriod - 1;
                } else {
                    alert = false;
                }

                if (last_accel_values != null) {
                    mAccelLast = mAccelCurrent;
                    mAccelCurrent = (float) Math.sqrt(accel_values[0] * accel_values[0]
                            + accel_values[1] * accel_values[1]
                            + accel_values[2] * accel_values[2]);
                    float delta = mAccelCurrent - mAccelLast;
                    mAccel = mAccel * 0.9f + delta;

                    if (mAccel > shakeThreshold) {
                        alert = true;
                        remainingAlertPeriod = maxAlertPeriod;
                        if (sink != null) {
                            sink.onSensorTrigger(EventTrigger.ACCELEROMETER, mAccel + "");
                        }
                    }
                }
                last_accel_values = accel_values.clone();
            }
        }
    }

    public void stop(Context context) {
        sensorMgr.unregisterListener(this);
    }
}
