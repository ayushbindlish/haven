package org.havenapp.main.pairing;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.service.SupervisorWorker;

import java.security.SecureRandom;

/**
 * Child device: enables supervised mode (auto-generating a private ntfy topic + command
 * secret if needed) and shows the pairing QR for a parent device to scan.
 */
public class SupervisedSetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        PreferenceManager p = new PreferenceManager(this);

        if (p.getSupervisedTopic().isEmpty()) p.setSupervisedTopic("haven-" + rand(14));
        if (p.getRemoteCommandSecret().isEmpty()) p.setRemoteCommandSecret(rand(10));
        p.setSupervisedEnabled(true);
        SupervisorWorker.reschedule(this);

        PairingPayload payload = new PairingPayload(
                android.os.Build.MODEL, p.getSupervisedServer(), p.getSupervisedTopic(),
                p.getRemoteCommandSecret(),
                p.getRemoteAccessOnion() == null ? "" : p.getRemoteAccessOnion(),
                p.getRemoteAccessCredential() == null ? "" : p.getRemoteAccessCredential());
        final String text = payload.encode();

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);

        TextView t = new TextView(this);
        t.setText(R.string.supervised_qr_hint);
        t.setGravity(Gravity.CENTER);

        ImageView img = new ImageView(this);
        int q = (int) (260 * getResources().getDisplayMetrics().density);
        Bitmap qr = QrCodec.encode(text, q);
        if (qr != null) img.setImageBitmap(qr);

        Button copy = new Button(this);
        copy.setText(R.string.supervised_copy);
        copy.setOnClickListener(v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("haven-pairing", text));
            Toast.makeText(this, R.string.supervised_copied, Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        root.addView(t);
        root.addView(img, new LinearLayout.LayoutParams(q, q) {{ topMargin = pad; }});
        root.addView(copy, lp);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        setTitle(R.string.supervised_qr_title);
    }

    private static String rand(int n) {
        String cs = "abcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(cs.charAt(r.nextInt(cs.length())));
        return sb.toString();
    }
}
