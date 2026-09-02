package org.havenapp.main.autoarm;

import org.havenapp.main.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Time-of-day / day-of-week arm windows. Stored as a JSON array of
 * {@code {"days":[1..7 Calendar.DAY_OF_WEEK], "armMin":H*60+M, "disarmMin":H*60+M}}.
 * A window whose disarm minute is {@code <=} its arm minute wraps past midnight; its day
 * set is the day the window starts.
 *
 * <p>The core predicates take the raw JSON string so they can be unit-tested with no
 * Android dependencies.
 */
public final class AutoArmSchedule {

    private AutoArmSchedule() {}

    public static boolean hasAny(PreferenceManager p) {
        return hasAny(p.getAutoArmSchedule());
    }

    public static boolean hasAny(String scheduleJson) {
        try {
            return new JSONArray(scheduleJson).length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean inArmWindow(PreferenceManager p) {
        return inArmWindow(p.getAutoArmSchedule(), System.currentTimeMillis());
    }

    public static boolean inArmWindow(String scheduleJson, long atMillis) {
        try {
            JSONArray a = new JSONArray(scheduleJson);
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(atMillis);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            int prevDow = dow == Calendar.SUNDAY ? Calendar.SATURDAY : dow - 1;
            int minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                JSONArray days = o.getJSONArray("days");
                int arm = o.getInt("armMin");
                int dis = o.getInt("disarmMin");
                if (arm < dis) {
                    if (contains(days, dow) && minOfDay >= arm && minOfDay < dis) return true;
                } else { // wraps midnight
                    if (contains(days, dow) && minOfDay >= arm) return true;
                    if (contains(days, prevDow) && minOfDay < dis) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static long nextEdgeMillis(PreferenceManager p, long now) {
        return nextEdgeMillis(p.getAutoArmSchedule(), now);
    }

    /**
     * Epoch millis of the next arm-edge or disarm-edge strictly after {@code now}, or 0 if
     * there are no windows. Scans the next 8 days.
     */
    public static long nextEdgeMillis(String scheduleJson, long now) {
        long best = 0;
        try {
            JSONArray a = new JSONArray(scheduleJson);
            for (int dayOffset = 0; dayOffset <= 8; dayOffset++) {
                Calendar base = Calendar.getInstance();
                base.setTimeInMillis(now);
                base.add(Calendar.DAY_OF_YEAR, dayOffset);
                int dow = base.get(Calendar.DAY_OF_WEEK);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    if (!contains(o.getJSONArray("days"), dow)) continue;
                    int arm = o.getInt("armMin");
                    int dis = o.getInt("disarmMin");
                    long armAt = edge(base, arm);
                    long disAt = edge(base, dis <= arm ? dis + 1440 : dis); // wrap -> next day
                    if (armAt > now) best = earliest(best, armAt);
                    if (disAt > now) best = earliest(best, disAt);
                }
            }
        } catch (Exception ignored) {
        }
        return best;
    }

    private static long edge(Calendar day, int minOfDay) {
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MINUTE, minOfDay);
        return c.getTimeInMillis();
    }

    private static long earliest(long a, long b) {
        return a == 0 ? b : Math.min(a, b);
    }

    private static boolean contains(JSONArray days, int dow) throws Exception {
        for (int i = 0; i < days.length(); i++) if (days.getInt(i) == dow) return true;
        return false;
    }
}
