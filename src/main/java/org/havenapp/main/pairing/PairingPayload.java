package org.havenapp.main.pairing;

import android.util.Base64;

import org.json.JSONObject;

/**
 * The blob a child device shows as a QR and a parent device scans. Carries everything the
 * parent needs to watch the child: the shared ntfy bus (events + commands flow over it),
 * a command secret, and optionally the Tor onion address for full log access.
 */
public final class PairingPayload {

    public final String name;
    public final String ntfyServer;
    public final String ntfyTopic;
    public final String cmdSecret;
    public final String onion;      // may be ""
    public final String onionPw;    // may be ""

    public PairingPayload(String name, String ntfyServer, String ntfyTopic,
                          String cmdSecret, String onion, String onionPw) {
        this.name = name;
        this.ntfyServer = ntfyServer;
        this.ntfyTopic = ntfyTopic;
        this.cmdSecret = cmdSecret;
        this.onion = onion == null ? "" : onion;
        this.onionPw = onionPw == null ? "" : onionPw;
    }

    public String encode() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("n", name);
            o.put("s", ntfyServer);
            o.put("t", ntfyTopic);
            o.put("k", cmdSecret);
            o.put("o", onion);
            o.put("p", onionPw);
            return "HVPAIR1:" + Base64.encodeToString(
                    o.toString().getBytes("UTF-8"), Base64.NO_WRAP | Base64.URL_SAFE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PairingPayload decode(String s) {
        try {
            if (s == null || !s.startsWith("HVPAIR1:")) return null;
            byte[] json = Base64.decode(s.substring(8), Base64.NO_WRAP | Base64.URL_SAFE);
            JSONObject o = new JSONObject(new String(json, "UTF-8"));
            if (o.optInt("v") != 1) return null;
            return new PairingPayload(o.optString("n"), o.optString("s"), o.optString("t"),
                    o.optString("k"), o.optString("o"), o.optString("p"));
        } catch (Exception e) {
            return null;
        }
    }
}
