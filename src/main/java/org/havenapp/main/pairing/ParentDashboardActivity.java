package org.havenapp.main.pairing;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.alerts.HttpPoster;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parent device: pick a paired child, see its recent heartbeat / events / location, and
 * send it commands over the shared ntfy topic.
 */
public class ParentDashboardActivity extends AppCompatActivity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private PairedStore store;
    private PairedStore.Device current;
    private TextView feed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(R.string.parent_dashboard_title);
        store = new PairedStore(this);
        List<PairedStore.Device> devices = store.all();

        if (devices.isEmpty()) {
            startActivity(new Intent(this, ParentScanActivity.class));
            finish();
            return;
        }
        current = devices.get(0);
        render();
        refresh();
    }

    private void render() {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView who = new TextView(this);
        who.setText(getString(R.string.parent_watching, current.name));
        who.setTextSize(18);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.addView(btn(R.string.parent_refresh, v -> refresh()));
        btns.addView(btn(R.string.parent_status, v -> send("STATUS")));
        btns.addView(btn(R.string.parent_locate, v -> send("LOCATE")));
        btns.addView(btn(R.string.parent_photo, v -> send("PHOTO")));

        feed = new TextView(this);
        feed.setMovementMethod(new ScrollingMovementMethod());
        feed.setText("…");
        feed.setPadding(0, pad, 0, 0);

        Button unpair = btn(R.string.parent_unpair, v -> {
            store.removeByTopic(current.topic);
            Toast.makeText(this, R.string.parent_unpaired, Toast.LENGTH_SHORT).show();
            finish();
        });

        ScrollView sv = new ScrollView(this);
        sv.addView(feed);
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);

        root.addView(who);
        root.addView(btns);
        root.addView(sv, grow);
        root.addView(unpair);
        setContentView(root);
    }

    private Button btn(int label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(lp);
        return b;
    }

    private String topicUrl() {
        return current.server.replaceAll("/+$", "") + "/" + current.topic;
    }

    private void send(String cmd) {
        Toast.makeText(this, getString(R.string.parent_sent, cmd), Toast.LENGTH_SHORT).show();
        boolean tor = new PreferenceManager(this).getAlertsViaTor();
        new Thread(() -> {
            try {
                Map<String, String> h = new HashMap<>();
                h.put("Title", "haven-cmd");
                HttpPoster.put(topicUrl(), h,
                        ("CMD\t" + current.secret + " " + cmd).getBytes("UTF-8"), tor);
            } catch (Exception ignored) {
            }
            ui.postDelayed(this::refresh, 4000);
        }).start();
    }

    private void refresh() {
        boolean tor = new PreferenceManager(this).getAlertsViaTor();
        long since = System.currentTimeMillis() / 1000 - 6 * 3600;
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                String body = HttpPoster.get(topicUrl() + "/json?poll=1&since=" + since, tor);
                for (String line : body.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    JSONObject o = new JSONObject(line);
                    String m = o.optString("message", "");
                    long t = o.optLong("time", 0) * 1000;
                    String when = t > 0 ? android.text.format.DateFormat.format("MM-dd HH:mm", t).toString() : "";
                    if (m.startsWith("HB\t")) sb.append(when).append("  ♥ ").append(hb(m.substring(3))).append('\n');
                    else if (m.startsWith("EV\t")) sb.append(when).append("  ⚠ ").append(m.substring(3)).append('\n');
                    else if (m.startsWith("LOC\t")) sb.append(when).append("  📍 ").append(m.substring(4)).append('\n');
                    else if (m.startsWith("RESP\t")) sb.append(when).append("  ↩ ").append(m.substring(5)).append('\n');
                }
            } catch (Exception e) {
                sb.append(getString(R.string.parent_fetch_failed)).append('\n');
            }
            final String out = sb.length() == 0 ? getString(R.string.parent_nothing) : sb.toString();
            ui.post(() -> feed.setText(out));
        }).start();
    }

    private String hb(String json) {
        try {
            JSONObject o = new JSONObject(json);
            return o.optString("name") + "  batt " + o.optInt("batt") + "%"
                    + (o.optBoolean("charging") ? " (chg)" : "")
                    + "  " + (o.optBoolean("armed") ? "ARMED" : "off");
        } catch (Exception e) {
            return json;
        }
    }
}
