package org.havenapp.main.alerts;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertManager {
    private static final String TAG = "AlertManager";

    /** Ignore a byte-identical alert that arrives within this window. */
    private static final long DEDUPE_WINDOW_MS = 3000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {0, 2000, 8000};

    private final List<AlertChannel> channels = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Context context;

    private String lastKey;
    private long lastKeyAt;

    public AlertManager(Context context) {
        this.context = context;
        initializeChannels();
    }

    private void initializeChannels() {
        channels.add(new SMSAlertChannel(context));
        channels.add(new TelegramAlertChannel(context));
        channels.add(new NtfyAlertChannel(context));
        channels.add(new BriarAlertChannel(context));
        channels.add(new SessionAlertChannel(context));
    }

    public void sendAlert(String message, String mediaPath, int eventType) {
        String key = eventType + "|" + message + "|" + (mediaPath == null ? "" : mediaPath);
        long now = SystemClock.elapsedRealtime();
        synchronized (this) {
            if (key.equals(lastKey) && now - lastKeyAt < DEDUPE_WINDOW_MS) {
                return;
            }
            lastKey = key;
            lastKeyAt = now;
        }

        // Mirror to a paired parent device, if supervised mode is on.
        org.havenapp.main.pairing.SupervisorBus.publishEvent(context, message);

        for (AlertChannel channel : channels) {
            if (channel.isEnabled() && channel.isAvailable()) {
                executor.execute(() -> deliver(channel, message, mediaPath, eventType));
            }
        }
    }

    private void deliver(AlertChannel channel, String message, String mediaPath, int eventType) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(BACKOFF_MS[attempt]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                channel.sendAlert(message, mediaPath, eventType);
                Log.d(TAG, "Alert sent via " + channel.getChannelName()
                        + (attempt > 0 ? " (retry " + attempt + ")" : ""));
                return;
            } catch (Exception e) {
                Log.w(TAG, "Attempt " + (attempt + 1) + " via " + channel.getChannelName()
                        + " failed: " + e.getMessage());
            }
        }
        Log.e(TAG, "Giving up on " + channel.getChannelName() + " after " + MAX_ATTEMPTS + " attempts");
    }

    /** Fire a test alert through every currently enabled + available channel. */
    public int sendTest(String message) {
        int fired = 0;
        for (AlertChannel channel : channels) {
            if (channel.isEnabled() && channel.isAvailable()) {
                fired++;
                executor.execute(() -> deliver(channel, message, null, -1));
            }
        }
        return fired;
    }

    public void addChannel(AlertChannel channel) {
        channels.add(channel);
    }

    public void removeChannel(AlertChannel channel) {
        channels.remove(channel);
    }

    public List<AlertChannel> getChannels() {
        return new ArrayList<>(channels);
    }
}
