package org.havenapp.main.autoarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires on each schedule edge: re-evaluate auto-arm, then re-arm the alarm for the next edge. */
public class ScheduleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AutoArmController.evaluate(context, "schedule");
        AutoArmScheduler.sync(context);
    }
}
