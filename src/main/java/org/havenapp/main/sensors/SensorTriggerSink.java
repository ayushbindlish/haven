package org.havenapp.main.sensors;

/**
 * Callback a sensor monitor uses to report a detection. Replaces the old
 * per-monitor {@code bindService} + {@link android.os.Messenger} plumbing so that
 * {@link SensingCoordinator} can start and stop individual monitors cheaply as it
 * moves between power tiers.
 */
public interface SensorTriggerSink {

    /**
     * @param eventTriggerType one of the {@code org.havenapp.main.model.EventTrigger} constants
     * @param value            human-readable detail / media path, may be null
     */
    void onSensorTrigger(int eventTriggerType, String value);
}
