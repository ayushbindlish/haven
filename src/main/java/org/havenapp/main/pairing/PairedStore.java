package org.havenapp.main.pairing;

import android.content.Context;

import org.havenapp.main.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Parent-side list of paired child devices (JSON in prefs). */
public final class PairedStore {

    public static final class Device {
        public String name, server, topic, secret, onion, onionPw;

        static Device from(JSONObject o) {
            Device d = new Device();
            d.name = o.optString("n");
            d.server = o.optString("s");
            d.topic = o.optString("t");
            d.secret = o.optString("k");
            d.onion = o.optString("o");
            d.onionPw = o.optString("p");
            return d;
        }

        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("n", name); o.put("s", server); o.put("t", topic);
                o.put("k", secret); o.put("o", onion); o.put("p", onionPw);
            } catch (Exception ignored) {}
            return o;
        }
    }

    private final PreferenceManager prefs;

    public PairedStore(Context c) {
        this.prefs = new PreferenceManager(c);
    }

    public List<Device> all() {
        List<Device> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getPairedDevices());
            for (int i = 0; i < a.length(); i++) out.add(Device.from(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public void addOrReplace(PairingPayload p) {
        try {
            JSONArray a = new JSONArray(prefs.getPairedDevices());
            JSONArray b = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                if (!a.getJSONObject(i).optString("t").equals(p.ntfyTopic)) b.put(a.getJSONObject(i));
            }
            Device d = new Device();
            d.name = p.name; d.server = p.ntfyServer; d.topic = p.ntfyTopic;
            d.secret = p.cmdSecret; d.onion = p.onion; d.onionPw = p.onionPw;
            b.put(d.json());
            prefs.setPairedDevices(b.toString());
        } catch (Exception ignored) {}
    }

    public void removeByTopic(String topic) {
        try {
            JSONArray a = new JSONArray(prefs.getPairedDevices());
            JSONArray b = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                if (!a.getJSONObject(i).optString("t").equals(topic)) b.put(a.getJSONObject(i));
            }
            prefs.setPairedDevices(b.toString());
        } catch (Exception ignored) {}
    }
}
