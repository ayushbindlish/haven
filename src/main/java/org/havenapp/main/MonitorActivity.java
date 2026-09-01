/*
 * Copyright (c) 2017 Nathanial Freitas
 *
 *   This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.havenapp.main;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.havenapp.main.model.EventTrigger;
import org.havenapp.main.service.MonitorService;
import org.havenapp.main.ui.AccelConfigureActivity;
import org.havenapp.main.ui.CameraConfigureActivity;
import org.havenapp.main.ui.CameraFragment;
import org.havenapp.main.ui.MicrophoneConfigureActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static org.havenapp.main.Utils.getTimerText;

public class MonitorActivity extends AppCompatActivity implements TimePickerDialog.OnTimeSetListener {

    private PreferenceManager preferences = null;

    private TextView txtTimer;

    private CountDownTimer cTimer;

    private boolean mIsMonitoring = false;
    private boolean mIsInitializedLayout = false;
    private boolean mOnTimerTicking = false;

    private final static int REQUEST_CAMERA = 999;
    private final static int REQUEST_TIMER = 1000;

    private CameraFragment mFragmentCamera;

    private View mBtnCamera, mBtnMic, mBtnAccel;
    private Animation mAnimShake;
    private TextView txtStatus;

    private int lastEventType = -1;

    AppCompatButton btnBlankScreen;
    private boolean isBlankScreenActive = false;

    /**
     * Looper used to update back the UI after motion detection
     */
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if (mIsMonitoring) {

                String message = null;
                View view = null;

                switch (msg.what) {
                    case EventTrigger.CAMERA:
                        view = mBtnCamera;
                        message = getString(R.string.motion_detected);
                        break;
                    case EventTrigger.POWER:
                        view = mBtnAccel;
                        message = getString(R.string.power_detected);
                        break;
                    case EventTrigger.MICROPHONE:
                        view = mBtnMic;
                        message = getString(R.string.sound_detected);
                        break;
                    case EventTrigger.ACCELEROMETER:
                    case EventTrigger.BUMP:
                        view = mBtnAccel;
                        message = getString(R.string.device_move_detected);
                        break;
                    case EventTrigger.LIGHT:
                        view = mBtnCamera;
                        message = getString(R.string.status_light);
                        break;
                }

                if (view != null) {
                    view.startAnimation(mAnimShake);
                }

                if (lastEventType != msg.what) {
                    if (!TextUtils.isEmpty(message)) {
                        txtStatus.setText(message);
                    }
                }

                lastEventType = msg.what;
            }
        }
    };

    final org.havenapp.main.HavenEventBus.Listener busListener =
            new org.havenapp.main.HavenEventBus.Listener() {
        @Override
        public void onHavenEvent(String action, android.os.Bundle extras) {
            if (!"event".equals(action) || extras == null) return;
            int eventType = extras.getInt("type", -1);
            boolean detected = extras.getBoolean("detected", true);
            if (detected)
                handler.sendEmptyMessage(eventType);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initSetupLayout();

        // Move this AFTER initSetupLayout() since it calls setContentView()
        findViewById(R.id.btnBlankScreen).setOnClickListener(v -> blankScreen());

        // Check if service is already running
        if (MonitorService.getInstance() != null && MonitorService.getInstance().isRunning()) {
            initActiveLayout();
        }

        // Request permissions after UI is set up: camera -> mic -> notifications.
        askForPermission(Manifest.permission.CAMERA, 2);
    }

    private void initActiveLayout() {

        ((Button) findViewById(R.id.btnStartLater)).setText(R.string.action_cancel);
        findViewById(R.id.btnStartNow).setVisibility(View.INVISIBLE);
        findViewById(R.id.timer_text_title).setVisibility(View.INVISIBLE);
        txtTimer.setText(R.string.status_on);

        mOnTimerTicking = false;
        mIsMonitoring = true;
    }

    private void updateBlankScreenButton() {
        if (btnBlankScreen != null) {
            if (mIsMonitoring) {
                btnBlankScreen.setVisibility(View.VISIBLE);
            } else {
                btnBlankScreen.setVisibility(View.GONE);
            }
        }
    }
    private void initSetupLayout() {
        setContentView(R.layout.activity_monitor);
        org.havenapp.main.Utils.applyBarInsets(findViewById(android.R.id.content), true, true, true);
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (mIsMonitoring) {
                            moveTaskToBack(true);
                        } else {
                            finish();
                        }
                    }
                });

        // Blank button
        btnBlankScreen = findViewById(R.id.btnBlankScreen);

        if (btnBlankScreen != null) {
            btnBlankScreen.setOnClickListener(v -> {
                blankScreen();
                btnBlankScreen.setVisibility(View.GONE);
            });
        } else {
            Log.e("MonitorActivity", "btnBlankScreen not found in layout");
        }

        updateBlankScreenButton();

        Log.d("MonitorActivity", "initSetupLayout called");
        preferences = new PreferenceManager(getApplicationContext());

        Log.d("MonitorActivity", "setContentView called");

        // Make fragment visible BEFORE any camera operations
        findViewById(R.id.fragment_camera).setVisibility(View.VISIBLE);

        txtTimer = (TextView) findViewById(R.id.timer_text);
        View viewTimer = findViewById(R.id.timer_container);

        int timeM = preferences.getTimerDelay() * 1000;

        txtTimer.setText(getTimerText(timeM));
        txtTimer.setOnClickListener(v -> {
            if (cTimer == null)
                showTimeDelayDialog();
        });

        findViewById(R.id.timer_text_title).setOnClickListener(v -> {
            if (cTimer == null)
                showTimeDelayDialog();
        });

        findViewById(R.id.btnStartLater).setOnClickListener(v -> {
            Log.d("MonitorActivity", "Start Later clicked");
            doCancel();
        });

        findViewById(R.id.btnStartNow).setOnClickListener(v -> {
            Log.d("MonitorActivity", "Start Now clicked");
            ((Button) findViewById(R.id.btnStartLater)).setText(R.string.action_cancel);
            findViewById(R.id.btnStartNow).setVisibility(View.INVISIBLE);
            findViewById(R.id.timer_text_title).setVisibility(View.INVISIBLE);
            initTimer();
        });

        mBtnAccel = findViewById(R.id.btnAccelSettings);
        mBtnAccel.setOnClickListener(v -> {
            Log.d("MonitorActivity", "Accel button clicked");
            if (!mIsMonitoring)
                startActivity(new Intent(MonitorActivity.this, AccelConfigureActivity.class));
        });

        mBtnMic = findViewById(R.id.btnMicSettings);
        mBtnMic.setOnClickListener(v -> {
            Log.d("MonitorActivity", "Mic button clicked");
            if (!mIsMonitoring)
                startActivity(new Intent(MonitorActivity.this, MicrophoneConfigureActivity.class));
        });

        mBtnCamera = findViewById(R.id.btnCameraSwitch);
        mBtnCamera.setOnClickListener(v -> {
            Log.d("MonitorActivity", "Camera button clicked");
            if (!mIsMonitoring)
                configCamera();
        });

        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Log.d("MonitorActivity", "Settings button clicked");
            showSettings();
        });

        mFragmentCamera = ((CameraFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_camera));

        // remove fragment, do this in onResume instead

        txtStatus = findViewById(R.id.txtStatus);
        mAnimShake = AnimationUtils.loadAnimation(this, R.anim.shake);
        mIsInitializedLayout = true;
    }

    private void configCamera() {
        // Stop camera BEFORE launching config activity
        if (mFragmentCamera != null) {
            mFragmentCamera.stopCamera();
        }

        startActivityForResult(new Intent(this, CameraConfigureActivity.class), REQUEST_CAMERA);
    }



    private void updateTimerValue(int val) {
        preferences.setTimerDelay(val);
        int valM = val * 1000;
        txtTimer.setText(getTimerText(valM));
    }

    private final static int REQUEST_STOP_LOCK = 1003;

    private void doCancel() {
        boolean wasTimer = false;

        // Disarming requires the PIN if the user set one and enabled "PIN to stop".
        if (mIsMonitoring) {
            org.havenapp.main.security.PinManager pin =
                    new org.havenapp.main.security.PinManager(this);
            if (pin.hasPin() && pin.isPinToStop()
                    && !org.havenapp.main.security.PinManager.unlockedThisProcess) {
                android.content.Intent i = new android.content.Intent(this,
                        org.havenapp.main.ui.LockActivity.class);
                i.putExtra(org.havenapp.main.ui.LockActivity.EXTRA_REASON, "stop");
                startActivityForResult(i, REQUEST_STOP_LOCK);
                return;
            }
        }

        // If screen is blanked, unblank it first to ensure proper cleanup
        if (isBlankScreenActive) {
            unblankScreen();
        }

        if (cTimer != null) {
            cTimer.cancel();
            cTimer = null;
            mOnTimerTicking = false;
            wasTimer = true;
        }

        if (mIsMonitoring) {
            mIsMonitoring = false;
            btnBlankScreen.setVisibility(View.GONE);

            // Ensure camera is stopped before stopping service
            if (mFragmentCamera != null) {
                mFragmentCamera.stopCamera();
            }

            stopService(new Intent(this, MonitorService.class));
            finish();
        } else {
            findViewById(R.id.btnStartNow).setVisibility(View.VISIBLE);
            findViewById(R.id.timer_text_title).setVisibility(View.VISIBLE);

            ((Button) findViewById(R.id.btnStartLater)).setText(R.string.start_later);

            int timeM = preferences.getTimerDelay() * 1000;
            txtTimer.setText(getTimerText(timeM));

            if (!wasTimer)
                finish();
        }
    }


    private void showSettings() {

        Intent i = new Intent(this, SettingsActivity.class);

        if (cTimer != null) {
            cTimer.cancel();
            cTimer = null;
            startActivityForResult(i, REQUEST_TIMER);

        } else {
            startActivity(i);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CAMERA) {
            // Restart camera after config
            if (mFragmentCamera != null) {
                mFragmentCamera.initCamera();
            }
        } else if (requestCode == REQUEST_STOP_LOCK && resultCode == RESULT_OK) {
            doCancel(); // PIN accepted -> proceed with the disarm
        }
    }

    @Override
    protected void onDestroy() {
        if (!mIsMonitoring && mFragmentCamera != null) {
            mFragmentCamera.stopCamera();
        }
        // Don't stop the service here if monitoring is active
        super.onDestroy();
    }

    private void initTimer() {
        txtTimer.setTextColor(getResources().getColor(R.color.colorAccent));

        // Set the session before starting the timer
        preferences.setCurrentSession(new Date(System.currentTimeMillis()));

        cTimer = new CountDownTimer((preferences.getTimerDelay()) * 1000L, 1000) {
            public void onTick(long millisUntilFinished) {
                mOnTimerTicking = true;
                txtTimer.setText(getTimerText(millisUntilFinished));
            }

            public void onFinish() {
                txtTimer.setText(R.string.status_on);
                initMonitor();
                mOnTimerTicking = false;
            }
        };

        cTimer.start();
    }

    private void initMonitor() {
        mIsMonitoring = true;

        btnBlankScreen.setVisibility(View.VISIBLE);

        // The service (CameraX) now owns the camera while monitoring; release the
        // preview camera so the two don't collide.
        if (mFragmentCamera != null) {
            mFragmentCamera.stopCamera();
        }

        Intent serviceIntent = new Intent(this, MonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            // e.g. ForegroundServiceStartNotAllowedException (API 31+) if the OS decides
            // the app is not sufficiently in the foreground. Surface it rather than crash.
            mIsMonitoring = false;
            Log.e("Monitor", "unable to start MonitorService", e);
            Toast.makeText(this, R.string.secure_service_stopped, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Use app-specific external directory instead of public external storage
            File fileImageDir = new File(getExternalFilesDir(null), preferences.getDefaultMediaStoragePath());

            // More robust directory creation
            if (!fileImageDir.exists()) {
                boolean created = fileImageDir.mkdirs();
                if (!created) {
                    Log.e("Monitor", "Failed to create directory: " + fileImageDir.getAbsolutePath());
                }
            }

            File nomediaFile = new File(fileImageDir, ".nomedia");
            if (!nomediaFile.exists()) {
                nomediaFile.createNewFile();
            }
        } catch (IOException e) {
            Log.e("Monitor", "unable to init media storage directory", e);
        }
    }


    /**
     * When the user leaves the activity: monitoring keeps running in MonitorService
     * (foreground, camera + sensors), so just drop to the background rather than tearing
     * the Activity down abruptly.
     */

    private void showTimeDelayDialog() {
        int totalSecs = preferences.getTimerDelay();

        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;

        TimePickerDialog mTimePickerDialog = TimePickerDialog.newInstance(this, hours, minutes, seconds, true);
        mTimePickerDialog.enableSeconds(true);
        mTimePickerDialog.show(getSupportFragmentManager(), "TimePickerDialog");
    }

    @Override
    public void onResume() {
        super.onResume();

        // Only restart camera if it was actually stopped (not monitoring)
        if (!mIsMonitoring && mFragmentCamera != null) {
            mFragmentCamera.initCamera();
        }

        // Rest of existing onResume code...
        if (mFragmentCamera != null && mFragmentCamera.getView() != null) {
            mFragmentCamera.getView().setClickable(false);
            mFragmentCamera.getView().setFocusable(false);
            Log.d("MonitorActivity", "Fragment view configured");
        }

        if (mIsInitializedLayout && (!mOnTimerTicking) && (!mIsMonitoring)) {
            int totalMilliseconds = preferences.getTimerDelay() * 1000;
            txtTimer.setText(getTimerText(totalMilliseconds));
        }

        org.havenapp.main.HavenEventBus.register(busListener);
    }

    private void blankScreen() {
        if (isBlankScreenActive) {
            unblankScreen();
            return;
        }

        isBlankScreenActive = true;

        // The camera runs in the service now, so blanking is purely cosmetic: black the
        // screen and drop brightness so the phone doesn't look like it's doing anything.
        if (mFragmentCamera != null) {
            mFragmentCamera.stopCamera();
        }

        // Hide all UI elements
        findViewById(R.id.buttonBar).setVisibility(View.GONE);
        findViewById(R.id.timer_container).setVisibility(View.GONE);
        findViewById(R.id.txtStatus).setVisibility(View.GONE);

        // Set black background
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        // Keep screen on but dimmed
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = 0.01f; // Almost completely dark
        getWindow().setAttributes(params);

        // Add invisible tap overlay with proper touch handling
        View blankOverlay = new View(this);
        blankOverlay.setBackgroundColor(Color.BLACK);

        // Add gesture detection for double-tap to exit
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                unblankScreen();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                showTemporaryStatus();
                return true;
            }
        });

        blankOverlay.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true; // Consume all touch events
        });

        ViewGroup rootView = findViewById(android.R.id.content);
        rootView.addView(blankOverlay);
        blankOverlay.setTag("blank_overlay");

        Log.d("MonitorActivity", "Screen blanked (monitoring continues in the service)");
        Toast.makeText(this, "Double-tap to restore screen", Toast.LENGTH_LONG).show();
    }

    private void unblankScreen() {
        if (!isBlankScreenActive) return;

        isBlankScreenActive = false;

        // Remove blank overlay
        ViewGroup rootView = findViewById(android.R.id.content);
        View overlay = rootView.findViewWithTag("blank_overlay");
        if (overlay != null) {
            rootView.removeView(overlay);
        }

        // Restore UI elements
        findViewById(R.id.buttonBar).setVisibility(View.VISIBLE);
        if (mIsMonitoring) {
            findViewById(R.id.timer_container).setVisibility(View.VISIBLE);
            findViewById(R.id.txtStatus).setVisibility(View.VISIBLE);
        }

        // Restore normal background
        getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);

        // Restore normal brightness
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(params);

        Log.d("MonitorActivity", "Screen restored - camera view back to full size");
    }

    private void showTemporaryStatus() {
        if (!mIsMonitoring) return;

        TextView statusText = findViewById(R.id.txtStatus);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("Monitoring Active - Double-tap to restore");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isBlankScreenActive && mIsMonitoring) {
                statusText.setVisibility(View.GONE);
            }
        }, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        org.havenapp.main.HavenEventBus.unregister(busListener);
        if (!mIsMonitoring && mFragmentCamera != null) {
            mFragmentCamera.stopCamera();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 2:
                askForPermission(Manifest.permission.RECORD_AUDIO, 3);
                break;
            case 3:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askForPermission(Manifest.permission.POST_NOTIFICATIONS, 4);
                }
                break;
            case 4:
                // Permission chain complete.
                break;
        }
    }


    private boolean askForPermission(String permission, Integer requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {

                //This is called if user has denied the permission before
                //In this case I am just asking the permission again
                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);

            } else {

                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onTimeSet(TimePickerDialog view, int hourOfDay, int minute, int second) {
        int delaySeconds = second + minute * 60 + hourOfDay * 60 * 60;
        updateTimerValue(delaySeconds);
    }

}
