package org.havenapp.main.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.location.Location;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

/** JVM coverage (via Robolectric) of the geofence store + the trusted-place check. */
@RunWith(RobolectricTestRunner.class)
public class GeofenceStoreRoboTest {

    private GeofenceStore store;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        store = new GeofenceStore(ctx);
        store.clear();
    }

    @Test
    public void addListRemove() {
        store.add(new GeofenceStore.Place("Home", 12.9716, 77.5946, 200f, true));
        store.add(new GeofenceStore.Place("Cafe", 12.98, 77.60, 120f, false));

        List<GeofenceStore.Place> all = store.all();
        assertEquals(2, all.size());
        assertEquals("Home", all.get(0).name);
        assertTrue(all.get(0).trusted);
        assertFalse(all.get(1).trusted);
        assertEquals(200f, all.get(0).radiusM, 0.001f);

        store.removeByName("Home");
        assertEquals(1, store.all().size());
        assertEquals("Cafe", store.all().get(0).name);
    }

    @Test
    public void legacyPlacesWithoutTheTrustedKeyDefaultToUntrusted() {
        // simulate a store written before the "trusted" flag existed
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences("org.havenapp.main", Context.MODE_PRIVATE)
                .edit().putString("geofences",
                        "[{\"name\":\"Old\",\"lat\":1.0,\"lng\":2.0,\"r\":150}]").commit();
        assertFalse(store.all().get(0).trusted);
        assertFalse(store.hasTrusted());
    }

    @Test
    public void inTrustedPlaceUsesRealDistance() {
        assertFalse(store.hasTrusted());
        store.add(new GeofenceStore.Place("Home", 12.9716, 77.5946, 200f, true));
        store.add(new GeofenceStore.Place("Unsafe", 0.0, 0.0, 150f, false));
        assertTrue(store.hasTrusted());

        Location near = new Location("t");
        near.setLatitude(12.9718);
        near.setLongitude(77.5947); // ~25 m from Home
        Location far = new Location("t");
        far.setLatitude(13.05);
        far.setLongitude(77.65); // ~10 km

        assertTrue(store.inTrustedPlace(near));
        assertFalse(store.inTrustedPlace(far));
        assertFalse("a missing fix errs toward arming", store.inTrustedPlace(null));
    }
}
