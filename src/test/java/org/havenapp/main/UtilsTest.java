package org.havenapp.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;

@RunWith(RobolectricTestRunner.class)
public class UtilsTest {

    private Context ctx() {
        return ApplicationProvider.getApplicationContext();
    }

    private void setBattery(int level, int scale, int status, int plugged) {
        Intent i = new Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, level)
                .putExtra(BatteryManager.EXTRA_SCALE, scale)
                .putExtra(BatteryManager.EXTRA_STATUS, status)
                .putExtra(BatteryManager.EXTRA_PLUGGED, plugged);
        ApplicationProvider.<android.app.Application>getApplicationContext().sendStickyBroadcast(i);
    }

    @Test
    public void formatDateTimeIsNullSafeAndRendersTheDate() {
        assertEquals("", Utils.formatDateTime(null));

        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.MARCH, 4, 9, 5, 0);
        String s = Utils.formatDateTime(c.getTime());
        assertFalse(s.isEmpty());
        assertTrue("carries the year", s.contains("2026"));
    }

    @Test
    public void batteryPercentageFromLevelAndScale() {
        setBattery(63, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0);
        assertEquals(63, Utils.getBatteryPercentage(ctx()));
    }

    @Test
    public void chargingReflectsStatusAndPluggedState() {
        setBattery(80, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0);
        assertFalse(Utils.isCharging(ctx()));

        setBattery(80, 100, BatteryManager.BATTERY_STATUS_CHARGING, 0);
        assertTrue(Utils.isCharging(ctx()));

        setBattery(100, 100, BatteryManager.BATTERY_STATUS_FULL, 0);
        assertTrue(Utils.isCharging(ctx()));

        setBattery(80, 100, BatteryManager.BATTERY_STATUS_UNKNOWN, BatteryManager.BATTERY_PLUGGED_USB);
        assertTrue("plugged in but status unknown still counts", Utils.isCharging(ctx()));
    }
}
