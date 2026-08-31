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
 * Refactored 2026: reports via {@link SensorTriggerSink}; registers with a large
 * maxReportLatency so the sensor hub batches readings and the app processor can sleep.
 */
public class AmbientLightMonitor implements SensorEventListener {

    private final SensorManager sensorMgr;
    private final Sensor sensor;
    private final SensorTriggerSink sink;

    private long lastUpdate = -1;
    private float[] current_values;
    private float[] last_values;

    private final static float LIGHT_CHANGE_THRESHOLD = 100f;
    /** ~5s of hardware batching keeps the AP asleep between wakeups. */
    private final static int MAX_REPORT_LATENCY_US = 5_000_000;
    private final static int CHECK_INTERVAL = 1000;

    public AmbientLightMonitor(Context context, SensorTriggerSink sink) {
        this.sink = sink;
        sensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (sensor == null) {
            Log.i("AmbientLightMonitor", "Warning: no ambient light sensor");
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
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            if ((curTime - lastUpdate) > CHECK_INTERVAL) {
                lastUpdate = curTime;
                current_values = event.values.clone();

                if (last_values != null) {
                    float lightChangedValue = Math.abs(last_values[0] - current_values[0]);
                    if (lightChangedValue > LIGHT_CHANGE_THRESHOLD && sink != null) {
                        sink.onSensorTrigger(EventTrigger.LIGHT, lightChangedValue + "");
                    }
                }
                last_values = current_values.clone();
            }
        }
    }

    public void stop(Context context) {
        sensorMgr.unregisterListener(this);
    }
}
