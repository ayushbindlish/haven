package org.havenapp.main.autoarm

import android.location.Location
import androidx.test.core.app.ApplicationProvider
import org.havenapp.main.PreferenceManager
import org.havenapp.main.location.GeofenceStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the auto-arm pieces that need the real framework: SharedPreferences round-trips,
 * and the geofence trusted-place check (which calls the native Location.distanceBetween).
 */
class AutoArmInstrumentedTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var prefs: PreferenceManager

    @Before
    fun setUp() {
        prefs = PreferenceManager(ctx)
        GeofenceStore(ctx).clear()
    }

    @After
    fun tearDown() {
        prefs.autoArmEnabled = false
        prefs.setAutoArmSchedule("[]")
        prefs.trustedSsids = "[]"
        prefs.trustedBtDevices = "[]"
        GeofenceStore(ctx).clear()
    }

    @Test
    fun preferenceRoundTrip() {
        prefs.autoArmEnabled = true
        prefs.setAutoArmSchedule("""[{"days":[2,3,4,5,6],"armMin":540,"disarmMin":1080}]""")
        prefs.autoArmAwayEnabled = true
        prefs.autoArmAwayMinutes = 20
        prefs.autoArmUntrustedLocation = true
        prefs.trustedSsids = """["HomeNet"]"""
        prefs.trustedBtDevices = """[{"addr":"AA:BB:CC:DD:EE:FF","name":"Watch"}]"""
        prefs.autoArmOnBtDisconnect = true
        prefs.lockDeviceOnArm = true
        prefs.lockAppOnArm = true
        prefs.disarmOnUnlock = true
        prefs.tamperCaptureEnabled = true

        val fresh = PreferenceManager(ctx)
        assertTrue(fresh.autoArmEnabled)
        assertTrue(AutoArmSchedule.hasAny(fresh))
        assertTrue(fresh.autoArmAwayEnabled)
        assertEquals(20, fresh.autoArmAwayMinutes)
        assertTrue(fresh.autoArmUntrustedLocation)
        assertTrue(fresh.trustedSsids.contains("HomeNet"))
        assertTrue(fresh.trustedBtDevices.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(fresh.autoArmOnBtDisconnect)
        assertTrue(fresh.lockDeviceOnArm)
        assertTrue(fresh.lockAppOnArm)
        assertTrue(fresh.disarmOnUnlock)
        assertTrue(fresh.tamperCaptureEnabled)
    }

    @Test
    fun trustedPlaceGeofence() {
        val store = GeofenceStore(ctx)
        assertFalse(store.hasTrusted())
        store.add(GeofenceStore.Place("Home", 12.9716, 77.5946, 200f, true))
        store.add(GeofenceStore.Place("SomewhereElse", 0.0, 0.0, 150f, false))
        assertTrue(store.hasTrusted())

        val near = Location("t").apply { latitude = 12.9718; longitude = 77.5947 } // ~25 m
        val far = Location("t").apply { latitude = 13.05; longitude = 77.65 }        // ~10 km

        assertTrue(store.inTrustedPlace(near))
        assertFalse(store.inTrustedPlace(far))
        assertFalse(store.inTrustedPlace(null))
    }

    @Test
    fun scheduleNextEdgeIsFuture() {
        prefs.setAutoArmSchedule("""[{"days":[1,2,3,4,5,6,7],"armMin":0,"disarmMin":1439}]""")
        val now = System.currentTimeMillis()
        val edge = AutoArmSchedule.nextEdgeMillis(prefs, now)
        assertTrue(edge > now)
        assertEquals(0L, edge % 60000)
    }
}
