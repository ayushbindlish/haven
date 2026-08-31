package org.havenapp.main.mesh;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.PowerPolicy;
import org.havenapp.main.alerts.AlertManager;
import org.havenapp.main.alerts.HttpPoster;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Zero-infrastructure "are my other phones alive?" mesh, layered on a shared ntfy topic.
 * Each device beacons {@code HB<TAB>name<TAB>epoch} once per housekeeping tick and reads
 * back recent messages; if a known peer goes quiet for longer than the stale window it
 * fires an alert. Cost: one PUT + one GET per tick (piggybacks HousekeepingWorker).
 */
public final class MeshMonitor {

    private static final String TAG = "MeshMonitor";
    private static final String PREFS = "org.havenapp.main";
    private static final String KEY_PEERS = "mesh_peers";        // {name: lastSeenEpoch}
    private static final String KEY_ALERTED = "mesh_alerted";     // {name: 1}
    private static final String KEY_SINCE = "mesh_since";

    private MeshMonitor() {}

    public static void tick(Context context) {
        PreferenceManager prefs = new PreferenceManager(context);
        if (!prefs.getMeshEnabled()) return;
        String base = prefs.getNtfyServer();
        String topic = prefs.getNtfyTopic();
        if (base == null || base.isEmpty() || topic == null || topic.isEmpty()) return;

        base = base.replaceAll("/+$", "");
        String url = base + "/" + topic;
        boolean tor = prefs.getAlertsViaTor();
        String self = deviceName(prefs);
        long now = System.currentTimeMillis() / 1000;

        try {
            Map<String, String> h = new HashMap<>();
            h.put("Title", "haven-mesh");
            h.put("Priority", "min");
            HttpPoster.put(url, h, ("HB\t" + self + "\t" + now).getBytes("UTF-8"), tor);
        } catch (Exception e) {
            Log.w(TAG, "beacon failed", e);
        }

        SharedPrefsJson peers = new SharedPrefsJson(context, KEY_PEERS);
        SharedPrefsJson alerted = new SharedPrefsJson(context, KEY_ALERTED);
        long since = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SINCE, now - 86400);

        try {
            String body = HttpPoster.get(base + "/" + topic + "/json?poll=1&since=" + since, tor);
            for (String line : body.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JSONObject o = new JSONObject(line);
                String msg = o.optString("message", "");
                if (!msg.startsWith("HB\t")) continue;
                String[] p = msg.split("\t");
                if (p.length < 3) continue;
                String name = p[1];
                if (name.equals(self)) continue;
                peers.putLong(name, Long.parseLong(p[2]));
                alerted.remove(name); // it's alive again
            }
        } catch (Exception e) {
            Log.w(TAG, "poll failed", e);
        }

        long staleSec = Math.max(3 * 3600,
                3 * PowerPolicy.housekeepingIntervalMs(context) / 1000);
        AlertManager alerts = new AlertManager(context);
        for (String name : peers.keys()) {
            long last = peers.getLong(name, 0);
            if (now - last > staleSec && !alerted.has(name)) {
                alerted.putLong(name, 1);
                alerts.sendAlert("Mesh: device \"" + name + "\" hasn't checked in for "
                        + ((now - last) / 3600) + "h", null, -1);
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_SINCE, now).apply();
    }

    private static String deviceName(PreferenceManager prefs) {
        String n = prefs.getMeshDeviceName();
        return (n == null || n.trim().isEmpty()) ? Build.MODEL.replace(' ', '-') : n.trim();
    }

    /** Tiny JSON-object-in-one-pref helper. */
    private static final class SharedPrefsJson {
        private final android.content.SharedPreferences sp;
        private final String key;
        private JSONObject o;

        SharedPrefsJson(Context c, String key) {
            this.sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            this.key = key;
            try {
                o = new JSONObject(sp.getString(key, "{}"));
            } catch (Exception e) {
                o = new JSONObject();
            }
        }

        java.util.List<String> keys() {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) l.add(it.next());
            return l;
        }
        boolean has(String k) { return o.has(k); }
        long getLong(String k, long d) { return o.optLong(k, d); }

        void putLong(String k, long v) {
            try { o.put(k, v); save(); } catch (Exception ignored) {}
        }
        void remove(String k) { o.remove(k); save(); }
        private void save() { sp.edit().putString(key, o.toString()).apply(); }
    }
}
