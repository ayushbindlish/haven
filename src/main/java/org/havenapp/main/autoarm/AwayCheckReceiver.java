package org.havenapp.main.autoarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AwayCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AwayWatcher.check(context);
        AwayWatcher.sync(context);
    }
}
