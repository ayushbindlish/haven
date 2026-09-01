package org.havenapp.main.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;

/**
 * After a reboot or app update, if monitoring was active, prompt to re-arm from the
 * foreground (Android 12+ blocks a silent camera/mic FGS restart from the background).
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                break;
            default:
                return;
        }

        PreferenceManager prefs = new PreferenceManager(context);
        if (!prefs.getBootResumeEnabled() || !prefs.getMonitorServiceActive()) return;

        ResumeNotifier.post(context, context.getString(R.string.resume_monitoring_text));
    }
}
