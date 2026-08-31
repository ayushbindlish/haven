package org.havenapp.main.net;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

import org.havenapp.main.PreferenceManager;

/** Start/stop helper for {@link DnsFilterVpnService}. */
public final class ContentFilter {

    public static final int REQUEST_VPN_CONSENT = 8;

    private ContentFilter() {}

    /** @return true if it started, false if it launched the consent dialog (call again on OK). */
    public static boolean start(Activity activity) {
        Intent consent = VpnService.prepare(activity);
        if (consent != null) {
            activity.startActivityForResult(consent, REQUEST_VPN_CONSENT);
            return false;
        }
        new PreferenceManager(activity).setContentFilterEnabled(true);
        activity.startService(new Intent(activity, DnsFilterVpnService.class));
        return true;
    }

    public static void stop(Context context) {
        new PreferenceManager(context).setContentFilterEnabled(false);
        context.startService(new Intent(context, DnsFilterVpnService.class)
                .setAction(DnsFilterVpnService.ACTION_STOP));
    }

    public static boolean isRunning(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(200)) {
            if (DnsFilterVpnService.class.getName().equals(s.service.getClassName())) return true;
        }
        return false;
    }
}
