package org.havenapp.main.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.havenapp.main.MonitorActivity;
import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;

/**
 * After a reboot or an app update, Android 12+ will not let us start a camera/microphone
 * foreground service from the background, so we can't silently resume monitoring. Instead,
 * if monitoring was active, post a high-priority notification that re-arms Haven from the
 * foreground when tapped.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String CHANNEL = "haven_resume";
    private static final int NOTIF_ID = 42;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                break;
            default:
                return;
        }

        PreferenceManager prefs = new PreferenceManager(context);
        if (!prefs.getBootResumeEnabled()) return;
        if (!prefs.getMonitorServiceActive()) return;
        if (MonitorService.getInstance() != null && MonitorService.getInstance().isRunning()) return;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    context.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }

        Intent launch = new Intent(context, MonitorActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .putExtra("auto_resume", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(context, 0, launch, flags);

        Notification n = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_haven)
                .setContentTitle(context.getString(R.string.resume_monitoring_title))
                .setContentText(context.getString(R.string.resume_monitoring_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build();
        nm.notify(NOTIF_ID, n);
    }
}
