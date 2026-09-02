package org.havenapp.main.ui;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.appintro.AppIntro;
import com.github.appintro.AppIntroFragment;
import com.github.appintro.AppIntroPageTransformerType;

import org.havenapp.main.MonitorActivity;
import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;

import java.util.ArrayList;
import java.util.List;

public class PPAppIntro extends AppIntro {

    private static final int REQUEST_ACCEL_CONFIG = 1001;
    private static final int REQUEST_MIC_CONFIG = 1002;
    private static final int REQUEST_CAMERA_CONFIG = 1003;

    /** Slides in order, so we can map the current fragment back to an index (AppIntro 6
     *  removed the public pager). */
    private final List<Fragment> slides = new ArrayList<>();
    private int currentPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge (targetSdk 36): pad the intro content clear of the system bars.
        org.havenapp.main.Utils.applyBarInsets(findViewById(android.R.id.content), true, true, true);

        setTransformer(AppIntroPageTransformerType.Fade.INSTANCE);
        setWizardMode(true);
        setIndicatorEnabled(true);
        setIndicatorColor(
                ContextCompat.getColor(this, R.color.colorAccent),
                ContextCompat.getColor(this, R.color.colorPrimaryLight)
        );

        // Slide 1: Welcome & App Purpose
        track(AppIntroFragment.createInstance(
                getString(R.string.intro1_title),
                getString(R.string.intro1_desc),
                R.drawable.web_hi_res_512,
                R.color.colorPrimaryDark, // @ColorRes in AppIntro 6
                0, 0, 0, 0, 0
        ));

        // Slide 2: Privacy & Security Focus
        CustomSlideBigText privacySlide = CustomSlideBigText.newInstance(R.layout.custom_slide_big_text);
        privacySlide.setTitle(getString(R.string.intro2_title));
        track(privacySlide);

