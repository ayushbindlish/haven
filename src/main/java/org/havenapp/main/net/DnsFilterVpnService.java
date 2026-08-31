package org.havenapp.main.net;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * DNS-only content filter. The VPN captures ONLY traffic to a private DNS address
 * (one /32 route), so every other packet flows over the real network untouched - no
 * userspace TCP/IP, negligible battery cost. Blocked names get an NXDOMAIN; everything
 * else is forwarded to an upstream resolver over a protected socket.
 *
 * If anything goes wrong the query is forwarded unmodified, so browsing never breaks.
 */
public class DnsFilterVpnService extends VpnService {

    private static final String TAG = "DnsFilterVpn";
    private static final String VPN_ADDR = "10.111.222.1";
    private static final String DNS_ADDR = "10.111.222.2";
    private static final String UPSTREAM = "9.9.9.9"; // Quad9
    private static final String CHANNEL = "haven_filter";

    public static final String ACTION_STOP = "org.havenapp.main.STOP_FILTER";

    private ParcelFileDescriptor tun;
    private Thread worker;
    private volatile boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNotice();
        startTunnel();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        closeQuietly();
        super.onDestroy();
    }

    private void startTunnel() {
        if (running) return;
        try {
            Builder b = new Builder()
                    .setSession("Haven filter")
                    .addAddress(VPN_ADDR, 32)
                    .addDnsServer(DNS_ADDR)
                    .addRoute(DNS_ADDR, 32);   // <-- only DNS to our address is captured
            try {
                b.addDisallowedApplication(getPackageName());
            } catch (Exception ignored) {
            }
            tun = b.establish();
            if (tun == null) {
                stopSelf();
                return;
            }
            running = true;
            worker = new Thread(this::loop, "haven-dns-filter");
            worker.start();
            Log.i(TAG, "DNS filter tunnel up");
        } catch (Exception e) {
            Log.e(TAG, "establish failed", e);
            stopSelf();
        }
    }

    private void loop() {
        DnsBlocklist blocklist = new DnsBlocklist(this);
        PreferenceManager prefs = new PreferenceManager(this);
        try (FileInputStream in = new FileInputStream(tun.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(tun.getFileDescriptor());
             DatagramSocket upstream = new DatagramSocket()) {

            protect(upstream);
            upstream.setSoTimeout(5000);
            byte[] buf = new byte[32767];

            while (running) {
                int n = in.read(buf);
                if (n <= 0) continue;
                try {
                    handlePacket(buf, n, out, upstream, blocklist, prefs);
                } catch (Exception perPacket) {
                    Log.w(TAG, "packet error", perPacket);
                }
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "loop died", e);
        }
    }

    /** Parse an IPv4/UDP/DNS packet from the tun, filter, and write a reply back. */
    private void handlePacket(byte[] pkt, int len, FileOutputStream out,
                              DatagramSocket upstream, DnsBlocklist blocklist,
                              PreferenceManager prefs) throws Exception {
        if (len < 28) return;
        int ipVer = (pkt[0] & 0xF0) >> 4;
        if (ipVer != 4) return;
        int ihl = (pkt[0] & 0x0F) * 4;
        int proto = pkt[9] & 0xFF;
        if (proto != 17) return; // UDP only

        int udpStart = ihl;
        int dstPort = ((pkt[udpStart + 2] & 0xFF) << 8) | (pkt[udpStart + 3] & 0xFF);
        if (dstPort != 53) return;

        int dnsStart = udpStart + 8;
        int dnsLen = len - dnsStart;
        if (dnsLen < 12) return;
        byte[] dns = new byte[dnsLen];
        System.arraycopy(pkt, dnsStart, dns, 0, dnsLen);

        String qname = parseQName(dns);
        byte[] response;
        if (qname != null && blocklist.isBlocked(qname)) {
            response = nxdomain(dns);
            if (prefs.getFilterLogEnabled()) Log.i(TAG, "blocked " + qname);
        } else {
            response = forward(dns, upstream);
            if (response == null) return; // upstream failed -> drop, client retries
        }

        byte[] reply = wrapReply(pkt, ihl, response);
        out.write(reply);
    }

    private byte[] forward(byte[] dns, DatagramSocket sock) {
        try {
            InetAddress up = InetAddress.getByName(UPSTREAM);
            sock.send(new DatagramPacket(dns, dns.length, up, 53));
            byte[] r = new byte[1500];
            DatagramPacket dp = new DatagramPacket(r, r.length);
            sock.receive(dp);
            byte[] o = new byte[dp.getLength()];
            System.arraycopy(r, 0, o, 0, dp.getLength());
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    /** Build an NXDOMAIN answer for a query. */
    private static byte[] nxdomain(byte[] q) {
        byte[] r = q.clone();
        r[2] = (byte) 0x81;                 // QR=1, Opcode=0, RD copied
        r[3] = (byte) 0x83;                 // RA=1, RCODE=3 (NXDOMAIN)
        r[6] = 0; r[7] = 0;                 // ANCOUNT 0
        r[8] = 0; r[9] = 0;                 // NSCOUNT 0
        r[10] = 0; r[11] = 0;               // ARCOUNT 0
        return r;
    }

    /** Swap IP src/dst, swap UDP ports, drop in the new payload, fix lengths + checksums. */
    private static byte[] wrapReply(byte[] req, int ihl, byte[] dnsPayload) {
        int udp = ihl;
        int total = ihl + 8 + dnsPayload.length;
        byte[] p = new byte[total];
        System.arraycopy(req, 0, p, 0, ihl + 8);
        System.arraycopy(dnsPayload, 0, p, ihl + 8, dnsPayload.length);

        // IP total length
        p[2] = (byte) (total >> 8);
        p[3] = (byte) total;
        // swap IP addresses (src<->dst)
        for (int i = 0; i < 4; i++) {
            byte t = p[12 + i];
            p[12 + i] = p[16 + i];
            p[16 + i] = t;
        }
        // IP header checksum
        p[10] = 0; p[11] = 0;
        int sum = checksum(p, 0, ihl);
        p[10] = (byte) (sum >> 8);
        p[11] = (byte) sum;

        // swap UDP ports, set length, zero checksum (optional for IPv4)
        byte a = p[udp], b = p[udp + 1];
        p[udp] = p[udp + 2]; p[udp + 1] = p[udp + 3];
        p[udp + 2] = a; p[udp + 3] = b;
        int udpLen = 8 + dnsPayload.length;
        p[udp + 4] = (byte) (udpLen >> 8);
        p[udp + 5] = (byte) udpLen;
        p[udp + 6] = 0; p[udp + 7] = 0;
        return p;
    }

    private static int checksum(byte[] d, int off, int len) {
        long s = 0;
        for (int i = 0; i < len; i += 2) {
            int hi = d[off + i] & 0xFF;
            int lo = (i + 1 < len) ? d[off + i + 1] & 0xFF : 0;
            s += (hi << 8) | lo;
        }
        while ((s >> 16) != 0) s = (s & 0xFFFF) + (s >> 16);
        return (int) (~s & 0xFFFF);
    }

    /** Pull the first question's name out of a DNS message. */
    private static String parseQName(byte[] dns) {
        try {
            int i = 12;
            StringBuilder sb = new StringBuilder();
            while (i < dns.length) {
                int l = dns[i++] & 0xFF;
                if (l == 0) break;
                if ((l & 0xC0) == 0xC0) return null; // compression in a question -> bail
                if (sb.length() > 0) sb.append('.');
                for (int k = 0; k < l && i < dns.length; k++) sb.append((char) (dns[i++] & 0xFF));
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void startForegroundNotice() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN));
        }
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_haven)
                .setContentTitle(getString(R.string.content_filter_running))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build();
        startForeground(7, n);
    }

    private void closeQuietly() {
        try {
            if (tun != null) tun.close();
        } catch (Exception ignored) {
        }
        tun = null;
    }
}
