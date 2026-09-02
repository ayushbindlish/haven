package org.havenapp.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.havenapp.main.PowerPolicy.Tier;
import org.junit.Test;

/** The per-tier feature knobs are pure functions — no Android needed. */
public class PowerPolicyTest {

    @Test
    public void locationIntervalWidensAsPowerTightens() {
        assertEquals(5 * 60_000L, PowerPolicy.locationIntervalMs(Tier.FULL));
        assertEquals(15 * 60_000L, PowerPolicy.locationIntervalMs(Tier.BALANCED));
        assertEquals(30 * 60_000L, PowerPolicy.locationIntervalMs(Tier.SAVER));
        assertEquals(60 * 60_000L, PowerPolicy.locationIntervalMs(Tier.CRITICAL));
        assertTrue(PowerPolicy.locationIntervalMs(Tier.FULL)
                < PowerPolicy.locationIntervalMs(Tier.CRITICAL));
    }

    @Test
    public void cameraFpsDropsWithTier() {
        assertEquals(5, PowerPolicy.cameraAnalysisFps(Tier.FULL));
        assertEquals(3, PowerPolicy.cameraAnalysisFps(Tier.BALANCED));
        assertEquals(2, PowerPolicy.cameraAnalysisFps(Tier.SAVER));
        assertEquals(1, PowerPolicy.cameraAnalysisFps(Tier.CRITICAL));
    }

    @Test
    public void videoAndMlOnlyWithThermalHeadroom() {
        assertTrue(PowerPolicy.allowVideoCapture(Tier.FULL));
        assertTrue(PowerPolicy.allowVideoCapture(Tier.BALANCED));
        assertFalse(PowerPolicy.allowVideoCapture(Tier.SAVER));
        assertFalse(PowerPolicy.allowVideoCapture(Tier.CRITICAL));

        assertTrue(PowerPolicy.allowMl(Tier.FULL));
        assertTrue(PowerPolicy.allowMl(Tier.BALANCED));
        assertFalse(PowerPolicy.allowMl(Tier.SAVER));
        assertFalse(PowerPolicy.allowMl(Tier.CRITICAL));
    }

    @Test
    public void housekeepingCadenceStretchesUnderPressure() {
        assertEquals(3 * 3_600_000L, PowerPolicy.housekeepingIntervalMs(Tier.FULL));
        assertEquals(24 * 3_600_000L, PowerPolicy.housekeepingIntervalMs(Tier.CRITICAL));
        long prev = 0;
        for (Tier t : new Tier[]{Tier.FULL, Tier.BALANCED, Tier.SAVER, Tier.CRITICAL}) {
            long v = PowerPolicy.housekeepingIntervalMs(t);
            assertTrue("monotonically non-decreasing", v >= prev);
            prev = v;
        }
    }
}
