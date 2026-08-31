package org.havenapp.main.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.alerts.AlertManager;

/**
 * Immediate alert when an app is installed or removed (the 6-hour compromise audit also
 * catches these, but slower). Manifest-registered so it fires even when Haven isn't
 * running. Gated by {@link PreferenceManager#getAlertAppChanges()} - on by default.
 */
public class PackageChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null || intent.getData() == null) return;
        // Ignore the "replaced" half of an update.
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;

        String pkg = intent.getData().getSchemeSpecificPart();
        if (pkg == null || pkg.equals(context.getPackageName())) return;

        if (!new PreferenceManager(context).getAlertAppChanges()) return;

        String label = pkg;
        try {
            PackageManager pm = context.getPackageManager();
            label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) + " (" + pkg + ")";
        } catch (Exception ignored) {
        }

        String msg;
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            msg = "App installed: " + label;
        } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            msg = "App removed: " + label;
        } else {
            return;
        }

        try {
            new AlertManager(context.getApplicationContext()).sendAlert(msg, null, -1);
        } catch (Exception ignored) {
        }
    }
}
