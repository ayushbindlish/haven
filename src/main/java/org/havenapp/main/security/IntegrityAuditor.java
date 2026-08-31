package org.havenapp.main.security;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Snapshots security-relevant device state and reports deviations from a stored baseline.
 * All checks are read-only. This is a tripwire for "someone changed my phone", not a full
 * anti-malware engine.
 */
public final class IntegrityAuditor {

    private static final String PREFS = "org.havenapp.main";
    private static final String KEY_BASELINE = "integrity_baseline";

    private final Context context;

    public IntegrityAuditor(Context context) {
        this.context = context.getApplicationContext();
    }

    /** @return check-name -> current value. */
    public JSONObject snapshot() {
        JSONObject o = new JSONObject();
        try {
            o.put("accessibility_services", norm(Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)));
            o.put("notification_listeners", norm(Settings.Secure.getString(
                    context.getContentResolver(), "enabled_notification_listeners")));
            o.put("adb_enabled", Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.ADB_ENABLED, 0));
            o.put("developer_options", Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0));
            o.put("device_secure", keyguardSecure() ? 1 : 0);
            o.put("device_admins", deviceAdmins());
            o.put("installed_packages", installedPackages());
            o.put("root_indicators", rootIndicators());
            o.put("sim", simFingerprint());
        } catch (Exception ignored) {
        }
        return o;
    }

    /** Compare against the stored baseline; empty list = no change (or no baseline yet). */
    public List<String> auditAgainstBaseline() {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prev = sp.getString(KEY_BASELINE, null);
        JSONObject cur = snapshot();
        if (prev == null) {
            sp.edit().putString(KEY_BASELINE, cur.toString()).apply();
            return new ArrayList<>();
        }
        List<String> changes = new ArrayList<>();
        try {
            JSONObject old = new JSONObject(prev);
            diffScalar(changes, old, cur, "accessibility_services", "Accessibility services changed");
            diffScalar(changes, old, cur, "notification_listeners", "Notification-listener apps changed");
            diffScalar(changes, old, cur, "adb_enabled", "USB debugging toggled");
            diffScalar(changes, old, cur, "developer_options", "Developer options toggled");
            diffScalar(changes, old, cur, "device_secure", "Screen lock changed");
            diffSet(changes, old, cur, "device_admins", "Device-admin app");
            diffSet(changes, old, cur, "installed_packages", "App");
            diffScalar(changes, old, cur, "root_indicators", "Root indicators changed");
            diffScalar(changes, old, cur, "sim", "SIM / carrier changed");
        } catch (Exception ignored) {
        }
        return changes;
    }

    public void resetBaseline() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_BASELINE, snapshot().toString()).apply();
    }

    public boolean hasBaseline() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_BASELINE);
    }

    /* ------------------------------------------------------------------ checks */

    private boolean keyguardSecure() {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    private String deviceAdmins() {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return "";
        List<ComponentName> admins = dpm.getActiveAdmins();
        if (admins == null) return "";
        TreeSet<String> set = new TreeSet<>();
        String self = context.getPackageName();
        for (ComponentName c : admins) {
            if (!c.getPackageName().equals(self)) set.add(c.getPackageName());
        }
        return TextUtils.join(",", set);
    }

    private String installedPackages() {
        PackageManager pm = context.getPackageManager();
        TreeSet<String> set = new TreeSet<>();
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            if (p.applicationInfo != null
                    && (p.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                set.add(p.packageName);
            }
        }
        return TextUtils.join(",", set);
    }

    /** Coarse SIM fingerprint (operator + country + line) - readable with READ_PHONE_STATE. */
    private String simFingerprint() {
        try {
            android.telephony.TelephonyManager tm =
                    (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null || tm.getSimState() != android.telephony.TelephonyManager.SIM_STATE_READY) {
                return "no-sim";
            }
            String op = safe(tm.getSimOperator());
            String co = safe(tm.getSimCountryIso());
            String nm = "";
            try {
                nm = safe(tm.getSimOperatorName());
            } catch (Exception ignored) {
            }
            return op + "/" + co + "/" + nm;
        } catch (SecurityException se) {
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private int rootIndicators() {
        int score = 0;
        String[] paths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk",
                "/data/local/bin/su", "/data/local/xbin/su", "/system/bin/magisk",
                "/data/adb/magisk", "/sbin/magisk"
        };
        for (String p : paths) {
            if (new File(p).exists()) score++;
        }
        String tags = Build.TAGS;
        if (tags != null && tags.contains("test-keys")) score++;
        return score;
    }

    /* ------------------------------------------------------------------ helpers */

    private static String norm(String s) {
        return s == null ? "" : s;
    }

    private static void diffScalar(List<String> out, JSONObject a, JSONObject b, String key, String label) {
        String av = a.optString(key, "");
        String bv = b.optString(key, "");
        if (!av.equals(bv)) out.add(label + " (" + av + " -> " + bv + ")");
    }

    private static void diffSet(List<String> out, JSONObject a, JSONObject b, String key, String label) {
        List<String> av = Arrays.asList(a.optString(key, "").split(","));
        List<String> bv = Arrays.asList(b.optString(key, "").split(","));
        for (String x : bv) {
            if (!x.isEmpty() && !av.contains(x)) out.add(label + " added: " + x);
        }
        for (String x : av) {
            if (!x.isEmpty() && !bv.contains(x)) out.add(label + " removed: " + x);
        }
    }
}
