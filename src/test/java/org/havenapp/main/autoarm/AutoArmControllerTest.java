package org.havenapp.main.autoarm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Trusted-radio matching logic in {@link AutoArmController}. The prefs are a real
 * in-memory {@link SharedPreferences}; only the {@link Context} and the Wi-Fi service are
 * mocked.
 */
public class AutoArmControllerTest {

    private Context ctx;
    private FakePrefs prefs;

    @Before
    public void setUp() {
        prefs = new FakePrefs();
        ctx = mock(Context.class);
        lenient().when(ctx.getApplicationContext()).thenReturn(ctx);
        lenient().when(ctx.getSharedPreferences(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(prefs);
    }

    @Test
    public void noTrustedSsids_isNotOnTrustedWifi() {
        assertFalse(AutoArmController.onTrustedWifi(ctx));
    }

    @Test
    public void connectedToListedSsid_isTrusted() {
        prefs.edit().putString("auto_arm_trusted_ssids", "[\"HomeNet\",\"Office\"]").commit();
        WifiManager wm = mock(WifiManager.class);
        WifiInfo info = mock(WifiInfo.class);
        when(ctx.getSystemService(Context.WIFI_SERVICE)).thenReturn(wm);
        when(wm.getConnectionInfo()).thenReturn(info);
        when(info.getSSID()).thenReturn("\"HomeNet\"");   // framework wraps the SSID in quotes

        assertTrue(AutoArmController.onTrustedWifi(ctx));
    }

    @Test
    public void connectedToOtherSsid_isNotTrusted() {
        prefs.edit().putString("auto_arm_trusted_ssids", "[\"HomeNet\"]").commit();
        WifiManager wm = mock(WifiManager.class);
        WifiInfo info = mock(WifiInfo.class);
        when(ctx.getSystemService(Context.WIFI_SERVICE)).thenReturn(wm);
        when(wm.getConnectionInfo()).thenReturn(info);
        when(info.getSSID()).thenReturn("\"CoffeeShop\"");

        assertFalse(AutoArmController.onTrustedWifi(ctx));
    }

    @Test
    public void isTrustedBt_matchesStoredAddress() {
        prefs.edit().putString("auto_arm_trusted_bt",
                "[{\"addr\":\"AA:BB:CC:DD:EE:FF\",\"name\":\"Watch\"}]").commit();
        assertTrue(AutoArmController.isTrustedBt(ctx, "AA:BB:CC:DD:EE:FF"));
        assertFalse(AutoArmController.isTrustedBt(ctx, "11:22:33:44:55:66"));
        assertFalse(AutoArmController.isTrustedBt(ctx, null));
    }

    @Test
    public void noteBtConnection_tracksConnectedSet() {
        AutoArmController.noteBtConnection(ctx, "AA:BB:CC:DD:EE:FF", true);
        AutoArmController.noteBtConnection(ctx, "11:22:33:44:55:66", true);
        assertTrue(prefs.getString("bt_connected_addrs", "[]").contains("AA:BB:CC:DD:EE:FF"));

        AutoArmController.noteBtConnection(ctx, "AA:BB:CC:DD:EE:FF", false);
        assertFalse(prefs.getString("bt_connected_addrs", "[]").contains("AA:BB:CC:DD:EE:FF"));
        assertTrue(prefs.getString("bt_connected_addrs", "[]").contains("11:22:33:44:55:66"));

        AutoArmController.clearBtConnections(ctx);
        assertFalse(prefs.getString("bt_connected_addrs", "[]").contains("11:22:33:44:55:66"));
    }

    /* ------------------------------------------------------------ in-memory prefs */

    static final class FakePrefs implements SharedPreferences {
        private final Map<String, Object> m = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(m); }
        @Override public String getString(String k, String d) {
            Object v = m.get(k); return v != null ? (String) v : d;
        }
        @SuppressWarnings("unchecked")
        @Override public Set<String> getStringSet(String k, Set<String> d) {
            Object v = m.get(k); return v != null ? (Set<String>) v : d;
        }
        @Override public int getInt(String k, int d) {
            Object v = m.get(k); return v != null ? (Integer) v : d;
        }
        @Override public long getLong(String k, long d) {
            Object v = m.get(k); return v != null ? (Long) v : d;
        }
        @Override public float getFloat(String k, float d) {
            Object v = m.get(k); return v != null ? (Float) v : d;
        }
        @Override public boolean getBoolean(String k, boolean d) {
            Object v = m.get(k); return v != null ? (Boolean) v : d;
        }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

        @Override public Editor edit() {
            return new Editor() {
                @Override public Editor putString(String k, String v) { m.put(k, v); return this; }
                @Override public Editor putStringSet(String k, Set<String> v) { m.put(k, v); return this; }
                @Override public Editor putInt(String k, int v) { m.put(k, v); return this; }
                @Override public Editor putLong(String k, long v) { m.put(k, v); return this; }
                @Override public Editor putFloat(String k, float v) { m.put(k, v); return this; }
                @Override public Editor putBoolean(String k, boolean v) { m.put(k, v); return this; }
                @Override public Editor remove(String k) { m.remove(k); return this; }
                @Override public Editor clear() { m.clear(); return this; }
                @Override public boolean commit() { return true; }
                @Override public void apply() {}
            };
        }
    }
}
