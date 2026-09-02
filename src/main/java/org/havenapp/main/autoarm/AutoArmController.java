package org.havenapp.main.autoarm;

import android.Manifest;
import android.app.KeyguardManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.alerts.AlertManager;
import org.havenapp.main.location.GeofenceStore;
import org.havenapp.main.location.LocationTracker;
import org.havenapp.main.security.AdminManager;
import org.havenapp.main.security.PinManager;
import org.havenapp.main.service.MonitorService;
import org.havenapp.main.service.ResumeNotifier;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether the monitor should be running based on the auto-arm inputs (schedule,
 * "away" dwell, being outside every trusted place, a trusted Bluetooth device leaving) and
 * a "safe context" that suppresses them (connected to a trusted Wi-Fi / Bluetooth device,
 * or physically inside a trusted place). Also carries the on-arm lock actions and the
 * disarm-on-unlock behaviour.
 *
 * <p>All entry points are safe to call from a receiver; location-dependent checks resolve
 * asynchronously via {@link LocationTracker#lastKnown}.
 */
public final class AutoArmController {

    private static final String TAG = "AutoArm";

    private AutoArmController() {}

    public static boolean anyTriggerEnabled(Context c) {
        PreferenceManager p = new PreferenceManager(c);
        return p.getAutoArmEnabled() && (AutoArmSchedule.hasAny(p)
                || p.getAutoArmAwayEnabled()
                || p.getAutoArmUntrustedLocation()
                || p.getAutoArmOnBtDisconnect());
    }

    private static boolean armed() {
        MonitorService s = MonitorService.getInstance();
        return s != null && s.isRunning();
    }

    /** Re-evaluate and act. {@code reason} is one of "schedule", "bt-disconnect", "away-dwell",
     *  "wifi", "boot", "manual". */
    public static void evaluate(Context c, String reason) {
        PreferenceManager p = new PreferenceManager(c);
        if (!p.getAutoArmEnabled()) return;
        Context app = c.getApplicationContext();
        LocationTracker.lastKnown(app, loc -> decide(app, p, reason, loc));
    }

    private static void decide(Context c, PreferenceManager p, String reason, Location loc) {
        boolean scheduleArm = AutoArmSchedule.inArmWindow(p);
        boolean safe = onTrustedWifi(c)
                || trustedBtConnected(c)
                || new GeofenceStore(c).inTrustedPlace(loc);

        boolean want;
        String why;
        if (scheduleArm) {
            want = true;
            why = "scheduled window";
        } else if (safe) {
            want = false;
            why = "";
        } else {
            boolean locTrigger = p.getAutoArmUntrustedLocation() && new GeofenceStore(c).hasTrusted();
            boolean btTrigger = p.getAutoArmOnBtDisconnect() && "bt-disconnect".equals(reason);
            boolean awayTrigger = p.getAutoArmAwayEnabled() && "away-dwell".equals(reason);
            want = locTrigger || btTrigger || awayTrigger;
            why = btTrigger ? "a trusted Bluetooth device left"
                    : awayTrigger ? "the phone was left undisturbed"
                    : locTrigger ? "outside every trusted area" : "";
        }

        if (want && !armed()) {
            arm(c, why);
        } else if (!want && armed() && p.getAutoArmed() && !scheduleArm) {
            disarm(c, "auto-arm conditions cleared");
        }
    }

    /* --------------------------------------------------------------------- actions */

    public static void arm(Context c, String why) {
        if (armed()) return;
        PreferenceManager p = new PreferenceManager(c);
        try {
            ContextCompat.startForegroundService(c, new Intent(c, MonitorService.class));
            p.setAutoArmed(true);
            Log.i(TAG, "auto-armed (" + why + ")");
            onArmed(c, p);
            new AlertManager(c).sendAlert(c.getString(R.string.autoarm_armed_alert, why), null, -1);
        } catch (Exception e) {
            Log.w(TAG, "auto-arm foreground-service start blocked", e);
            ResumeNotifier.post(c, c.getString(R.string.autoarm_blocked_notice));
        }
    }

    private static void disarm(Context c, String why) {
        PreferenceManager p = new PreferenceManager(c);
        c.stopService(new Intent(c, MonitorService.class));
        p.setAutoArmed(false);
        Log.i(TAG, "auto-disarmed (" + why + ")");
    }

    /** Lock actions when a session becomes armed (auto or manual). */
    public static void onArmed(Context c, PreferenceManager p) {
        if (p.getLockAppOnArm() && new PinManager(c).hasPin()) {
            PinManager.unlockedThisProcess = false;
            p.setArmedAppLock(true);
        }
        if (p.getLockDeviceOnArm()) {
            KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            boolean alreadyLocked = kg != null && kg.isKeyguardLocked();
            AdminManager admin = new AdminManager(c);
            if (!alreadyLocked && admin.isActive()) {
                admin.lockNow();
                Log.i(TAG, "locked device screen on arm");
            }
        }
    }

    /** Called when the owner unlocks the device (ACTION_USER_PRESENT / admin callback). */
    public static void onUserUnlocked(Context c) {
        PreferenceManager p = new PreferenceManager(c);
        p.setArmedAppLock(false);
        if (p.getDisarmOnUnlock() && armed()) {
            c.stopService(new Intent(c, MonitorService.class));
            p.setAutoArmed(false);
            Log.i(TAG, "disarmed on device unlock");
            try {
                new AlertManager(c).sendAlert(c.getString(R.string.autoarm_disarmed_unlock), null, -1);
            } catch (Exception ignored) {
            }
        }
    }

    /* --------------------------------------------------------------- safe-context */

    static boolean onTrustedWifi(Context c) {
        try {
            JSONArray a = new JSONArray(new PreferenceManager(c).getTrustedSsids());
            if (a.length() == 0) return false;
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return false;
            @SuppressWarnings("deprecation")
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return false;
            String ssid = info.getSSID();
            if (ssid == null) return false;
            ssid = ssid.replaceAll("^\"|\"$", "");
            if (ssid.isEmpty() || ssid.equals(WifiManager.UNKNOWN_SSID)) return false;
            for (int i = 0; i < a.length(); i++) {
                if (ssid.equals(a.getString(i))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static boolean trustedBtConnected(Context c) {
        try {
            JSONArray trusted = new JSONArray(new PreferenceManager(c).getTrustedBtDevices());
            if (trusted.length() == 0) return false;
            BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad = bm != null ? bm.getAdapter() : null;
            if (ad == null || !ad.isEnabled()) return false;

            Set<String> trustedAddrs = new HashSet<>();
            for (int i = 0; i < trusted.length(); i++) {
                trustedAddrs.add(trusted.getJSONObject(i).optString("addr"));
            }
            JSONArray connected = new JSONArray(new PreferenceManager(c).getBtConnectedAddrs());
            for (int i = 0; i < connected.length(); i++) {
                if (trustedAddrs.contains(connected.getString(i))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Address is in the trusted-BT list. */
    public static boolean isTrustedBt(Context c, String addr) {
        if (addr == null) return false;
        try {
            JSONArray a = new JSONArray(new PreferenceManager(c).getTrustedBtDevices());
            for (int i = 0; i < a.length(); i++) {
                if (addr.equals(a.getJSONObject(i).optString("addr"))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Maintain the "currently connected" address set from ACL broadcasts. */
    public static void noteBtConnection(Context c, String addr, boolean connected) {
        if (addr == null) return;
        try {
            PreferenceManager p = new PreferenceManager(c);
            JSONArray a = new JSONArray(p.getBtConnectedAddrs());
            Set<String> s = new HashSet<>();
            for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
            if (connected) s.add(addr);
            else s.remove(addr);
            p.setBtConnectedAddrs(new JSONArray(s).toString());
        } catch (Exception ignored) {
        }
    }

    public static void clearBtConnections(Context c) {
        new PreferenceManager(c).setBtConnectedAddrs("[]");
    }

    static boolean hasBluetoothConnectPerm(Context c) {
        return Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(c,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
}
