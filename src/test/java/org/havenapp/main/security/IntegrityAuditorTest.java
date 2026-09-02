package org.havenapp.main.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.havenapp.main.TestPrefs;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class IntegrityAuditorTest {

    private Context ctx;
    private IntegrityAuditor auditor;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        TestPrefs.of(ctx).edit().remove("integrity_baseline").commit();
        auditor = new IntegrityAuditor(ctx);
    }

    private static boolean has(List<String> changes, String needle) {
        for (String c : changes) if (c.contains(needle)) return true;
        return false;
    }

    @Test
    public void firstAuditRecordsTheBaselineAndReportsNothing() {
        assertFalse(auditor.hasBaseline());
        assertEquals(0, auditor.auditAgainstBaseline().size());
        assertTrue(auditor.hasBaseline());
    }

    @Test
    public void anUnchangedDeviceProducesNoFindings() {
        auditor.resetBaseline();
        assertEquals(0, auditor.auditAgainstBaseline().size());
    }

    @Test
    public void aChangedScalarIsReported() throws Exception {
        auditor.resetBaseline();

        // rewrite the stored baseline so it disagrees with the live snapshot
        SharedPreferences sp = TestPrefs.of(ctx);
        JSONObject baseline = new JSONObject(sp.getString("integrity_baseline", "{}"));
        int live = baseline.optInt("adb_enabled", 0);
        baseline.put("adb_enabled", live == 1 ? 0 : 1);
        sp.edit().putString("integrity_baseline", baseline.toString()).commit();

        List<String> changes = auditor.auditAgainstBaseline();
        assertTrue("the adb-debugging toggle change is surfaced", has(changes, "USB debugging"));
    }

    @Test
    public void aNewInstalledPackageIsReported() throws Exception {
        auditor.resetBaseline();

        SharedPreferences sp = TestPrefs.of(ctx);
        JSONObject baseline = new JSONObject(sp.getString("integrity_baseline", "{}"));
        // drop one package from the baseline set so the live snapshot looks like it gained one
        org.json.JSONArray pkgs = baseline.optJSONArray("installed_packages");
        if (pkgs != null && pkgs.length() > 0) {
            org.json.JSONArray trimmed = new org.json.JSONArray();
            for (int i = 1; i < pkgs.length(); i++) trimmed.put(pkgs.get(i));
            baseline.put("installed_packages", trimmed);
            sp.edit().putString("integrity_baseline", baseline.toString()).commit();

            assertTrue(has(auditor.auditAgainstBaseline(), "App"));
        }
    }
}
