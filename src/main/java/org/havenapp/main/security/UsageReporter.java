package org.havenapp.main.security;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Builds a "screen time in the last 24h" digest from {@link UsageStatsManager}. Requires
 * the user to grant "Usage access" in system settings ({@link #hasPermission}).
 */
public final class UsageReporter {

    private final Context context;

    public UsageReporter(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasPermission() {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public android.content.Intent settingsIntent() {
        return new android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    /** @return a human-readable digest, or null if no permission / no data. */
    public String buildDailyDigest(int topN) {
        if (!hasPermission()) return null;
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long end = System.currentTimeMillis();
        long start = end - 24L * 60 * 60 * 1000;
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
        if (stats == null || stats.isEmpty()) return null;

        List<UsageStats> list = new ArrayList<>();
        for (UsageStats s : stats) {
            if (s.getTotalTimeInForeground() > 60_000) list.add(s);
        }
        Collections.sort(list, (a, b) ->
                Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

        PackageManager pm = context.getPackageManager();
        StringBuilder sb = new StringBuilder("Screen time (last 24h):\n");
        int shown = 0;
        for (UsageStats s : list) {
            if (shown++ >= topN) break;
            String label = s.getPackageName();
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(s.getPackageName(), 0)).toString();
            } catch (Exception ignored) {
            }
            sb.append("• ").append(label).append(" — ")
                    .append(fmt(s.getTotalTimeInForeground())).append('\n');
        }
        return shown == 0 ? null : sb.toString().trim();
    }

    private static String fmt(long ms) {
        long m = ms / 60000;
        if (m < 60) return m + "m";
        return String.format(Locale.US, "%dh %02dm", m / 60, m % 60);
    }
}
