package org.havenapp.main.alerts;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * On-disk queue of alerts that exhausted their in-process retries, so a notification
 * isn't silently lost when the network is down or the process is killed mid-send.
 * {@link AlertManager#flushPending} re-attempts them on the next app start / housekeeping
 * tick; entries older than {@link #EXPIRY_MS} or past the size cap are dropped.
 */
final class PendingAlertStore {

    private static final String TAG = "PendingAlertStore";
    private static final String FILE = "pending_alerts.json";
    private static final int MAX_ENTRIES = 50;
    static final long EXPIRY_MS = 24L * 60 * 60 * 1000;

    static final class Pending {
        final String channel;
        final String message;
        final String mediaPath;
        final int eventType;
        final long queuedAt;

        Pending(String channel, String message, String mediaPath, int eventType, long queuedAt) {
            this.channel = channel;
            this.message = message;
            this.mediaPath = mediaPath;
            this.eventType = eventType;
            this.queuedAt = queuedAt;
        }
    }

    private PendingAlertStore() {}

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    static synchronized void add(Context c, String channel, String message,
                                 String mediaPath, int eventType) {
        try {
            List<Pending> all = load(c);
            all.add(new Pending(channel, message, mediaPath, eventType, System.currentTimeMillis()));
            while (all.size() > MAX_ENTRIES) all.remove(0);
            save(c, all);
        } catch (Exception e) {
            Log.w(TAG, "could not queue pending alert", e);
        }
    }

    static synchronized List<Pending> load(Context c) {
        List<Pending> out = new ArrayList<>();
        File f = file(c);
        if (!f.isFile()) return out;
        try {
            byte[] buf = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) != -1) n += r;
            }
            JSONArray arr = new JSONArray(new String(buf, "UTF-8"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long queuedAt = o.optLong("t", now);
                if (now - queuedAt > EXPIRY_MS) continue; // drop expired
                out.add(new Pending(o.optString("c"), o.optString("m"),
                        o.isNull("p") ? null : o.optString("p"), o.optInt("e", -1), queuedAt));
            }
        } catch (Exception e) {
            Log.w(TAG, "could not read pending alerts", e);
        }
        return out;
    }

    static synchronized void save(Context c, List<Pending> all) {
        try {
            JSONArray arr = new JSONArray();
            for (Pending p : all) {
                JSONObject o = new JSONObject();
                o.put("c", p.channel);
                o.put("m", p.message);
                if (p.mediaPath != null) o.put("p", p.mediaPath);
                o.put("e", p.eventType);
                o.put("t", p.queuedAt);
                arr.put(o);
            }
            File f = file(c);
            if (all.isEmpty()) { f.delete(); return; }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                out.write(arr.toString().getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Log.w(TAG, "could not persist pending alerts", e);
        }
    }
}