        // Slide 3: Sensor Configuration
        CustomSlideBigText configSlide = CustomSlideBigText.newInstance(R.layout.custom_slide_big_text);
        configSlide.setTitle(getString(R.string.intro3_desc));
        configSlide.showButton(getString(R.string.action_configure), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConfigurationFlow();
            }
        });
        track(configSlide);

        // Slide 4: How It Works
        CustomSlideBigText howItWorksSlide = CustomSlideBigText.newInstance(R.layout.custom_slide_big_text);
        howItWorksSlide.setTitle(getString(R.string.intro4_desc));
        track(howItWorksSlide);

        // Slide 5: Notifications Setup
        final CustomSlideNotify notifySlide = CustomSlideNotify.newInstance(R.layout.custom_slide_notify);
        notifySlide.setSaveListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phoneNumber = notifySlide.getPhoneNumber();
                if (isValidPhoneNumber(phoneNumber)) {
                    PreferenceManager pm = new PreferenceManager(PPAppIntro.this);
                    pm.setRemotePhoneNumber(phoneNumber);
                    Toast.makeText(PPAppIntro.this, R.string.phone_saved, Toast.LENGTH_SHORT).show();
                    goToNextSlide();
                } else {
                    Toast.makeText(PPAppIntro.this, R.string.invalid_phone_number, Toast.LENGTH_SHORT).show();
                }
            }
        });
        track(notifySlide);

        // Slide 6: Ready to Protect
        track(AppIntroFragment.createInstance(
                getString(R.string.intro5_title),
                getString(R.string.intro5_desc),
                R.drawable.web_hi_res_512,
                R.color.colorPrimaryDark, // @ColorRes in AppIntro 6
                0, 0, 0, 0, 0
        ));

        setDoneText(getString(R.string.onboarding_action_end));
        setSkipButtonEnabled(false);

        // Back on the first slide asks to confirm exit; elsewhere it steps back a slide.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentPosition <= 0) {
                    new AlertDialog.Builder(PPAppIntro.this)
                            .setTitle(R.string.exit_setup_title)
                            .setMessage(R.string.exit_setup_message)
                            .setPositiveButton(R.string.continue_setup, null)
                            .setNegativeButton(R.string.exit_anyway, (dialog, which) -> {
                                setDefaultPreferences();
                                finish();
                            })
                            .show();
                } else {
                    goToPreviousSlide();
                }
            }
        });
    }

    private void startConfigurationFlow() {
        // Check sensor permissions BEFORE launching AccelConfigureActivity
        checkSensorPermissionsFirst();
    }

    private void checkSensorPermissionsFirst() {
        // Try to access accelerometer to trigger GrapheneOS permission dialog
        SensorManager sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (sensor != null) {
            try {
                // Register listener briefly to trigger GrapheneOS sensors permission
                SensorEventListener dummyListener = new SensorEventListener() {
                    public void onSensorChanged(SensorEvent event) {
                        // Immediately unregister after first reading
                        sensorMgr.unregisterListener(this);
                        // Now launch the actual accelerometer configuration
                        launchAccelConfig();
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                };

                sensorMgr.registerListener(dummyListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

            } catch (Exception e) {
                // If sensor access fails, still proceed
                launchAccelConfig();
            }
        } else {
            // No accelerometer, still proceed
            launchAccelConfig();
        }
    }

    private void launchAccelConfig() {
        // Start with accelerometer configuration
        Intent accelIntent = new Intent(this, AccelConfigureActivity.class);
        accelIntent.putExtra("from_onboarding", true);
        startActivityForResult(accelIntent, REQUEST_ACCEL_CONFIG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ACCEL_CONFIG:
                // After accelerometer config, start microphone config
                Intent micIntent = new Intent(this, MicrophoneConfigureActivity.class);
                micIntent.putExtra("from_onboarding", true);
                startActivityForResult(micIntent, REQUEST_MIC_CONFIG);
                break;

            case REQUEST_MIC_CONFIG:
                // After microphone config, start camera config
                Intent cameraIntent = new Intent(this, CameraConfigureActivity.class);
                cameraIntent.putExtra("from_onboarding", true);
                startActivityForResult(cameraIntent, REQUEST_CAMERA_CONFIG);
                break;

            case REQUEST_CAMERA_CONFIG:
                // Configuration flow complete
                Toast.makeText(this, R.string.sensors_configured, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        // Basic phone number validation
        return phoneNumber != null &&
                phoneNumber.trim().length() >= 10 &&
                phoneNumber.matches("^[+]?[0-9\\s\\-\\(\\)]+$");
    }

    @Override
    protected void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        // Set default preferences if user skips
        setDefaultPreferences();
        launchMainActivity();
    }

    @Override
    protected void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);

        // Mark onboarding as complete
        PreferenceManager pm = new PreferenceManager(this);
        pm.setFirstLaunch(false);

        // Show completion message
        Toast.makeText(this, R.string.setup_complete, Toast.LENGTH_SHORT).show();

        launchMainActivity();
    }

    private void launchMainActivity() {
        Intent intent = new Intent(this, MonitorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("onboarding_complete", true);
        startActivity(intent);

        setResult(RESULT_OK);
        finish();
    }

    private void setDefaultPreferences() {
        PreferenceManager pm = new PreferenceManager(this);
        // Set reasonable defaults if user skips setup
        pm.setMicrophoneSensitivity("Medium");
        pm.setAccelerometerSensitivity("50");
        pm.setCameraSensitivity(30000);
        pm.setTimerDelay(30); // 30 second countdown
    }

    @Override
    protected void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);

        int pos = newFragment == null ? -1 : slides.indexOf(newFragment);
        if (pos >= 0) currentPosition = pos;

        switch (currentPosition) {
            case 0:
                // Welcome slide
                break;
            case 2:
                // Configuration slide - check sensor availability
                checkSensorAvailability();
                break;
            case 4:
                // Notification slide - prefill existing phone number
                prefillPhoneNumber();
                break;
        }
    }

    /** addSlide() is final in AppIntro 6, so track order here for onSlideChanged mapping. */
    private void track(Fragment fragment) {
        slides.add(fragment);
        addSlide(fragment);
    }

    private void checkSensorAvailability() {
        // Check if required sensors are available and show warnings if not
        // This could be implemented to inform users about missing sensors
    }

    private void prefillPhoneNumber() {
        // If user has already entered a phone number, prefill it
        PreferenceManager pm = new PreferenceManager(this);
        String existingNumber = pm.getRemotePhoneNumber();
        if (!existingNumber.isEmpty()) {
            // Prefill the phone number field if it exists
        }
    }
}
