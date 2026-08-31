package org.havenapp.main.sensors;

/**
 * Created by n8fr8 on 3/10/17.
 * Refactored 2026 to {@link SensorTriggerSink}.
 */

import android.content.Context;
import android.util.Log;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.model.EventTrigger;
import org.havenapp.main.sensors.media.AudioRecorderTask;
import org.havenapp.main.sensors.media.MicSamplerTask;
import org.havenapp.main.sensors.media.MicrophoneTaskFactory;

public final class MicrophoneMonitor implements MicSamplerTask.MicListener {

    private MicSamplerTask microphone;
    private double mNoiseThreshold = 70.0;
    private final SensorTriggerSink sink;
    private final Context context;

    public MicrophoneMonitor(Context context, SensorTriggerSink sink) {
        this.context = context;
        this.sink = sink;

        PreferenceManager prefs = new PreferenceManager(context);

        switch (prefs.getMicrophoneSensitivity()) {
            case "High":
                mNoiseThreshold = 40;
                break;
            case "Medium":
                mNoiseThreshold = 60;
                break;
            default:
                try {
                    mNoiseThreshold = Double.parseDouble(prefs.getMicrophoneSensitivity());
                } catch (Exception ignored) {
                }
                break;
        }

        try {
            microphone = MicrophoneTaskFactory.makeSampler(context);
            microphone.setMicListener(this);
            microphone.execute();
        } catch (MicrophoneTaskFactory.RecordLimitExceeded e) {
            e.printStackTrace();
        }
    }

    public void stop(Context context) {
        if (microphone != null)
            microphone.cancel(true);
    }

    public void onSignalReceived(short[] signal) {
        int total = 0;
        int count = 0;
        for (short peak : signal) {
            if (peak != 0) {
                total += Math.abs(peak);
                count++;
            }
        }
        int average = 0;
        if (count > 0) average = total / count;

        double averageDB = 0.0;
        if (average != 0) {
            averageDB = 20 * Math.log10(Math.abs(average));
        }

        if (averageDB > mNoiseThreshold) {
            if (!MicrophoneTaskFactory.isRecording() && sink != null) {
                sink.onSensorTrigger(EventTrigger.MICROPHONE, null);

                try {
                    AudioRecorderTask audioRecorderTask = MicrophoneTaskFactory.makeRecorder(context);
                    audioRecorderTask.setAudioRecorderListener(path -> {
                        if (sink != null) {
                            sink.onSensorTrigger(EventTrigger.MICROPHONE, path);
                        }
                    });
                    audioRecorderTask.start();
                } catch (MicrophoneTaskFactory.RecordLimitExceeded rle) {
                    Log.w("MicrophoneMonitor", "We are already recording!");
                }
            }
        }
    }

    public void onMicError() {
        Log.e("MicrophoneMonitor", "Microphone is not ready");
    }
}
