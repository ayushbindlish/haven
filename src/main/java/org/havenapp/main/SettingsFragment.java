package org.havenapp.main;

/**
 * Created by Anupam Das (opticod) on 29/12/17.
 */

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.havenapp.main.service.WebServer;
import org.havenapp.main.ui.AccelConfigureActivity;
import org.havenapp.main.ui.CameraConfigureActivity;
import org.havenapp.main.ui.MicrophoneConfigureActivity;

import java.io.File;
import java.util.Locale;

import info.guardianproject.netcipher.proxy.OrbotHelper;


public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, TimePickerDialog.OnTimeSetListener {

    private PreferenceManager preferences;
    private HavenApp app;
    private AppCompatActivity mActivity;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // Read/write the same SharedPreferences file PreferenceManager uses, so
        // preference widgets bind directly to app settings without a manual bridge.
        getPreferenceManager().setSharedPreferencesName("org.havenapp.main");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);
        addPreferencesFromResource(R.xml.settings);
        mActivity = (AppCompatActivity) getActivity();
        preferences = new PreferenceManager(mActivity);
        setHasOptionsMenu(true);
        app = (HavenApp) mActivity.getApplication();

        File directory = new File(mActivity.getExternalFilesDir(null), preferences.getDirPath());
        directory.mkdirs();

        if (preferences.getCameraActivation()) {
            String camera = preferences.getCamera();
            switch (camera) {
                case PreferenceManager.FRONT:
                    ((ListPreference) findPreference(PreferenceManager.CAMERA)).setValueIndex(0);
                    break;
                case PreferenceManager.BACK:
                    ((ListPreference) findPreference(PreferenceManager.CAMERA)).setValueIndex(1);
                    break;
                case PreferenceManager.OFF:
                    ((ListPreference) findPreference(PreferenceManager.CAMERA)).setValueIndex(2);
                    break;
            }
        }

        SwitchPreference smsSwitch =
                (SwitchPreference) findPreference(PreferenceManager.REMOTE_NOTIFICATION_ACTIVE);
        smsSwitch.setChecked(preferences.isRemoteNotificationActive());
        smsSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            preferences.setRemoteNotificationActive((Boolean) newValue);
            return true;
        });

        findPreference(PreferenceManager.REMOTE_PHONE_NUMBER).setOnPreferenceClickListener(preference -> {
            if (preferences.getRemotePhoneNumber().isEmpty()) {
                ((EditTextPreference) findPreference(PreferenceManager.REMOTE_PHONE_NUMBER)).setText(getCountryCode());
            }
            return false;
        });

        if (checkValidString(preferences.getRemotePhoneNumber())) {
            ((EditTextPreference) findPreference(PreferenceManager.REMOTE_PHONE_NUMBER))
                    .setText(preferences.getRemotePhoneNumber());
            findPreference(PreferenceManager.REMOTE_PHONE_NUMBER).setSummary(preferences.getRemotePhoneNumber());
        } else {
            findPreference(PreferenceManager.REMOTE_PHONE_NUMBER).setSummary(R.string.sms_dialog_summary);
        }

        if (preferences.getRemoteAccessActive()) {
            ((SwitchPreference) findPreference(PreferenceManager.REMOTE_ACCESS_ACTIVE)).setChecked(true);
        }

        if (checkValidString(preferences.getRemoteAccessOnion())) {
            ((EditTextPreference) findPreference(PreferenceManager.REMOTE_ACCESS_ONION)).setText(preferences.getRemoteAccessOnion().trim() + ":" + WebServer.LOCAL_PORT);
            findPreference(PreferenceManager.REMOTE_ACCESS_ONION).setSummary(preferences.getRemoteAccessOnion().trim() + ":" + WebServer.LOCAL_PORT);
        } else {
            findPreference(PreferenceManager.REMOTE_ACCESS_ONION).setSummary(R.string.remote_access_hint);
        }

        if (checkValidString(preferences.getRemoteAccessCredential())) {
            ((EditTextPreference) findPreference(PreferenceManager.REMOTE_ACCESS_CRED)).setText(preferences.getRemoteAccessCredential().trim());
            findPreference(PreferenceManager.REMOTE_ACCESS_CRED).setSummary(R.string.bullets);
        } else {
            findPreference(PreferenceManager.REMOTE_ACCESS_CRED).setSummary(R.string.remote_access_credential_hint);
        }

        if (preferences.getNotificationTimeMs() > 0) {
            findPreference(PreferenceManager.NOTIFICATION_TIME).setSummary(preferences.getNotificationTimeMs() / 60000 + " " + getString(R.string.minutes));
        }

        findPreference(PreferenceManager.CAMERA_SENSITIVITY).setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(mActivity, CameraConfigureActivity.class));
            return true;
        });

        findPreference(PreferenceManager.CONFIG_MOVEMENT).setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(mActivity, AccelConfigureActivity.class));
            return true;
        });

        findPreference(PreferenceManager.CONFIG_SOUND).setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(mActivity, MicrophoneConfigureActivity.class));
            return true;
        });

        findPreference(PreferenceManager.CONFIG_TIME_DELAY).setOnPreferenceClickListener(preference -> {
            showTimeDelayDialog(PreferenceManager.CONFIG_TIME_DELAY);
            return true;
        });

        findPreference(PreferenceManager.CONFIG_VIDEO_LENGTH).setOnPreferenceClickListener(preference -> {
            showTimeDelayDialog(PreferenceManager.CONFIG_VIDEO_LENGTH);
            return true;
        });

        findPreference(PreferenceManager.DISABLE_BATTERY_OPT).setOnPreferenceClickListener(preference -> {
            requestChangeBatteryOptimizations();
            return true;
        });

        org.havenapp.main.security.PinManager pin = new org.havenapp.main.security.PinManager(mActivity);
        Preference pinPref = findPreference("pin_set");
        pinPref.setSummary(pin.hasPin() ? R.string.pin_set_summary_set : R.string.pin_set_summary_none);
        pinPref.setOnPreferenceClickListener(p -> {
            showPinDialog();
            return true;
        });

        Preference addPlace = findPreference("add_place_here");
        if (addPlace != null) addPlace.setOnPreferenceClickListener(p -> {
            saveCurrentPlace();
            return true;
        });
        Preference clearPlaces = findPreference("clear_places");
        if (clearPlaces != null) clearPlaces.setOnPreferenceClickListener(p -> {
            new org.havenapp.main.location.GeofenceStore(mActivity).clear();
            android.widget.Toast.makeText(mActivity, R.string.clear_places_title,
                    android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });

        Preference showQr = findPreference("supervised_show_qr");
        if (showQr != null) showQr.setOnPreferenceClickListener(p -> {
            startActivity(new Intent(mActivity, org.havenapp.main.pairing.SupervisedSetupActivity.class));
            return true;
        });
        Preference disSup = findPreference("supervised_disable");
        if (disSup != null) disSup.setOnPreferenceClickListener(p -> {
            preferences.setSupervisedEnabled(false);
            org.havenapp.main.service.SupervisorWorker.reschedule(mActivity);
            android.widget.Toast.makeText(mActivity, R.string.supervised_off,
                    android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });

        Preference backupNow = findPreference("backup_now");
        if (backupNow != null) backupNow.setOnPreferenceClickListener(p -> {
            if (!org.havenapp.main.backup.BackupManager.configured(mActivity)) {
                android.widget.Toast.makeText(mActivity, R.string.backup_not_configured,
                        android.widget.Toast.LENGTH_LONG).show();
            } else {
                android.widget.Toast.makeText(mActivity, R.string.backup_started,
                        android.widget.Toast.LENGTH_SHORT).show();
                org.havenapp.main.service.BackupWorker.runNow(mActivity);
            }
            return true;
        });

        Preference duressPref = findPreference("duress_pin_set");
        if (duressPref != null) {
            org.havenapp.main.security.PinManager pm2 = new org.havenapp.main.security.PinManager(mActivity);
            duressPref.setSummary(pm2.hasDuressPin() ? R.string.pin_set_summary_set : R.string.duress_pin_summary);
            duressPref.setOnPreferenceClickListener(p -> {
                showDuressPinDialog();
                return true;
            });
        }

        Preference adminPref = findPreference("admin_protect");
        refreshAdminSummary(adminPref);
        adminPref.setOnPreferenceClickListener(p -> {
            org.havenapp.main.security.AdminManager am =
                    new org.havenapp.main.security.AdminManager(mActivity);
            if (am.isActive()) {
                am.deactivate();
                refreshAdminSummary(p);
            } else {
                startActivity(am.activationIntent());
            }
            return true;
        });

        // Kick off the runtime-permission chain: camera -> mic -> notifications.
        askForPermission(Manifest.permission.CAMERA, 2);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            save();
            return true;
        }
        return false;
    }

    protected void save() {
        preferences.activateAccelerometer(true);
        preferences.activateCamera(true);
        preferences.activateMicrophone(true);

        setPhoneNumber();

        boolean videoMonitoringActive = ((SwitchPreference) findPreference(mActivity.getResources().getString(R.string.video_active_preference_key))).isChecked();
        preferences.setActivateVideoMonitoring(videoMonitoringActive);

        boolean smsActive = ((SwitchPreference) findPreference(PreferenceManager.REMOTE_NOTIFICATION_ACTIVE)).isChecked();
        preferences.setRemoteNotificationActive(smsActive);

        boolean remoteAccessActive = ((SwitchPreference) findPreference(PreferenceManager.REMOTE_ACCESS_ACTIVE)).isChecked();
        preferences.activateRemoteAccess(remoteAccessActive);
        String password = ((EditTextPreference) findPreference(PreferenceManager.REMOTE_ACCESS_CRED)).getText();

        if (checkValidStrings(password, preferences.getRemoteAccessCredential())
                && (TextUtils.isEmpty(preferences.getRemoteAccessCredential())
                    || !password.trim().equals(preferences.getRemoteAccessCredential().trim()))) {
            preferences.setRemoteAccessCredential(password.trim());
            app.stopServer();
            app.startServer();
        }

        mActivity.setResult(AppCompatActivity.RESULT_OK);
        mActivity.finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == org.havenapp.main.net.ContentFilter.REQUEST_VPN_CONSENT) {
            SwitchPreference sw = findPreference("content_filter_enabled");
            if (resultCode == Activity.RESULT_OK) {
                org.havenapp.main.net.ContentFilter.start(mActivity);
            } else if (sw != null) {
                sw.setChecked(false);
            }
            return;
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            String onionHost = data.getStringExtra("hs_host");
            if (checkValidString(onionHost)) {
                preferences.setRemoteAccessOnion(onionHost.trim());
                ((EditTextPreference) findPreference(PreferenceManager.REMOTE_ACCESS_ONION)).setText(preferences.getRemoteAccessOnion().trim() + ":" + WebServer.LOCAL_PORT);
                if (checkValidString(preferences.getRemoteAccessOnion())) {
                    findPreference(PreferenceManager.REMOTE_ACCESS_ONION).setSummary(preferences.getRemoteAccessOnion().trim() + ":" + WebServer.LOCAL_PORT);
                } else {
                    findPreference(PreferenceManager.REMOTE_ACCESS_ONION).setSummary(R.string.remote_access_hint);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null) return;
        switch (key) {
            case PreferenceManager.CAMERA:
                switch (Integer.parseInt(((ListPreference) findPreference(PreferenceManager.CAMERA)).getValue())) {
                    case 0:
                        preferences.setCamera(PreferenceManager.FRONT);
                        findPreference(PreferenceManager.CAMERA).setSummary(PreferenceManager.FRONT);
                        break;
                    case 1:
                        preferences.setCamera(PreferenceManager.BACK);
                        findPreference(PreferenceManager.CAMERA).setSummary(PreferenceManager.BACK);
                        break;
                    case 2:
                        preferences.setCamera(PreferenceManager.NONE);
                        findPreference(PreferenceManager.CAMERA).setSummary(PreferenceManager.NONE);
                        break;
                }
                break;
            case PreferenceManager.REMOTE_ACCESS_ACTIVE:
                boolean remoteAccessActive = ((SwitchPreference) findPreference(PreferenceManager.REMOTE_ACCESS_ACTIVE)).isChecked();
                if (remoteAccessActive) {
                    checkRemoteAccessOnion();
                    app.startServer();
                } else {
                    app.stopServer();
                }
                break;
            case PreferenceManager.REMOTE_NOTIFICATION_ACTIVE:
                preferences.setRemoteNotificationActive(
                        ((SwitchPreference) findPreference(PreferenceManager.REMOTE_NOTIFICATION_ACTIVE)).isChecked());
                break;
            case PreferenceManager.REMOTE_PHONE_NUMBER:
                setPhoneNumber();
                if (getActivity() != null) Utils.hideKeyboard(getActivity());
                break;
            case PreferenceManager.NOTIFICATION_TIME:
                try {
                    String text = ((EditTextPreference) findPreference(PreferenceManager.NOTIFICATION_TIME)).getText();
                    int notificationTimeMs = Integer.parseInt(text) * 60000;
                    preferences.setNotificationTimeMs(notificationTimeMs);
                    findPreference(PreferenceManager.NOTIFICATION_TIME).setSummary(preferences.getNotificationTimeMs() / 60000 + " " + getString(R.string.minutes));
                } catch (NumberFormatException ignored) {
                }
                break;
            case PreferenceManager.REMOTE_ACCESS_ONION: {
                EditTextPreference preference = findPreference(PreferenceManager.REMOTE_ACCESS_ONION);
                assert preference != null;
                String text = preference.getText();
                if (checkValidString(text)) {
                    preferences.setRemoteAccessOnion(text.trim());
                    preference.setSummary(preferences.getRemoteAccessOnion().trim() + ":" + WebServer.LOCAL_PORT);
                } else {
                    preferences.setRemoteAccessOnion(text);
                    preference.setSummary(R.string.remote_access_hint);
                }
                break;
            }
            case PreferenceManager.REMOTE_ACCESS_CRED: {
                EditTextPreference preference = findPreference(PreferenceManager.REMOTE_ACCESS_CRED);
                assert preference != null;
                String text = preference.getText();
                if (checkValidString(text)) {
                    preferences.setRemoteAccessCredential(text.trim());
                    preference.setSummary(R.string.bullets);
                } else {
                    preferences.setRemoteAccessCredential(text);
                    preference.setSummary(R.string.remote_access_credential_hint);
                }
                break;
            }
            case PreferenceManager.CONFIG_BASE_STORAGE:
                setDefaultStoragePath();
                break;
            case "encrypt_database":
                android.widget.Toast.makeText(mActivity,
                        R.string.restart_to_apply, android.widget.Toast.LENGTH_LONG).show();
                break;
            case "backup_enabled":
            case "backup_url":
            case "backup_user":
            case "backup_password":
            case "backup_passphrase":
                org.havenapp.main.service.BackupWorker.reschedule(mActivity);
                break;
            case "deadman_hours_text": {
                EditTextPreference p = findPreference("deadman_hours_text");
                int h = 0;
                try { h = Integer.parseInt(p.getText().trim()); } catch (Exception ignored) {}
                preferences.setDeadmanHours(Math.max(0, h));
                org.havenapp.main.service.DeadmanWorker.reschedule(mActivity);
                break;
            }
            case "remote_commands_enabled": {
                SwitchPreference sw = findPreference("remote_commands_enabled");
                if (sw != null && sw.isChecked()) {
                    ActivityCompat.requestPermissions(mActivity, new String[]{
                            Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS}, 6);
                }
                break;
            }
            case "content_filter_enabled": {
                SwitchPreference sw = findPreference("content_filter_enabled");
                if (sw != null && sw.isChecked()) {
                    org.havenapp.main.net.ContentFilter.start(mActivity);
                } else {
                    org.havenapp.main.net.ContentFilter.stop(mActivity);
                }
                break;
            }
            case "filter_ads":
            case "filter_malware":
            case "filter_adult":
            case "filter_custom_domains":
                if (org.havenapp.main.net.ContentFilter.isRunning(mActivity)) {
                    org.havenapp.main.net.ContentFilter.stop(mActivity);
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            () -> org.havenapp.main.net.ContentFilter.start(mActivity), 400);
                }
                break;
            case "location_tracking_enabled": {
                SwitchPreference sw = findPreference("location_tracking_enabled");
                if (sw != null && sw.isChecked()
                        && !org.havenapp.main.location.LocationTracker.hasPermission(mActivity)) {
                    ActivityCompat.requestPermissions(mActivity, new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 5);
                }
                break;
            }
            case "usage_report_enabled": {
                SwitchPreference sw = findPreference("usage_report_enabled");
                org.havenapp.main.security.UsageReporter ur =
                        new org.havenapp.main.security.UsageReporter(mActivity);
                if (sw != null && sw.isChecked() && !ur.hasPermission()) {
                    android.widget.Toast.makeText(mActivity, R.string.usage_access_needed,
                            android.widget.Toast.LENGTH_LONG).show();
                    sw.setChecked(false);
                    try {
                        startActivity(ur.settingsIntent());
                    } catch (Exception ignored) {
                    }
                } else if (sw != null && sw.isChecked()) {
                    // instant first report; the coalesced HousekeepingWorker sends it daily after
                    org.havenapp.main.service.UsageReportWorker.runNow(mActivity);
                }
                break;
            }
        }
    }

    String getCountryCode() {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        return "+" + phoneUtil.getCountryCodeForRegion(Locale.getDefault().getCountry());
    }

    private void setDefaultStoragePath() {
        String defaultStoragePath = ((EditTextPreference) findPreference(PreferenceManager.CONFIG_BASE_STORAGE)).getText();
        preferences.setDefaultMediaStoragePath(defaultStoragePath);
    }

    private void setPhoneNumber() {
        String phoneNumber = ((EditTextPreference) findPreference(PreferenceManager.REMOTE_PHONE_NUMBER)).getText();
        if (checkValidString(phoneNumber) && !getCountryCode().equalsIgnoreCase(phoneNumber)) {
            preferences.setRemotePhoneNumber(phoneNumber.trim());
            findPreference(PreferenceManager.REMOTE_PHONE_NUMBER).setSummary(phoneNumber.trim());
        } else if (!getCountryCode().equalsIgnoreCase(phoneNumber)) {
            preferences.setRemotePhoneNumber("");
            findPreference(PreferenceManager.REMOTE_PHONE_NUMBER).setSummary(R.string.sms_dialog_message);
        }
    }

    private void showTimeDelayDialog(String configVideoLength) {
        int totalSecs;
        if (configVideoLength.equalsIgnoreCase(PreferenceManager.CONFIG_TIME_DELAY)) {
            totalSecs = preferences.getTimerDelay();
        } else {
            totalSecs = preferences.getMonitoringTime();
        }
        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;

        TimePickerDialog mTimePickerDialog = TimePickerDialog.newInstance(this, hours, minutes, seconds, true);
        mTimePickerDialog.enableSeconds(true);
        if (configVideoLength.equalsIgnoreCase(PreferenceManager.CONFIG_TIME_DELAY)) {
            mTimePickerDialog.show(getParentFragmentManager(), "TimeDelayPickerDialog");
        } else {
            mTimePickerDialog.show(getParentFragmentManager(), "VideoLengthPickerDialog");
        }
    }

    private boolean checkValidString(String a) {
        return a != null && !a.trim().isEmpty();
    }

    private boolean checkValidStrings(String a, String b) {
        return a != null && !a.trim().isEmpty() && b != null && !b.trim().isEmpty();
    }

    private void checkRemoteAccessOnion() {
        if (OrbotHelper.isOrbotInstalled(mActivity)) {
            OrbotHelper.requestStartTor(mActivity);
            if (preferences.getRemoteAccessOnion() != null && TextUtils.isEmpty(preferences.getRemoteAccessOnion().trim())) {
                OrbotHelper.requestHiddenServiceOnPort(mActivity, WebServer.LOCAL_PORT);
            }
        } else {
            android.widget.Toast.makeText(mActivity, R.string.remote_access_onion_error, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void askForPermission(String permission, Integer requestCode) {
        if (mActivity != null && ContextCompat.checkSelfPermission(mActivity, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mActivity, new String[]{permission}, requestCode);
        }
    }

    @Override
    public void onTimeSet(TimePickerDialog view, int hourOfDay, int minute, int second) {
        int seconds = second + minute * 60 + hourOfDay * 60 * 60;
        if (view.getTag().equalsIgnoreCase("TimeDelayPickerDialog")) {
            preferences.setTimerDelay(seconds);
        } else if (view.getTag().equalsIgnoreCase("VideoLengthPickerDialog")) {
            preferences.setMonitoringTime(seconds);
        }
    }

    private void saveCurrentPlace() {
        if (!org.havenapp.main.location.LocationTracker.hasPermission(mActivity)) {
            android.widget.Toast.makeText(mActivity, R.string.location_permission_needed,
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        android.widget.Toast.makeText(mActivity, "…", android.widget.Toast.LENGTH_SHORT).show();
        new org.havenapp.main.location.LocationTracker(mActivity).requestOneShot(loc -> {
            if (loc == null || mActivity == null) {
                if (mActivity != null) android.widget.Toast.makeText(mActivity,
                        R.string.location_unavailable, android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            android.widget.EditText input = new android.widget.EditText(mActivity);
            input.setHint(R.string.place_name_hint);
            new androidx.appcompat.app.AlertDialog.Builder(mActivity)
                    .setTitle(R.string.add_place_title)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) return;
                        new org.havenapp.main.location.GeofenceStore(mActivity).add(
                                new org.havenapp.main.location.GeofenceStore.Place(
                                        name, loc.getLatitude(), loc.getLongitude(), 150f));
                        android.widget.Toast.makeText(mActivity,
                                getString(R.string.place_saved, name),
                                android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void showDuressPinDialog() {
        android.content.Context ctx = getContext();
        if (ctx == null) return;
        android.widget.EditText input = new android.widget.EditText(ctx);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint(R.string.pin_dialog_new);
        org.havenapp.main.security.PinManager pm = new org.havenapp.main.security.PinManager(mActivity);
        new androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.duress_pin_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    pm.setDuressPin(input.getText().toString());
                    Preference p = findPreference("duress_pin_set");
                    if (p != null) p.setSummary(pm.hasDuressPin()
                            ? R.string.pin_set_summary_set : R.string.duress_pin_summary);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshAdminSummary(Preference p) {
        if (p == null) return;
        boolean active = new org.havenapp.main.security.AdminManager(mActivity).isActive();
        p.setSummary(active ? R.string.admin_summary_on : R.string.admin_summary_off);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        refreshAdminSummary(findPreference("admin_protect"));
    }

    private void showPinDialog() {
        android.content.Context ctx = getContext();
        if (ctx == null) return;
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout box = new android.widget.LinearLayout(ctx);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        android.widget.EditText p1 = new android.widget.EditText(ctx);
        p1.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        p1.setHint(R.string.pin_dialog_new);
        android.widget.EditText p2 = new android.widget.EditText(ctx);
        p2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        p2.setHint(R.string.pin_dialog_confirm);
        box.addView(p1);
        box.addView(p2);

        org.havenapp.main.security.PinManager pin = new org.havenapp.main.security.PinManager(mActivity);
        new androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.pin_set_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String a = p1.getText().toString();
                    String b = p2.getText().toString();
                    if (!a.equals(b)) {
                        android.widget.Toast.makeText(ctx, R.string.pin_mismatch, android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pin.setPin(a);
                    android.widget.Toast.makeText(ctx,
                            a.isEmpty() ? R.string.pin_cleared : R.string.pin_saved,
                            android.widget.Toast.LENGTH_SHORT).show();
                    Preference pref = findPreference("pin_set");
                    if (pref != null) {
                        pref.setSummary(pin.hasPin() ? R.string.pin_set_summary_set : R.string.pin_set_summary_none);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void requestChangeBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getActivity().getPackageName();
            PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            } else {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
            }
            getActivity().startActivity(intent);
        }
    }
}
