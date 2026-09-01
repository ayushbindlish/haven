package org.havenapp.main;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny in-process event bus that replaces the deprecated {@code LocalBroadcastManager}.
 * Same semantics: listeners are invoked on the main thread, delivery is fire-and-forget,
 * and an exception in one listener never blocks the others.
 */
public final class HavenEventBus {

    public interface Listener {
        void onHavenEvent(String action, Bundle extras);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HavenEventBus() {}

    public static void register(Listener l) {
        if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void unregister(Listener l) {
        LISTENERS.remove(l);
    }

    public static void post(String action, Bundle extras) {
        MAIN.post(() -> {
            for (Listener l : LISTENERS) {
                try {
                    l.onHavenEvent(action, extras);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void post(String action) {
        post(action, null);
    }
}
