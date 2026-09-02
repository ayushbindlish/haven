package org.havenapp.main.security;

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
public class PinManagerTest {

    private PinManager pin;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        pin = new PinManager(ctx);
    }

    @Test
    public void setThenVerify() {
        assertFalse(pin.hasPin());
        pin.setPin("2468");
        assertTrue(pin.hasPin());
        assertTrue(pin.verify("2468"));
        assertFalse(pin.verify("2467"));
        assertFalse(pin.verify(""));
        assertFalse(pin.verify(null));
    }

    @Test
    public void clearingWithEmptyOrNullRemovesThePin() {
        pin.setPin("1111");
        assertTrue(pin.hasPin());
        pin.setPin("");
        assertFalse(pin.hasPin());
        pin.setPin("2222");
        pin.setPin(null);
        assertFalse(pin.hasPin());
    }

    @Test
    public void failedCountIncrementsOnWrongResetsOnRight() {
        pin.setPin("4321");
        assertEquals(0, pin.failedCount());
        pin.verify("0000");
        pin.verify("0001");
        assertEquals(2, pin.failedCount());
        pin.verify("4321");
        assertEquals(0, pin.failedCount());
    }

    @Test
    public void saltIsRandomPerPin() {
        pin.setPin("9999");
        String hashA = TestSp.of().getString("pin_hash", "");
        pin.setPin("9999");
        String hashB = TestSp.of().getString("pin_hash", "");
        assertFalse("same PIN, fresh salt -> different stored hash", hashA.equals(hashB));
        assertTrue(pin.verify("9999"));
    }

    @Test
    public void duressPinIsRecognisedAndDistinctFromTheRealPin() {
        pin.setPin("1234");
        pin.setDuressPin("8888");
        assertTrue(pin.hasDuressPin());
        assertTrue(pin.isDuressPin("8888"));
        assertFalse("the real PIN is not the duress PIN", pin.isDuressPin("1234"));
        assertFalse("verify() only matches the real PIN", pin.verify("8888"));
    }

    @Test
    public void lockOnLaunchDefaultsToHavingAPin() {
        assertFalse(pin.isLockOnLaunch());
        pin.setPin("0007");
        assertTrue(pin.isLockOnLaunch());
        pin.setLockOnLaunch(false);
        assertFalse(pin.isLockOnLaunch());
    }

    /** Small accessor for the shared prefs file the manager writes to. */
    static final class TestSp {
        static android.content.SharedPreferences of() {
            return ApplicationProvider.getApplicationContext()
                    .getSharedPreferences("org.havenapp.main", Context.MODE_PRIVATE);
        }
    }
}
