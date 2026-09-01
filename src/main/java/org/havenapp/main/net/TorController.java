package org.havenapp.main.net;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.havenapp.main.PreferenceManager;
import org.torproject.jni.TorService;

import java.net.InetSocketAddress;

/**
 * Optional built-in Tor. When {@link PreferenceManager#getEmbeddedTorEnabled()} is on we
 * run Guardian Project's {@link TorService} in-process instead of relying on Orbot; it
 * bootstraps a Tor client and exposes a local SOCKS proxy that
 * {@link org.havenapp.main.alerts.HttpPoster} routes "via Tor" traffic through.
 *
 * <p>{@code TorService} is a plain bound service — it starts Tor in {@code onCreate} and
 * keeps it running while something is bound, so we hold an application-context binding for
 * the process lifetime. Onion-service <em>hosting</em> still needs Orbot for now.
 */
public final class TorController {

    private static final String TAG = "TorController";
    private static final int DEFAULT_SOCKS = 9050;

    private static volatile String status = TorService.STATUS_OFF;
    private static volatile int socksPort = DEFAULT_SOCKS;
    private static boolean bound;
    private static BroadcastReceiver statusReceiver;
    private static ServiceConnection connection;

    private TorController() {}

    public static boolean isReady() {
        return TorService.STATUS_ON.equals(status);
    }

    public static String status() {
        return status;
    }

    /** SOCKS endpoint for "via Tor" traffic: embedded Tor's port when up, else Orbot's 9050. */
    public static InetSocketAddress socksAddress() {
        int port = DEFAULT_SOCKS;
        if (isReady()) {
            port = TorService.socksPort > 0 ? TorService.socksPort : socksPort;
        }
        if (port <= 0) port = DEFAULT_SOCKS;
        return new InetSocketAddress("127.0.0.1", port);
    }

    public static synchronized void start(Context context) {
        Context app = context.getApplicationContext();
        if (bound) return;
        if (!new PreferenceManager(app).getEmbeddedTorEnabled()) return;

        if (statusReceiver == null) {
            statusReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    String s = i.getStringExtra(TorService.EXTRA_STATUS);
                    if (s == null) return;
                    status = s;
                    if (TorService.STATUS_ON.equals(s) && TorService.socksPort > 0) {
                        socksPort = TorService.socksPort;
                        Log.i(TAG, "tor bootstrapped, socks=" + socksPort);
                    }
                }
            };
            ContextCompat.registerReceiver(app, statusReceiver,
                    new IntentFilter(TorService.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "TorService bound");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "TorService disconnected");
            }
        };

        try {
            bound = app.bindService(new Intent(app, TorService.class), connection,
                    Context.BIND_AUTO_CREATE);
            if (bound) {
                status = TorService.STATUS_STARTING;
                Log.i(TAG, "starting embedded Tor");
            } else {
                Log.w(TAG, "bindService returned false");
            }
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
        }
        if (!bound) {
            // don't leak the receiver if the bind never took
            connection = null;
            try {
                app.unregisterReceiver(statusReceiver);
            } catch (Exception ignored) {
            }
            statusReceiver = null;
        }
    }

    public static synchronized void stop(Context context) {
        if (!bound && connection == null && statusReceiver == null) return; // nothing to do
        Context app = context.getApplicationContext();
        if (bound && connection != null) {
            try {
                app.unbindService(connection);
            } catch (Exception ignored) {
            }
        }
        bound = false;
        connection = null;
        if (statusReceiver != null) {
            try {
                app.unregisterReceiver(statusReceiver);
            } catch (Exception ignored) {
            }
            statusReceiver = null;
        }
        status = TorService.STATUS_OFF;
        socksPort = DEFAULT_SOCKS;
        Log.i(TAG, "embedded Tor stopped");
    }

    /**
     * Start embedded Tor only when something actually needs it right now — alerts routed
     * over Tor while monitoring is armed, or the onion remote-access server running — and
     * stop it otherwise. Called from app start, the relevant Settings toggles, and when
     * the monitor service arms / disarms, so a bootstrap doesn't fire on every unrelated
     * background wake-up.
     */
    public static void reconcile(Context context) {
        PreferenceManager p = new PreferenceManager(context);
        boolean want = p.getEmbeddedTorEnabled()
                && ((p.getAlertsViaTor() && p.getMonitorServiceActive())
                    || p.getRemoteAccessActive());
        if (want) {
            start(context);
        } else {
            stop(context);
        }
    }
}
