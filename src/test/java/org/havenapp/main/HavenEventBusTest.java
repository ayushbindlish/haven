package org.havenapp.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class HavenEventBusTest {

    private void drainMainLooper() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void registeredListenerReceivesPostsOnTheMainThread() {
        List<String> seen = new ArrayList<>();
        HavenEventBus.Listener l = (action, extras) ->
                seen.add(action + ":" + (extras == null ? "-" : extras.getInt("n")));
        HavenEventBus.register(l);
        try {
            Bundle b = new Bundle();
            b.putInt("n", 7);
            HavenEventBus.post("event", b);
            HavenEventBus.post("screen_state_changed");
            assertEquals("delivery is deferred to the looper", 0, seen.size());

            drainMainLooper();
            assertEquals(List.of("event:7", "screen_state_changed:-"), seen);
        } finally {
            HavenEventBus.unregister(l);
        }
    }

    @Test
    public void unregisteredListenerStopsReceiving() {
        List<String> seen = new ArrayList<>();
        HavenEventBus.Listener l = (a, e) -> seen.add(a);
        HavenEventBus.register(l);
        HavenEventBus.unregister(l);
        HavenEventBus.post("event");
        drainMainLooper();
        assertEquals(0, seen.size());
    }

    @Test
    public void oneThrowingListenerDoesNotBlockTheOthers() {
        List<String> seen = new ArrayList<>();
        HavenEventBus.Listener bad = (a, e) -> { throw new RuntimeException("boom"); };
        HavenEventBus.Listener good = (a, e) -> seen.add(a);
        HavenEventBus.register(bad);
        HavenEventBus.register(good);
        try {
            HavenEventBus.post("event");
            drainMainLooper();
            assertEquals(List.of("event"), seen);
        } finally {
            HavenEventBus.unregister(bad);
            HavenEventBus.unregister(good);
        }
    }

    @Test
    public void doubleRegisterIsIdempotent() {
        List<String> seen = new ArrayList<>();
        HavenEventBus.Listener l = (a, e) -> seen.add(a);
        HavenEventBus.register(l);
        HavenEventBus.register(l);
        try {
            HavenEventBus.post("event");
            drainMainLooper();
            assertEquals(1, seen.size());
        } finally {
            HavenEventBus.unregister(l);
        }
        assertNull(null);
    }
}
