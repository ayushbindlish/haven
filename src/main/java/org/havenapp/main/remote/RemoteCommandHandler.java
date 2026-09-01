package org.havenapp.main.remote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.Utils;
import org.havenapp.main.location.LocationTracker;
import org.havenapp.main.service.MonitorService;

import java.util.Locale;

/**
 * Executes an authenticated remote command and produces a reply string. Transport-agnostic
 * (SMS today; Telegram polling can call this too). Auth = a shared secret the message must
 * start with (case-sensitive, compared in constant time), set in Settings. Repeated
 * wrong-secret attempts that still look like commands trigger a temporary lock-out.
 */
public final class RemoteCommandHandler {

    private static final String PREFS = "org.havenapp.main";
    private static final int MAX_BAD_ATTEMPTS = 5;
    private static final long ATTEMPT_WINDOW_MS = 15 * 60_000L;
    private static final long LOCKOUT_MS = 60 * 60_000L;

    public interface Reply {
        void send(String text);
    }

    private RemoteCommandHandler() {}

    /** @return true if the message was a valid, authenticated command (and was handled). */
    public static boolean handle(Context context, String rawMessage, Reply reply) {
        PreferenceManager prefs = new PreferenceManager(context);
        if (!prefs.getRemoteCommandsEnabled() && !prefs.getSupervisedEnabled()) return false;
        String secret = prefs.getRemoteCommandSecret();
        if (secret == null || secret.trim().isEmpty()) return false;

        Context app = context.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now < sp.getLong("remote_cmd_lockout_until", 0L)) return false; // silently ignore

        String msg = rawMessage == null ? "" : rawMessage.trim();
        // "Looks like a command attempt": long enough, with a space right after the secret.
        boolean looksLikeAttempt = msg.length() > secret.length()
                && msg.charAt(secret.length()) == ' ';
        boolean authed = looksLikeAttempt
                && constantTimeEquals(msg.substring(0, secret.length()), secret);
        if (!authed) {
            if (looksLikeAttempt) noteBadAttempt(sp, now);
            return false;
        }
        clearBadAttempts(sp);

        String rest = msg.substring(secret.length()).trim();
        String[] parts = rest.split("\\s+", 2);
        String cmd = parts[0].toUpperCase(Locale.US);
        String arg = parts.length > 1 ? parts[1].trim() : "";

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
                org.havenapp.main.service.ResumeNotifier.post(app, "Remote ARM — tap to arm Haven");
                reply.send("Tap the Haven notification on the device to arm.");
                return true;
            }
            case "SNOOZE": {
                int min = 30;
                try { min = Math.max(1, Integer.parseInt(arg)); } catch (Exception ignored) {}
                long until = System.currentTimeMillis() + min * 60_000L;
                prefs.setSnoozeUntil(until);
                app.stopService(new Intent(app, MonitorService.class));
                org.havenapp.main.service.SnoozeReceiver.schedule(app, until);
                reply.send("Snoozed " + min + " min; you'll be prompted to re-arm.");
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
                    // delay so the reply (SMS / HTTP) has a chance to leave before the
                    // process is torn down by the wipe
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(am::clearApplicationUserData, 4000);
                }
                return true;
            }
            default:
                reply.send("Commands: STATUS LOCATE PHOTO ARM DISARM SNOOZE <min> WIPE CONFIRM");
                return true;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private static void noteBadAttempt(SharedPreferences sp, long now) {
        long windowStart = sp.getLong("remote_cmd_window_start", 0L);
        int count = sp.getInt("remote_cmd_bad_count", 0);
        if (now - windowStart > ATTEMPT_WINDOW_MS) {
            windowStart = now;
            count = 0;
        }
        count++;
        SharedPreferences.Editor e = sp.edit()
                .putLong("remote_cmd_window_start", windowStart)
                .putInt("remote_cmd_bad_count", count);
        if (count >= MAX_BAD_ATTEMPTS) {
            e.putLong("remote_cmd_lockout_until", now + LOCKOUT_MS)
                    .putInt("remote_cmd_bad_count", 0);
        }
        e.apply();
    }

    private static void clearBadAttempts(SharedPreferences sp) {
        sp.edit()
                .putInt("remote_cmd_bad_count", 0)
                .putLong("remote_cmd_lockout_until", 0L)
                .apply();
    }
}
