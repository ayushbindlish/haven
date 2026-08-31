package org.havenapp.main.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.havenapp.main.MonitorActivity;
import org.havenapp.main.R;

/**
 * Android 12+ forbids starting a camera/microphone foreground service from the background,
 * so Haven can't silently (re)arm after a reboot / snooze / schedule / geofence event.
 * Instead we post one high-priority full-screen-intent notification that arms from the
 * foreground when tapped.
 */
public final class ResumeNotifier {

    private static final String CHANNEL = "haven_resume";
    private static final int NOTIF_ID = 42;

    private ResumeNotifier() {}

    public static void post(Context context, String reason) {
        if (MonitorService.getInstance() != null && MonitorService.getInstance().isRunning()) return;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    context.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH));
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
                .setContentText(reason != null ? reason
                        : context.getString(R.string.resume_monitoring_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build();
        nm.notify(NOTIF_ID, n);
    }
}
