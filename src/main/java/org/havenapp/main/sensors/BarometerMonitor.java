package org.havenapp.main.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.havenapp.main.model.EventTrigger;

/**
 * Created by n8fr8 on 3/10/17.
 *
 * Refactored 2026: reports via {@link SensorTriggerSink}; batched registration.
 * Many mid-range devices (e.g. Galaxy M31s) have no barometer — callers should
 * check {@link #isAvailable(Context)} before constructing.
 */
public class BarometerMonitor implements SensorEventListener {

    private final SensorManager sensorMgr;
    private final Sensor sensor;
    private final SensorTriggerSink sink;

    private long lastUpdate = -1;
    private float[] values;
    private float[] last_values;

    private final static int CHECK_INTERVAL = 1000;
    private final static int MAX_REPORT_LATENCY_US = 5_000_000;
    private final int CHANGE_THRESHOLD = 30; // hPa / mbar

    public static boolean isAvailable(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null;
    }

    public BarometerMonitor(Context context, SensorTriggerSink sink) {
        this.sink = sink;
        sensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_PRESSURE);

        if (sensor == null) {
            Log.i("BarometerMonitor", "Warning: no barometer sensor");
        } else {
            sensorMgr.registerListener(this, sensor,
                    SensorManager.SENSOR_DELAY_NORMAL, MAX_REPORT_LATENCY_US);
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Safe not to implement
    }

    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            if ((curTime - lastUpdate) > CHECK_INTERVAL) {
                lastUpdate = curTime;
                values = event.values.clone();

                if (last_values != null) {
                    float diffValue = Math.abs(values[0] - last_values[0]);
                    if (diffValue > CHANGE_THRESHOLD && sink != null) {
                        sink.onSensorTrigger(EventTrigger.PRESSURE, diffValue + "");
                    }
                }
                last_values = values.clone();
            }
        }
    }

    public void stop(Context context) {
        sensorMgr.unregisterListener(this);
    }
}
