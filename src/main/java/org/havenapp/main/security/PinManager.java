package org.havenapp.main.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Salted-SHA256 PIN for gating app entry, settings changes and disarming. This is a
 * deterrent, not a secret store - Phase 10 adds real at-rest encryption. Stored in the
 * same {@code org.havenapp.main} SharedPreferences file the rest of the app uses.
 */
public final class PinManager {

    private static final String PREFS = "org.havenapp.main";
    private static final String KEY_HASH = "pin_hash";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_LOCK_ON_LAUNCH = "pin_lock_on_launch";
    private static final String KEY_PIN_TO_STOP = "pin_to_stop";
    private static final String KEY_FAILED = "pin_failed_count";

    /** Set true by LockActivity after a successful unlock; cleared on process start. */
    public static volatile boolean unlockedThisProcess = false;

    private final SharedPreferences sp;

    public PinManager(Context context) {
        this.sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasPin() {
        return !TextUtils.isEmpty(sp.getString(KEY_HASH, ""));
    }

    public void setPin(String pin) {
        if (TextUtils.isEmpty(pin)) {
            sp.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_FAILED).apply();
            return;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        sp.edit()
                .putString(KEY_SALT, base64(salt))
                .putString(KEY_HASH, hash(pin, salt))
                .putInt(KEY_FAILED, 0)
                .apply();
    }

    public boolean verify(String pin) {
        if (!hasPin() || TextUtils.isEmpty(pin)) return false;
        byte[] salt = unbase64(sp.getString(KEY_SALT, ""));
        boolean ok = constantTimeEquals(hash(pin, salt), sp.getString(KEY_HASH, ""));
        sp.edit().putInt(KEY_FAILED, ok ? 0 : failedCount() + 1).apply();
        return ok;
    }

    public int failedCount() {
        return sp.getInt(KEY_FAILED, 0);
    }

    public boolean isLockOnLaunch() {
        return sp.getBoolean(KEY_LOCK_ON_LAUNCH, hasPin());
    }

    public void setLockOnLaunch(boolean v) {
        sp.edit().putBoolean(KEY_LOCK_ON_LAUNCH, v).apply();
    }

    public boolean isPinToStop() {
        return sp.getBoolean(KEY_PIN_TO_STOP, hasPin());
    }

    public void setPinToStop(boolean v) {
        sp.edit().putBoolean(KEY_PIN_TO_STOP, v).apply();
    }

    /* --------------------------------------------------------------------- crypto */

    private static String hash(String pin, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pin.getBytes("UTF-8"));
            // stretch a little
            byte[] h = md.digest();
            for (int i = 0; i < 20000; i++) {
                md.reset();
                md.update(h);
                h = md.digest();
            }
            return base64(h);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private static String base64(byte[] b) {
        return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
    }

    private static byte[] unbase64(String s) {
        return android.util.Base64.decode(s, android.util.Base64.NO_WRAP);
    }
}
