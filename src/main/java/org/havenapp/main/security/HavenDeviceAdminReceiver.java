package org.havenapp.main.security;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import org.havenapp.main.R;
import org.havenapp.main.alerts.AlertManager;

/**
 * Plain Device Admin (not Device Owner - no factory reset). It can't block uninstall
 * outright, but it forces the user to actively deactivate admin first, and it lets Haven
 * enforce a lock and fire a tamper alert the moment someone tries.
 */
public class HavenDeviceAdminReceiver extends DeviceAdminReceiver {

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Shown in the system "deactivate admin?" dialog.
        return context.getString(R.string.admin_disable_warning);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        try {
            new AlertManager(context.getApplicationContext())
                    .sendAlert(context.getString(R.string.admin_disabled_alert), null, -1);
        } catch (Exception ignored) {
        }
    }
}
