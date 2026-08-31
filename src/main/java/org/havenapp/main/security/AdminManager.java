package org.havenapp.main.security;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.havenapp.main.R;

/** Thin wrapper around the plain Device Admin registered in {@link HavenDeviceAdminReceiver}. */
public final class AdminManager {

    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;

    public AdminManager(Context context) {
        this.context = context.getApplicationContext();
        this.dpm = (DevicePolicyManager) this.context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.admin = new ComponentName(this.context, HavenDeviceAdminReceiver.class);
    }

    public boolean isActive() {
        return dpm != null && dpm.isAdminActive(admin);
    }

    /** Intent to hand to startActivity() to bring up the system "activate admin" screen. */
    public Intent activationIntent() {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        context.getString(R.string.admin_explanation));
    }

    public void deactivate() {
        if (isActive()) dpm.removeActiveAdmin(admin);
    }

    /** Lock the screen immediately (needs force-lock policy, which we declare). */
    public void lockNow() {
        if (isActive()) {
            try {
                dpm.lockNow();
            } catch (SecurityException ignored) {
            }
        }
    }
}
