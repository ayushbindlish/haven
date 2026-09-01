package org.havenapp.main.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import org.havenapp.main.PreferenceManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Receives SMS and runs any that start with the configured secret through
 * {@link RemoteCommandHandler}, replying to the sender by SMS.
 */
public class SmsCommandReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        if (!new PreferenceManager(context).getRemoteCommandsEnabled()) return;

        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        StringBuilder body = new StringBuilder();
        String from = null;
        for (SmsMessage m : msgs) {
            body.append(m.getMessageBody());
            from = m.getOriginatingAddress();
        }
        final String sender = from;
        final Context app = context.getApplicationContext();
        // A command reply can be async (LOCATE waits on a fix), so keep the receiver alive
        // with goAsync() and only finish() once the reply is sent — or after a safety timeout.
        final PendingResult pending = goAsync();
        final AtomicBoolean done = new AtomicBoolean(false);
        final Runnable finish = () -> { if (done.compareAndSet(false, true)) pending.finish(); };
        final Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(finish, 45_000L);

        boolean handled = RemoteCommandHandler.handle(app, body.toString(), reply -> {
            try {
                if (sender != null) {
                    SmsManager sm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? app.getSystemService(SmsManager.class)
                            : SmsManager.getDefault();
                    if (sm != null) {
                        for (String part : sm.divideMessage(reply)) {
                            sm.sendTextMessage(sender, null, part, null, null);
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                main.postDelayed(finish, 4_000L); // let a WIPE/late reply drain first
            }
        });
        if (!handled) finish.run();
    }
}
