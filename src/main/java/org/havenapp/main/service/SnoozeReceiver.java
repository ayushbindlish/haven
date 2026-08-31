package org.havenapp.main.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Fires when a snooze window ends and prompts to re-arm. */
public class SnoozeReceiver extends BroadcastReceiver {

    private static final String ACTION = "org.havenapp.main.SNOOZE_END";

    public static void schedule(Context context, long whenMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getBroadcast(context, 71,
                new Intent(context, SnoozeReceiver.class).setAction(ACTION), flags);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        ResumeNotifier.post(context, "Snooze ended — tap to re-arm Haven");
    }
}
