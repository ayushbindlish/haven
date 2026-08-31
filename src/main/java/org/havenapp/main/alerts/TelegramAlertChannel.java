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
 * Sends alerts through the Telegram Bot API. No SDK, no dedicated phone number: the user
 * creates a bot with @BotFather, pastes the token + their chat id, and Haven POSTs to
 * api.telegram.org (optionally via Orbot). Bot chats are not end-to-end encrypted - the
 * settings screen says so.
 */
public class TelegramAlertChannel implements AlertChannel {

    private static final String TAG = "TelegramAlertChannel";
    private static final String API = "https://api.telegram.org/bot";

    private final PreferenceManager prefs;

    public TelegramAlertChannel(Context context) {
        this.prefs = new PreferenceManager(context);
    }

    @Override
    public boolean isEnabled() {
        return prefs.getTelegramEnabled()
                && !TextUtils.isEmpty(prefs.getTelegramBotToken())
                && !TextUtils.isEmpty(prefs.getTelegramChatId());
    }

    @Override
    public boolean isAvailable() {
        return !TextUtils.isEmpty(prefs.getTelegramBotToken())
                && !TextUtils.isEmpty(prefs.getTelegramChatId());
    }

    @Override
    public void sendAlert(String message, String mediaPath, int eventType) throws Exception {
        String token = prefs.getTelegramBotToken();
        String chatId = prefs.getTelegramChatId();
        boolean tor = prefs.getAlertsViaTor();

        File media = TextUtils.isEmpty(mediaPath) ? null : new File(mediaPath);
        if (media != null && media.exists()) {
            String name = media.getName().toLowerCase(Locale.US);
            String method, field, mime;
            if (name.endsWith(".mp4")) {
                method = "sendVideo"; field = "video"; mime = "video/mp4";
            } else if (name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".mp3")) {
                method = "sendAudio"; field = "audio"; mime = "audio/mp4";
            } else {
                method = "sendPhoto"; field = "photo"; mime = "image/jpeg";
            }
            Map<String, String> fields = new HashMap<>();
            fields.put("chat_id", chatId);
            fields.put("caption", message);
            HttpPoster.postMultipart(API + token + "/" + method, fields, field, media, mime, tor);
        } else {
            Map<String, String> form = new HashMap<>();
            form.put("chat_id", chatId);
            form.put("text", message);
            HttpPoster.postForm(API + token + "/sendMessage", form, tor);
        }
        Log.d(TAG, "telegram alert sent");
    }

    @Override
    public String getChannelName() {
        return "Telegram";
    }

    @Override
    public void configure(String... params) {
        if (params.length > 0) prefs.setTelegramBotToken(params[0]);
        if (params.length > 1) prefs.setTelegramChatId(params[1]);
        if (params.length > 2) prefs.setTelegramEnabled(Boolean.parseBoolean(params[2]));
    }

    @Override
    public boolean requiresConfiguration() {
        return TextUtils.isEmpty(prefs.getTelegramBotToken())
                || TextUtils.isEmpty(prefs.getTelegramChatId());
    }
}
