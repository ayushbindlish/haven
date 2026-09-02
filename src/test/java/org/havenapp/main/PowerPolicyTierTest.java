package org.havenapp.main;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.PowerManager;

import androidx.test.core.app.ApplicationProvider;

import org.havenapp.main.PowerPolicy.Tier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

/** {@link PowerPolicy#current} derives the tier from charge %, thermal status and mode. */
@RunWith(RobolectricTestRunner.class)
public class PowerPolicyTierTest {

    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        setMode(PreferenceManager.POWER_MODE_ADAPTIVE);
        setThermal(PowerManager.THERMAL_STATUS_NONE);
    }

    private void setMode(String mode) {
        TestPrefs.put(ctx, "power_mode", mode);
    }

    private void setThermal(int status) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        Shadows.shadowOf(pm).setCurrentThermalStatus(status);
    }

    private void setBattery(int pct, boolean charging) {
        Intent i = new Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, pct)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, charging
                        ? BatteryManager.BATTERY_STATUS_CHARGING
                        : BatteryManager.BATTERY_STATUS_DISCHARGING)
                .putExtra(BatteryManager.EXTRA_PLUGGED, charging ? BatteryManager.BATTERY_PLUGGED_AC : 0);
        ApplicationProvider.<android.app.Application>getApplicationContext().sendStickyBroadcast(i);
    }

    @Test
    public void chargingAndCoolIsFull() {
        setBattery(90, true);
        assertEquals(Tier.FULL, PowerPolicy.current(ctx));
    }

    @Test
    public void onBatteryMidChargeIsBalanced() {
        setBattery(55, false);
        assertEquals(Tier.BALANCED, PowerPolicy.current(ctx));
    }

    @Test
    public void lowBatteryIsSaver() {
        setBattery(12, false);
        assertEquals(Tier.SAVER, PowerPolicy.current(ctx));
    }

    @Test
    public void veryLowBatteryIsCritical() {
        setBattery(4, false);
        assertEquals(Tier.CRITICAL, PowerPolicy.current(ctx));
    }

    @Test
    public void criticalThermalOverridesEverything() {
        setBattery(100, true);
        setThermal(PowerManager.THERMAL_STATUS_CRITICAL);
        assertEquals(Tier.CRITICAL, PowerPolicy.current(ctx));
    }

    @Test
    public void severeThermalDropsToSaverEvenWhileCharging() {
        setBattery(100, true);
        setThermal(PowerManager.THERMAL_STATUS_SEVERE);
        assertEquals(Tier.SAVER, PowerPolicy.current(ctx));
    }

    @Test
    public void batterySaverModeForcesSaver() {
        setBattery(80, false);
        setMode(PreferenceManager.POWER_MODE_BATTERY_SAVER);
        assertEquals(Tier.SAVER, PowerPolicy.current(ctx));
    }
}
