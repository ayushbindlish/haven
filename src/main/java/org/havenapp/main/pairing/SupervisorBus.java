package org.havenapp.main.pairing;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.Utils;
import org.havenapp.main.alerts.HttpPoster;
import org.havenapp.main.location.LocationTracker;
import org.havenapp.main.remote.RemoteCommandHandler;
import org.havenapp.main.service.MonitorService;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Child side of supervised mode. Publishes events / heartbeat / command replies to the
 * shared ntfy topic from the {@link PairingPayload}, and answers {@code CMD <secret> …}
 * messages a paired parent posts there.
 */
public final class SupervisorBus {

    private static final String TAG = "SupervisorBus";

    private SupervisorBus() {}

    public static boolean active(Context c) {
        PreferenceManager p = new PreferenceManager(c);
        return p.getSupervisedEnabled()
                && !p.getSupervisedServer().isEmpty()
                && !p.getSupervisedTopic().isEmpty();
    }

    private static String url(PreferenceManager p) {
        return p.getSupervisedServer().replaceAll("/+$", "") + "/" + p.getSupervisedTopic();
    }

    /** Copy of an alert, tagged so the parent dashboard can list it. */
    public static void publishEvent(Context c, String message) {
        if (!active(c)) return;
        PreferenceManager p = new PreferenceManager(c);
        try {
            Map<String, String> h = new HashMap<>();
            h.put("Title", "haven-event");
            h.put("Priority", "default");
            HttpPoster.put(url(p), h, ("EV\t" + message).getBytes("UTF-8"), p.getAlertsViaTor());
        } catch (Exception e) {
            Log.w(TAG, "publishEvent failed", e);
        }
    }

    /** Periodic tick: heartbeat + drain any pending parent commands. */
    public static void tick(Context c) {
        if (!active(c)) return;
        PreferenceManager p = new PreferenceManager(c);
        boolean tor = p.getAlertsViaTor();
        String base = p.getSupervisedServer().replaceAll("/+$", "");
        String topic = p.getSupervisedTopic();
        String secret = new PreferenceManager(c).getRemoteCommandSecret();

        // heartbeat
        try {
            boolean armed = MonitorService.getInstance() != null && MonitorService.getInstance().isRunning();
            JSONObject hb = new JSONObject();
            hb.put("name", Build.MODEL);
            hb.put("armed", armed);
            hb.put("batt", Utils.getBatteryPercentage(c));
            hb.put("charging", Utils.isCharging(c));
            hb.put("ts", System.currentTimeMillis());
            Map<String, String> h = new HashMap<>();
            h.put("Title", "haven-hb");
            h.put("Priority", "min");
            HttpPoster.put(base + "/" + topic, h, ("HB\t" + hb).getBytes("UTF-8"), tor);
        } catch (Exception e) {
            Log.w(TAG, "hb failed", e);
        }

        // drain CMDs
        long since = p.getSupervisedPollSince();
        if (since == 0) since = System.currentTimeMillis() / 1000 - 300;
        try {
            String body = HttpPoster.get(base + "/" + topic + "/json?poll=1&since=" + since, tor);
            for (String line : body.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JSONObject o = new JSONObject(line);
                String msg = o.optString("message", "");
                if (!msg.startsWith("CMD\t")) continue;
                final String reply = base + "/" + topic;
                RemoteCommandHandler.handle(c.getApplicationContext(),
                        msg.substring(4).replace('\t', ' '), text -> {
                            try {
                                Map<String, String> rh = new HashMap<>();
                                rh.put("Title", "haven-resp");
                                HttpPoster.put(reply, rh, ("RESP\t" + text).getBytes("UTF-8"), tor);
                            } catch (Exception ignored) {
                            }
                        });
            }
        } catch (Exception e) {
            Log.w(TAG, "poll failed", e);
        }
        p.setSupervisedPollSince(System.currentTimeMillis() / 1000);
    }

    /** One-shot location beacon to the topic (used after a geofence / LOCATE). */
    public static void beaconLocation(Context c) {
        if (!active(c)) return;
        PreferenceManager p = new PreferenceManager(c);
        new LocationTracker(c).requestOneShot(loc -> {
            try {
                Map<String, String> h = new HashMap<>();
                h.put("Title", "haven-loc");
                HttpPoster.put(url(p), h, ("LOC\t" + LocationTracker.format(loc)).getBytes("UTF-8"),
                        p.getAlertsViaTor());
            } catch (Exception ignored) {
            }
        });
    }
}
