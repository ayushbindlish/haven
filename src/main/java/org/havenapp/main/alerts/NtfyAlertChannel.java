package org.havenapp.main.alerts;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.havenapp.main.PreferenceManager;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sends alerts to an <a href="https://ntfy.sh">ntfy</a> topic - the public server or a
 * self-hosted one (recommended for privacy). Text alerts PUT the message as the body;
 * media alerts PUT the file bytes with the message in the {@code X-Message} header.
 */
public class NtfyAlertChannel implements AlertChannel {

    private static final String TAG = "NtfyAlertChannel";

    private final PreferenceManager prefs;

    public NtfyAlertChannel(Context context) {
        this.prefs = new PreferenceManager(context);
    }

    @Override
    public boolean isEnabled() {
        return prefs.getNtfyEnabled() && hasConfig();
    }

    @Override
    public boolean isAvailable() {
        return hasConfig();
    }

    private boolean hasConfig() {
        return !TextUtils.isEmpty(prefs.getNtfyServer()) && !TextUtils.isEmpty(prefs.getNtfyTopic());
    }

    @Override
    public void sendAlert(String message, String mediaPath, int eventType) throws Exception {
        String base = prefs.getNtfyServer().replaceAll("/+$", "");
        String url = base + "/" + prefs.getNtfyTopic();
        boolean tor = prefs.getAlertsViaTor();

        Map<String, String> headers = new HashMap<>();
        headers.put("Title", "Haven");
        headers.put("Priority", "high");
        headers.put("Tags", "rotating_light");

        File media = TextUtils.isEmpty(mediaPath) ? null : new File(mediaPath);
        if (media != null && media.exists()) {
            headers.put("Filename", media.getName());
            headers.put("X-Message", asciiHeader(message));
            HttpPoster.put(url, headers, HttpPoster.readFile(media), tor);
        } else {
            HttpPoster.put(url, headers, message.getBytes("UTF-8"), tor);
        }
        Log.d(TAG, "ntfy alert sent to " + url);
    }

    /** ntfy headers must be latin-1 safe; drop anything else. */
    private static String asciiHeader(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127) sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public String getChannelName() {
        return "ntfy";
    }

    @Override
    public void configure(String... params) {
        if (params.length > 0) prefs.setNtfyServer(params[0]);
        if (params.length > 1) prefs.setNtfyTopic(params[1]);
        if (params.length > 2) prefs.setNtfyEnabled(Boolean.parseBoolean(params[2]));
    }

    @Override
    public boolean requiresConfiguration() {
        return !hasConfig();
    }
}
