package org.havenapp.main.net;

import android.content.Context;
import android.text.TextUtils;

import org.havenapp.main.PreferenceManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Domain-suffix blocklist assembled from the enabled categories plus the user's custom
 * list. Compact built-in seeds; advanced users add their own domains (or a hosts-file
 * import can be layered on later).
 */
public final class DnsBlocklist {

    // A small, high-signal starter set. Not exhaustive - the custom list + a future
    // downloadable list fill the gaps.
    private static final String[] ADS_TRACKERS = {
            "doubleclick.net", "googlesyndication.com", "google-analytics.com",
            "googletagmanager.com", "googleadservices.com", "adservice.google.com",
            "graph.facebook.com", "connect.facebook.net", "an.facebook.com",
            "app-measurement.com", "firebase-settings.crashlytics.com", "crashlytics.com",
            "adnxs.com", "rubiconproject.com", "pubmatic.com", "criteo.com", "criteo.net",
            "scorecardresearch.com", "quantserve.com", "amplitude.com", "branch.io",
            "appsflyer.com", "adjust.com", "kochava.com", "mixpanel.com", "segment.io",
            "unity3d.com", "unityads.unity3d.com", "applovin.com", "adcolony.com",
            "chartboost.com", "vungle.com", "inmobi.com", "mopub.com", "flurry.com",
            "moatads.com", "outbrain.com", "taboola.com", "bidswitch.net", "casalemedia.com"
    };
    private static final String[] MALWARE = {
            "malware.wicar.org", "testsafebrowsing.appspot.com"
            // intentionally tiny built-in; real coverage comes from a downloadable list
    };
    private static final String[] ADULT = {
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
            "youporn.com", "onlyfans.com", "chaturbate.com", "stripchat.com", "spankbang.com"
    };

    private final Set<String> suffixes = new HashSet<>();

    public DnsBlocklist(Context context) {
        PreferenceManager p = new PreferenceManager(context);
        if (p.getFilterAds()) add(ADS_TRACKERS);
        if (p.getFilterMalware()) add(MALWARE);
        if (p.getFilterAdult()) add(ADULT);
        String custom = p.getFilterCustomDomains();
        if (!TextUtils.isEmpty(custom)) {
            for (String line : custom.split("[\\s,]+")) {
                String d = line.trim().toLowerCase(Locale.US);
                if (!d.isEmpty() && !d.startsWith("#")) suffixes.add(d);
            }
        }
    }

    private void add(String[] arr) {
        for (String s : arr) suffixes.add(s);
    }

    public boolean isEmpty() {
        return suffixes.isEmpty();
    }

    /** True if {@code host} is, or is a subdomain of, any blocked suffix. */
    public boolean isBlocked(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (suffixes.contains(host)) return true;
        int dot = host.indexOf('.');
        while (dot != -1) {
            String parent = host.substring(dot + 1);
            if (suffixes.contains(parent)) return true;
            dot = host.indexOf('.', dot + 1);
        }
        return false;
    }
}
