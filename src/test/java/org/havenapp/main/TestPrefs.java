package org.havenapp.main;

import android.content.Context;
import android.content.SharedPreferences;

/** Direct access to the {@code org.havenapp.main} SharedPreferences file used across the app. */
public final class TestPrefs {

    private TestPrefs() {}

    public static SharedPreferences of(Context c) {
        return c.getSharedPreferences("org.havenapp.main", Context.MODE_PRIVATE);
    }

    public static void put(Context c, String key, boolean v) {
        of(c).edit().putBoolean(key, v).commit();
    }

    public static void put(Context c, String key, String v) {
        of(c).edit().putString(key, v).commit();
    }

    public static void put(Context c, String key, int v) {
        of(c).edit().putInt(key, v).commit();
    }

    public static void put(Context c, String key, long v) {
        of(c).edit().putLong(key, v).commit();
    }
}
