package org.havenapp.main.ui;

import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.havenapp.main.R;
import org.havenapp.main.security.PinManager;

/**
 * Full-screen PIN / biometric gate. Launched for-result by {@code ListActivity} on
 * launch (if "require PIN to open" is set) and by {@code MonitorActivity} before
 * disarming (if "require PIN to stop" is set). RESULT_OK means unlocked.
 */
public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_REASON = "reason"; // "launch" | "stop"

    private PinManager pin;
    private TextView error;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        pin = new PinManager(this);

        if (!pin.hasPin()) {
            unlock();
            return;
        }

        setContentView(buildUi());
        maybePromptBiometric();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));

        TextView title = new TextView(this);
        title.setText(R.string.pin_enter_title);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFFFFFFFF);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setGravity(Gravity.CENTER);
        input.setTextColor(0xFFFFFFFF);
        input.setHint(R.string.pin_hint);

        error = new TextView(this);
        error.setTextColor(0xFFFF5252);
        error.setGravity(Gravity.CENTER);

        Button unlock = new Button(this);
        unlock.setText(R.string.pin_unlock);
        unlock.setOnClickListener(v -> {
            String entered = input.getText().toString();
            if (pin.verify(entered)) {
                unlock();
            } else if (pin.isDuressPin(entered)) {
                org.havenapp.main.security.DuressAction.fire(this);
                unlock(); // indistinguishable from a normal unlock
            } else {
                input.setText("");
                int fails = pin.failedCount();
                error.setText(getString(R.string.pin_wrong, fails));
                if (fails >= 5) {
                    new org.havenapp.main.security.AdminManager(this).lockNow();
                }
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        root.addView(title);
        root.addView(input, lp);
        root.addView(error, lp);
        root.addView(unlock, lp);
        return root;
    }

    private void maybePromptBiometric() {
        BiometricManager bm = BiometricManager.from(this);
        int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) return;

        BiometricPrompt prompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        unlock();
                    }
                });
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.pin_enter_title))
                .setNegativeButtonText(getString(R.string.pin_use_pin))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build());
    }

    private void unlock() {
        PinManager.unlockedThisProcess = true;
        new org.havenapp.main.PreferenceManager(this).markCheckin(); // dead-man's switch check-in
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Can't back out of the lock; drop to the launcher instead.
        moveTaskToBack(true);
    }
}
