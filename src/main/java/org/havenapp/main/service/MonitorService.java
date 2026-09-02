
/*
 * Copyright (c) 2017 Nathanial Freitas / Guardian Project
 *  * Licensed under the GPLv3 license.
 *
 * Copyright (c) 2013-2015 Marco Ziccardi, Luca Bonato
 * Licensed under the MIT license.
 */

package org.havenapp.main.service;


import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import org.havenapp.main.HavenApp;
import org.havenapp.main.MonitorActivity;
import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.alerts.AlertManager;
import org.havenapp.main.database.HavenEventDB;
import org.havenapp.main.model.Event;
import org.havenapp.main.model.EventTrigger;
import org.havenapp.main.resources.ResourceManager;
import org.havenapp.main.sensors.PowerConnectionReceiver;
import org.havenapp.main.sensors.SensingCoordinator;
import org.havenapp.main.sensors.SensorTriggerSink;

import java.util.Date;

@SuppressLint("HandlerLeak")
public class MonitorService extends LifecycleService implements SensorTriggerSink {

    /**
     * Monitor instance
     */
    private static MonitorService sInstance;
    private BroadcastReceiver screenStateReceiver;
    private boolean isScreenOn = true;

    /**
     * To show a notification on service start
     */
    private final static String channelId = "monitor_id";
    private final static CharSequence channelName = "Haven notifications";
    private final static String channelDescription= "Important messages from Haven";
	
    /**
     * Object used to retrieve shared preferences
     */
     private PreferenceManager mPrefs = null;

    /**
     * Owns every non-camera sensor monitor and the low-power tier state machine.
     */
    private SensingCoordinator mCoordinator = null;
    private org.havenapp.main.location.LocationTracker mLocationTracker = null;

    private PowerConnectionReceiver mPowerReceiver = null;

    private boolean mIsMonitoringActive = false;


    /**
     * Last Event instances
     */
    private Event mLastEvent;

    /**
     * Last sent notification time
     */
    private Date mLastNotification;

        /**
	 * Handler for incoming messages
	 */
    private class MessageHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {

