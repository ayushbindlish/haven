package org.havenapp.main.alerts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.havenapp.main.PreferenceManager;
import java.util.ArrayList;

public class SMSAlertChannel implements AlertChannel {
    private static final String TAG = "SMSAlertChannel";
    private Context context;
    private PreferenceManager prefs;

    public SMSAlertChannel(Context context) {
        this.context = context;
        this.prefs = new PreferenceManager(context);
    }

    @Override
    public boolean isEnabled() {
        return prefs.isRemoteNotificationActive() && !TextUtils.isEmpty(prefs.getRemotePhoneNumber());
    }

    @Override
    public boolean isAvailable() {
        // Needs telephony hardware and the runtime SEND_SMS grant.
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void sendAlert(String message, String mediaPath, int eventType) throws Exception {
        String phoneNumber = prefs.getRemotePhoneNumber();
        if (TextUtils.isEmpty(phoneNumber)) {
            throw new Exception("No phone number configured for SMS alerts");
        }

        SmsManager smsManager;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // SmsManager.getDefault() is deprecated since API 31.
            smsManager = context.getSystemService(SmsManager.class);
        } else {
            smsManager = SmsManager.getDefault();
        }
        if (smsManager == null) {
            throw new Exception("SmsManager unavailable on this device");
        }

        // Handle long messages
        if (message.length() > 160) {
            ArrayList<String> parts = smsManager.divideMessage(message);
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
        } else {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        }

        Log.d(TAG, "SMS alert sent to " + phoneNumber);
    }

    @Override
    public String getChannelName() {
        return "SMS";
    }

    @Override
    public void configure(String... params) {
        if (params.length > 0) {
            prefs.setRemotePhoneNumber(params[0]);
        }
        if (params.length > 1) {
            prefs.setSMSEnabled(Boolean.parseBoolean(params[1]));
        }
    }

    @Override
    public boolean requiresConfiguration() {
        return TextUtils.isEmpty(prefs.getRemotePhoneNumber());
    }
}