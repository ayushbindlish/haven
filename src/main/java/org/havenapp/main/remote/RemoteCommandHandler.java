package org.havenapp.main.remote;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.Utils;
import org.havenapp.main.location.LocationTracker;
import org.havenapp.main.service.MonitorService;

import java.util.Locale;

/**
 * Executes an authenticated remote command and produces a reply string. Transport-agnostic
 * (SMS today; Telegram polling can call this too). Auth = a shared secret the message must
 * start with, set in Settings.
 */
public final class RemoteCommandHandler {

    public interface Reply {
        void send(String text);
    }

    private RemoteCommandHandler() {}

    /** @return true if the message was a valid, authenticated command (and was handled). */
    public static boolean handle(Context context, String rawMessage, Reply reply) {
        PreferenceManager prefs = new PreferenceManager(context);
        if (!prefs.getRemoteCommandsEnabled()) return false;
        String secret = prefs.getRemoteCommandSecret();
        if (secret == null || secret.trim().isEmpty()) return false;

        String msg = rawMessage == null ? "" : rawMessage.trim();
        if (!msg.toLowerCase(Locale.US).startsWith(secret.toLowerCase(Locale.US) + " ")) return false;
        String rest = msg.substring(secret.length()).trim();
        String[] parts = rest.split("\\s+", 2);
        String cmd = parts[0].toUpperCase(Locale.US);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        Context app = context.getApplicationContext();
        switch (cmd) {
            case "STATUS": {
                boolean armed = MonitorService.getInstance() != null
                        && MonitorService.getInstance().isRunning();
                reply.send("Haven: " + (armed ? "ARMED" : "disarmed")
                        + ", battery " + Utils.getBatteryPercentage(app) + "%"
                        + (Utils.isCharging(app) ? " (charging)" : ""));
                return true;
            }
            case "LOCATE": {
                new LocationTracker(app).requestOneShot(loc ->
                        reply.send("Location: " + LocationTracker.format(loc)));
                return true;
            }
            case "DISARM": {
                app.stopService(new Intent(app, MonitorService.class));
                reply.send("Haven disarmed.");
                return true;
            }
            case "ARM": {
                new org.havenapp.main.service.BootReceiver()
                        .onReceive(app, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
                reply.send("Tap the Haven notification on the device to arm.");
                return true;
            }
            case "SNOOZE": {
                int min = 30;
                try { min = Math.max(1, Integer.parseInt(arg)); } catch (Exception ignored) {}
                prefs.setSnoozeUntil(System.currentTimeMillis() + min * 60_000L);
                app.stopService(new Intent(app, MonitorService.class));
                reply.send("Snoozed " + min + " min.");
                return true;
            }
            case "PHOTO": {
                MonitorService s = MonitorService.getInstance();
                if (s != null && s.isRunning()) {
                    s.requestPhoto();
                    reply.send("Photo requested — check your alert channel / onion log.");
                } else {
                    reply.send("Not armed; can't take a photo.");
                }
                return true;
            }
            case "WIPE": {
                if (!"CONFIRM".equalsIgnoreCase(arg)) {
                    reply.send("Send: <secret> WIPE CONFIRM  to erase Haven's data.");
                    return true;
                }
                reply.send("Wiping Haven data now.");
                android.app.ActivityManager am =
                        (android.app.ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && am != null) {
                    am.clearApplicationUserData();
                }
                return true;
            }
            default:
                reply.send("Commands: STATUS LOCATE PHOTO ARM DISARM SNOOZE <min> WIPE CONFIRM");
                return true;
        }
    }
}