		    //only accept alert if monitor is running
		    if (mIsMonitoringActive)
		        alert(msg.what,msg.getData().getString(KEY_PATH));
		}
	}

	public final static String KEY_PATH = "path";
		
	/**
	 * Messenger interface used by the camera pipeline (still Activity-driven until Phase 3).
	 */
	private final Messenger messenger = new Messenger(new MessageHandler());

    /**
     * Background Operations
     */
    private void setupScreenStateReceiver() {
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    isScreenOn = false;
                    // Just send broadcast - no background camera
                    android.os.Bundle offB = new android.os.Bundle();
                    offB.putBoolean("screen_on", false);
                    org.havenapp.main.HavenEventBus.post("screen_state_changed", offB);
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    isScreenOn = true;
                    android.os.Bundle onB = new android.os.Bundle();
                    onB.putBoolean("screen_on", true);
                    org.havenapp.main.HavenEventBus.post("screen_state_changed", onB);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        // Android 14 (API 34) requires an explicit exported flag on runtime-registered receivers.
        ContextCompat.registerReceiver(this, screenStateReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Application
     */
    private HavenApp mApp = null;
    private AlertManager alertManager;
	/**
	 * Called on service creation, sends a notification
	 */
    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mApp = (HavenApp)getApplication();
        mPrefs = new PreferenceManager(this);
        alertManager = new AlertManager(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannel();
        }

        setupScreenStateReceiver();

        startSensors();

        showNotification();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d("MonitorService", "onStartCommand called");

        // Immediately start foreground to establish camera access rights
        showNotification();

        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Restart service when task is removed
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(
                getApplicationContext(),
                1,
                restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
        );

        super.onTaskRemoved(rootIntent);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupNotificationChannel ()
    {
        android.app.NotificationManager manager = (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        android.app.NotificationChannel channel;
        channel = new android.app.NotificationChannel(channelId, channelName,
                android.app.NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(channelDescription);
        channel.setLightColor(Color.RED);
        channel.setImportance(android.app.NotificationManager.IMPORTANCE_MIN);
        manager.createNotificationChannel(channel);
    }

    public static MonitorService getInstance ()
    {
        return sInstance;
    }
    
    /**
     * Called on service destroy, cancels persistent notification
     * and shows a toast
     */
    @Override
    public void onDestroy() {
        if (screenStateReceiver != null) {
            unregisterReceiver(screenStateReceiver);
        }
        stopSensors();
        stopForeground(true);
        super.onDestroy();
    }

    /**
     * Legacy messenger binder (kept for the camera-config preview path). LifecycleService
     * requires the super call for its lifecycle dispatch.
     */
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return messenger.getBinder();
    }
    
    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        Intent toLaunch = new Intent(getApplicationContext(), MonitorActivity.class);
        toLaunch.setAction(Intent.ACTION_MAIN);
        toLaunch.addCategory(Intent.CATEGORY_LAUNCHER);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent resultPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            resultPendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    toLaunch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            resultPendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    toLaunch,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        CharSequence text = getText(R.string.secure_service_started);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_stat_haven)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(text);

        mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        mBuilder.setContentIntent(resultPendingIntent);
        mBuilder.setWhen(System.currentTimeMillis());
        mBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            if (mPrefs != null && mPrefs.getLocationTrackingEnabled()
                    && org.havenapp.main.location.LocationTracker.hasPermission(this)) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            startForeground(1, mBuilder.build(), type);
        } else {
            startForeground(1, mBuilder.build());
        }
    }

    public boolean isRunning ()
    {
        return mIsMonitoringActive;

    }

    private void startSensors ()
    {
        mIsMonitoringActive = true;

        // set current event start date in prefs
        mPrefs.setCurrentSession(new Date(System.currentTimeMillis()));

        mCoordinator = new SensingCoordinator(this, this, this);
        mCoordinator.start();

        WatchdogWorker.start(this);

        if (mPrefs.getLocationTrackingEnabled()) {
            mLocationTracker = new org.havenapp.main.location.LocationTracker(this);
            mLocationTracker.start();
        }

        mPrefs.activateMonitorService(true);

        // bring up embedded Tor now if alerts are routed through it
        org.havenapp.main.net.TorController.reconcile(this);

        mPowerReceiver = new PowerConnectionReceiver();
        // register our power status receivers (single filter, both actions)
        IntentFilter powerFilter = new IntentFilter();
        powerFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        powerFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ContextCompat.registerReceiver(this, mPowerReceiver, powerFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void stopSensors ()
    {
        mIsMonitoringActive = false;

        if (mCoordinator != null) {
            mCoordinator.stop();
            mCoordinator = null;
        }

        if (mLocationTracker != null) {
            mLocationTracker.stop();
            mLocationTracker = null;
        }

        if (mPrefs.getMonitorServiceActive()) {
            mPrefs.activateMonitorService(false);
        }
        // tear down embedded Tor unless the onion server still needs it
        org.havenapp.main.net.TorController.reconcile(this);
        WatchdogWorker.stop(this);

        if (mPowerReceiver != null) {
            try {
                unregisterReceiver(mPowerReceiver);
            } catch (IllegalArgumentException ignored) {
                // was never registered
            }
            mPowerReceiver = null;
        }
    }

    /** Sink for {@link SensingCoordinator}: route every sensor detection into {@link #alert}. */
    @Override
    public void onSensorTrigger(int eventTriggerType, String value) {
        if (mIsMonitoringActive) {
            alert(eventTriggerType, value);
        }
    }

    /** Remote PHOTO command entry point. */
    public void requestPhoto() {
        if (mCoordinator != null) mCoordinator.requestPhoto();
    }

    /**
     * Record a tamper trigger and grab a silent still (screen stays off). Called from the
     * Device Admin password callbacks and the app lock screen.
     */
    public void tamper(String reason) {
        if (!mIsMonitoringActive) return;
        alert(EventTrigger.TAMPER, reason == null ? "tamper" : reason);
        if (mCoordinator != null) mCoordinator.requestPhoto();
    }

    /** Called by {@link PowerConnectionReceiver} when the charger is plugged / unplugged. */
    public void onPowerConnectivityChanged(boolean charging) {
        if (mCoordinator != null) {
            mCoordinator.onPowerConnectivityChanged(charging);
        }
        if (mLocationTracker != null) {
            mLocationTracker.applyPowerPolicy();
        }
    }

    /**
    * Sends an alert according to type of connectivity
    */
    public void alert(int alertType, String value) {
        Date now = new Date();
        boolean doNotification = false;

        //for the UI visual
        android.os.Bundle evB = new android.os.Bundle();
        evB.putInt("type", alertType);
        org.havenapp.main.HavenEventBus.post("event", evB);

        // FIX: Don't return early for empty values - create the event anyway
        // if (TextUtils.isEmpty(value))
        //     return;

        // Use a default value if empty
        if (TextUtils.isEmpty(value)) {
            value = "detected";
        }

        if (mLastEvent == null) {
            mLastEvent = new Event();
            long eventId = HavenEventDB.getDatabase(getApplicationContext())
                    .getEventDAO().insert(mLastEvent);
            mLastEvent.setId(eventId);
            doNotification = true;
        }
        else if (mPrefs.getNotificationTimeMs() == 0)
        {
            doNotification = true;
        }
        else if (mPrefs.getNotificationTimeMs() > 0 && mLastNotification != null)
        {
            //check if time window is within configured notification time window
            doNotification = ((now.getTime()-mLastNotification.getTime())>mPrefs.getNotificationTimeMs());
        }

        if (doNotification)
        {
            doNotification = !(mPrefs.getVideoMonitoringActive() && alertType == EventTrigger.CAMERA);
        }

        EventTrigger eventTrigger = new EventTrigger();
        eventTrigger.setType(alertType);
        eventTrigger.setPath(value);
        eventTrigger.setEventId(mLastEvent.getId()); // Make sure eventId is set

        mLastEvent.addEventTrigger(eventTrigger);

        //we don't need to resave the event, only the trigger
        long eventTriggerId = HavenEventDB.getDatabase(getApplicationContext())
                .getEventTriggerDAO().insert(eventTrigger);
        eventTrigger.setId(eventTriggerId);

        org.havenapp.main.security.EvidenceLog.append(this, alertType, value);

        Log.d("MonitorService", "Event saved: type=" + alertType + " value=" + value + " triggerId=" + eventTriggerId);

        if (doNotification) {
            mLastNotification = new Date();

            StringBuilder alertMessage = new StringBuilder();
            alertMessage.append(getString(R.string.intrusion_detected,
                    eventTrigger.getStringType(new ResourceManager(this))));

            // Send via all configured alert channels
            alertManager.sendAlert(alertMessage.toString(), eventTrigger.getPath(), alertType);
        }
    }




}
