package org.havenapp.main.autoarm;

import android.content.Context;
import android.util.Log;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.alerts.AlertManager;
import org.havenapp.main.service.MonitorService;

/**
 * Fan-in for tamper signals (wrong device unlock, unlock while armed, Haven PIN failure).
 * Always alerts. If the monitor is armed and silent capture is on, grabs a still with the
 * screen off via {@link MonitorService#tamper}.
 */
public final class TamperCapture {

    private static final String TAG = "AutoArm";

    private TamperCapture() {}

    public static void fire(Context c, String reason) {
        Context app = c.getApplicationContext();
        PreferenceManager p = new PreferenceManager(app);
        MonitorService svc = MonitorService.getInstance();
        boolean armed = svc != null && svc.isRunning();

        if (armed && p.getTamperCaptureEnabled()) {
            Log.i(TAG, "tamper capture: " + reason);
            svc.tamper(reason);
            return; // MonitorService.tamper already routed the alert through AlertManager
        }
        try {
            new AlertManager(app).sendAlert("Tamper: " + reason, null, -1);
        } catch (Exception ignored) {
        }
    }
}
