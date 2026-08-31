package org.havenapp.main.alerts;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import org.havenapp.main.PreferenceManager;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.util.List;

public class SignalAlertChannel implements AlertChannel {
    private static final String TAG = "SignalAlertChannel";
    private Context context;
    private PreferenceManager prefs;
    private SignalProtocolStore protocolStore;

    public SignalAlertChannel(Context context) {
        this.context = context;
        this.prefs = new PreferenceManager(context);
        // Signal integration is not wired up in this build (no signal-cli backend / no
        // registered number). Left as a stub so the AlertChannel plumbing stays intact;
        // isAvailable() returns false so it is never used. See the plan's Phase 4 notes -
        // Telegram + ntfy cover the "encrypted push with media" need for now.
    }

    @Override
    public boolean isEnabled() {
        return prefs.isSignalVerified() && !TextUtils.isEmpty(prefs.getSignalUsername());
    }

    @Override
    public boolean isAvailable() {
        return protocolStore != null && prefs.isSignalVerified();
    }

    @Override
    public void sendAlert(String message, String mediaPath, int eventType) throws Exception {
        String recipient = prefs.getRemotePhoneNumber();
        if (TextUtils.isEmpty(recipient)) {
            throw new Exception("No recipient configured for Signal alerts");
        }

        // Create Signal message
        SignalProtocolAddress recipientAddress = new SignalProtocolAddress(recipient, 1);
        SessionCipher sessionCipher = new SessionCipher(protocolStore, recipientAddress);

        // Encrypt message
        CiphertextMessage encryptedMessage = sessionCipher.encrypt(message.getBytes());

        // Send via Signal service (simplified - would need full Signal service integration)
        sendToSignalService(recipient, encryptedMessage, mediaPath);

        Log.d(TAG, "Signal alert sent to " + recipient);
    }

    private void sendToSignalService(String recipient, CiphertextMessage message, String mediaPath) {
        // This would integrate with libsignal-service-java for actual sending
        // For now, this is a placeholder for the full Signal service integration
        Log.d(TAG, "Would send Signal message to " + recipient);
    }

    @Override
    public String getChannelName() {
        return "Signal";
    }

    @Override
    public void configure(String... params) {
        if (params.length > 0) {
            prefs.setSignalUsername(params[0]);
        }
    }

    @Override
    public boolean requiresConfiguration() {
        return !prefs.isSignalVerified();
    }
}