package org.havenapp.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PreferenceManagerTest {

    private PreferenceManager p;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        p = new PreferenceManager(ctx);
    }

    @Test
    public void securitySensitiveFeaturesAreOffByDefault() {
        assertFalse("at-rest DB encryption is opt-in", p.getEncryptDatabase());
        assertFalse("media encryption is opt-in", p.getEncryptMedia());
        assertFalse("SMS remote commands are opt-in", p.getRemoteCommandsEnabled());
        assertFalse("supervised mode is opt-in", p.getSupervisedEnabled());
        assertFalse("auto-arm is opt-in", p.getAutoArmEnabled());
        assertFalse(p.getLockDeviceOnArm());
        assertFalse(p.getDisarmOnUnlock());
        assertFalse(p.getTamperCaptureEnabled());
    }

    @Test
    public void protectiveDefaultsAreOn() {
        assertTrue("tamper alerts default on", p.getTamperAlertsEnabled());
        assertTrue("compromise self-audit default on", p.getSecurityAuditEnabled());
        assertTrue("resume monitoring after reboot default on", p.getBootResumeEnabled());
        assertEquals("adaptive power mode by default",
                PreferenceManager.POWER_MODE_ADAPTIVE, p.getPowerMode());
    }

    @Test
    public void supervisedServerDefaultsToPublicNtfy() {
        assertEquals("https://ntfy.sh", p.getSupervisedServer());
        assertEquals("", p.getSupervisedTopic());
    }

    @Test
    public void autoArmAwayMinutesRoundTripsAndClampsToAtLeastOne() {
        assertEquals(15, p.getAutoArmAwayMinutes());
        p.setAutoArmAwayMinutes(25);
        assertEquals(25, new PreferenceManager(ApplicationProvider.getApplicationContext()).getAutoArmAwayMinutes());
        p.setAutoArmAwayMinutes(0);
        assertEquals(1, p.getAutoArmAwayMinutes());
    }

    @Test
    public void booleanRoundTripAcrossInstances() {
        p.setAutoArmEnabled(true);
        p.setLockDeviceOnArm(true);
        PreferenceManager fresh = new PreferenceManager(ApplicationProvider.getApplicationContext());
        assertTrue(fresh.getAutoArmEnabled());
        assertTrue(fresh.getLockDeviceOnArm());
    }
}
