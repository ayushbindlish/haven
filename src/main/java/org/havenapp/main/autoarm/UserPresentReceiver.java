package org.havenapp.main.autoarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The owner unlocked the device. Drops the "armed" app-lock flag and, if
 * {@code disarm_on_unlock} is set, stands the monitor down. This is the version-agnostic
 * path; {@code HavenDeviceAdminReceiver.onPasswordSucceeded} does the same a beat earlier
 * on devices where Device Admin is active.
 */
public class UserPresentReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
            AutoArmController.onUserUnlocked(context);
        }
    }
}
