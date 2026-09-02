package org.havenapp.main.net;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.havenapp.main.TestPrefs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DnsBlocklistTest {

    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        TestPrefs.put(ctx, "filter_ads", false);
        TestPrefs.put(ctx, "filter_malware", false);
        TestPrefs.put(ctx, "filter_adult", false);
        TestPrefs.put(ctx, "filter_custom_domains", "");
    }

    @Test
    public void adsCategoryBlocksSeedDomainsAndSubdomains() {
        TestPrefs.put(ctx, "filter_ads", true);
        DnsBlocklist bl = new DnsBlocklist(ctx);

        assertTrue(bl.isBlocked("doubleclick.net"));
        assertTrue("subdomain of a blocked suffix", bl.isBlocked("ad.doubleclick.net"));
        assertTrue("trailing dot is stripped", bl.isBlocked("doubleclick.net."));
        assertTrue("case-insensitive", bl.isBlocked("DoubleClick.NET"));
        assertFalse(bl.isBlocked("example.com"));
        assertFalse("substring, not a suffix match", bl.isBlocked("notdoubleclick.net.evil.test"));
    }

    @Test
    public void categoryOffMeansNothingBlocked() {
        DnsBlocklist bl = new DnsBlocklist(ctx);
        assertTrue(bl.isEmpty());
        assertFalse(bl.isBlocked("doubleclick.net"));
        assertFalse(bl.isBlocked("pornhub.com"));
    }

    @Test
    public void customDomainsParsedFromWhitespaceOrCommaList() {
        TestPrefs.put(ctx, "filter_custom_domains",
                "tracker.test, spy.example\n#a-comment-line\nBAD.Example.ORG");
        DnsBlocklist bl = new DnsBlocklist(ctx);

        assertTrue(bl.isBlocked("tracker.test"));
        assertTrue(bl.isBlocked("sub.spy.example"));
        assertTrue("stored lower-cased", bl.isBlocked("bad.example.org"));
        assertFalse("comment line ignored", bl.isBlocked("a-comment-line"));
        assertFalse(bl.isBlocked("unrelated.test"));
    }

    @Test
    public void nullHostIsNeverBlocked() {
        TestPrefs.put(ctx, "filter_ads", true);
        assertFalse(new DnsBlocklist(ctx).isBlocked(null));
    }
}
