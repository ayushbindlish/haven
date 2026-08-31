package org.havenapp.main.tile;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import org.havenapp.main.MonitorActivity;
import org.havenapp.main.R;
import org.havenapp.main.service.MonitorService;

/** Home-screen widget: one button, arms (via the Activity) or disarms Haven. */
public class HavenWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_TOGGLE = "org.havenapp.main.WIDGET_TOGGLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            boolean armed = MonitorService.getInstance() != null && MonitorService.getInstance().isRunning();
            if (armed) {
                context.stopService(new Intent(context, MonitorService.class));
            } else {
                context.startActivity(new Intent(context, MonitorActivity.class)
                        .putExtra("auto_resume", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            refreshAll(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(context, mgr, id);
    }

    private void refreshAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        for (int id : mgr.getAppWidgetIds(new ComponentName(context, HavenWidgetProvider.class))) {
            render(context, mgr, id);
        }
    }

    private void render(Context context, AppWidgetManager mgr, int id) {
        boolean armed = MonitorService.getInstance() != null && MonitorService.getInstance().isRunning();
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_haven);
        v.setTextViewText(R.id.widget_state,
                context.getString(armed ? R.string.tile_armed : R.string.tile_disarmed));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0,
                new Intent(context, HavenWidgetProvider.class).setAction(ACTION_TOGGLE), flags);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        mgr.updateAppWidget(id, v);
    }
}
