package org.havenapp.main.autoarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

/** Pure-logic tests for the arm-window / next-edge maths (no Android dependencies). */
public class AutoArmScheduleTest {

    private static long at(int dayOfWeek, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        // move to the requested day of week within the current week
        c.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String window(int[] days, int armMin, int disarmMin) {
        StringBuilder d = new StringBuilder("[");
        for (int i = 0; i < days.length; i++) {
            if (i > 0) d.append(',');
            d.append(days[i]);
        }
        d.append(']');
        return "[{\"days\":" + d + ",\"armMin\":" + armMin + ",\"disarmMin\":" + disarmMin + "}]";
    }

    @Test
    public void emptyOrGarbageIsNeverInWindow() {
        assertFalse(AutoArmSchedule.hasAny("[]"));
        assertFalse(AutoArmSchedule.hasAny("not json"));
        assertFalse(AutoArmSchedule.inArmWindow("[]", System.currentTimeMillis()));
        assertFalse(AutoArmSchedule.inArmWindow("garbage", System.currentTimeMillis()));
        assertEquals(0, AutoArmSchedule.nextEdgeMillis("[]", System.currentTimeMillis()));
    }

    @Test
    public void sameDayWindow() {
        // Wednesday 09:00 -> 17:00
        String s = window(new int[]{Calendar.WEDNESDAY}, 9 * 60, 17 * 60);
        assertTrue(AutoArmSchedule.inArmWindow(s, at(Calendar.WEDNESDAY, 12, 0)));
        assertTrue(AutoArmSchedule.inArmWindow(s, at(Calendar.WEDNESDAY, 9, 0)));   // inclusive start
        assertFalse(AutoArmSchedule.inArmWindow(s, at(Calendar.WEDNESDAY, 17, 0))); // exclusive end
        assertFalse(AutoArmSchedule.inArmWindow(s, at(Calendar.WEDNESDAY, 8, 59)));
        assertFalse(AutoArmSchedule.inArmWindow(s, at(Calendar.THURSDAY, 12, 0)));  // wrong day
    }

    @Test
    public void midnightWrappingWindow() {
        // Friday 22:00 -> Saturday 06:00
        String s = window(new int[]{Calendar.FRIDAY}, 22 * 60, 6 * 60);
        assertTrue(AutoArmSchedule.inArmWindow(s, at(Calendar.FRIDAY, 23, 30)));
        assertTrue(AutoArmSchedule.inArmWindow(s, at(Calendar.SATURDAY, 3, 0)));   // spill into next day
        assertFalse(AutoArmSchedule.inArmWindow(s, at(Calendar.SATURDAY, 6, 0)));  // window closed
        assertFalse(AutoArmSchedule.inArmWindow(s, at(Calendar.FRIDAY, 21, 0)));
        assertFalse(AutoArmSchedule.inArmWindow(s, at(Calendar.SATURDAY, 23, 0))); // Sat is not a start day
    }

    @Test
    public void nextEdgeIsInTheFutureAndOnAMinuteBoundary() {
        String s = window(new int[]{Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY}, 9 * 60, 18 * 60);
        long now = System.currentTimeMillis();
        long edge = AutoArmSchedule.nextEdgeMillis(s, now);
        assertTrue("edge must be in the future", edge > now);
        assertEquals("edge falls on a whole minute", 0, edge % 60000);
        // and no more than 8 days out
        assertTrue(edge - now <= 8L * 24 * 3600 * 1000);
    }
}
