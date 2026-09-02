package org.havenapp.main.autoarm;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.havenapp.main.PreferenceManager;

/**
 * Watches the two "am I with the phone" radios:
 *  - a trusted Bluetooth device (e.g. a watch) connecting / disconnecting
 *  - the Wi-Fi network changing (connecting to / leaving a trusted SSID)
 * and pokes {@link AutoArmController} to re-evaluate.
 */
public class TrustedRadioReceiver extends BroadcastReceiver {

    private static final String TAG = "AutoArm";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        PreferenceManager p = new PreferenceManager(context);
        if (!p.getAutoArmEnabled()) return;

        switch (action) {
            case BluetoothDevice.ACTION_ACL_CONNECTED:
            case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (dev == null) return;
                String addr = dev.getAddress();
                boolean connected = BluetoothDevice.ACTION_ACL_CONNECTED.equals(action);
                AutoArmController.noteBtConnection(context, addr, connected);
                if (AutoArmController.isTrustedBt(context, addr)) {
                    Log.i(TAG, "trusted BT " + addr + (connected ? " connected" : " disconnected"));
                    AutoArmController.evaluate(context,
                            connected ? "bt-connect" : "bt-disconnect");
                }
                break;
            }
            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                AutoArmController.evaluate(context, "wifi");
                break;
            default:
                break;
        }
    }
}
