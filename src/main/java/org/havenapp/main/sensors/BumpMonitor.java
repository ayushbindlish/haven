package org.havenapp.main.sensors;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.util.Log;

import org.havenapp.main.model.EventTrigger;

/**
 * Uses the hardware Significant Motion trigger sensor (API 18+), which runs on the
 * low-power sensor hub and wakes the AP only when it fires.
 *
 * Created by rockgecko on 27/12/17. Refactored 2026 to {@link SensorTriggerSink}.
 */
@TargetApi(18)
public class BumpMonitor {

    private final SensorManager sensorMgr;
    private final Sensor bumpSensor;
    private final SensorTriggerSink sink;

    private long lastUpdate = -1;
    private final static int CHECK_INTERVAL = 1000;

    public BumpMonitor(Context context, SensorTriggerSink sink) {
        this.sink = sink;
        sensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        bumpSensor = sensorMgr.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

        if (bumpSensor == null) {
            Log.i("BumpMonitor", "Warning: no significant motion sensor");
        } else {
            boolean registered = sensorMgr.requestTriggerSensor(sensorListener, bumpSensor);
            Log.i("BumpMonitor", "Significant motion sensor registered: " + registered);
        }
    }

    public void stop(Context context) {
        if (bumpSensor != null) {
            sensorMgr.cancelTriggerSensor(sensorListener, bumpSensor);
        }
    }

    private final TriggerEventListener sensorListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            long curTime = System.currentTimeMillis();
            if (event.sensor.getType() == Sensor.TYPE_SIGNIFICANT_MOTION) {
                if ((curTime - lastUpdate) > CHECK_INTERVAL) {
                    lastUpdate = curTime;
                    if (sink != null) {
                        sink.onSensorTrigger(EventTrigger.BUMP, "BUMPED!");
                    }
                }
            }
            // Trigger sensors are one-shot; re-arm.
            sensorMgr.requestTriggerSensor(sensorListener, bumpSensor);
        }
    };
}
