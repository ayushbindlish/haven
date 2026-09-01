package org.havenapp.main.alerts;

import android.text.TextUtils;

import org.havenapp.main.net.TorController;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Minimal HTTP helper for the network alert channels (Telegram, ntfy). Uses only
 * {@link HttpURLConnection}; when {@code tor} is set, tunnels TCP through a local SOCKS
 * proxy — Orbot's (127.0.0.1:9050) or the app's own embedded Tor, whichever is up (see
 * {@link TorController#socksAddress()}).
 */
public final class HttpPoster {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    // Tor needs much longer: the first circuit after a cold bootstrap can take 30-60 s,
    // and every request carries onion-routing latency.
    private static final int TOR_CONNECT_TIMEOUT_MS = 60000;
    private static final int TOR_READ_TIMEOUT_MS = 60000;

    private static Proxy torProxy() {
        return new Proxy(Proxy.Type.SOCKS, TorController.socksAddress());
    }

    private HttpPoster() {}

    static class HttpException extends Exception {
        HttpException(int code, String body) {
            super("HTTP " + code + (TextUtils.isEmpty(body) ? "" : ": " + trim(body)));
        }
        private static String trim(String s) { return s.length() > 300 ? s.substring(0, 300) : s; }
    }

    private static HttpURLConnection open(String urlStr, boolean tor) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) (tor ? url.openConnection(torProxy()) : url.openConnection());
        c.setConnectTimeout(tor ? TOR_CONNECT_TIMEOUT_MS : CONNECT_TIMEOUT_MS);
        c.setReadTimeout(tor ? TOR_READ_TIMEOUT_MS : READ_TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    /** application/x-www-form-urlencoded POST. */
    static String postForm(String urlStr, Map<String, String> form, boolean tor) throws Exception {
        HttpURLConnection c = open(urlStr, tor);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : form.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                        .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
            }
            try (OutputStream os = c.getOutputStream()) {
                os.write(sb.toString().getBytes("UTF-8"));
            }
            return finish(c);
        } finally {
            c.disconnect();
        }
    }

    /** multipart/form-data POST with one file part. */
    static String postMultipart(String urlStr, Map<String, String> fields,
                                String fileField, File file, String fileMime, boolean tor) throws Exception {
        String boundary = "----HavenBoundary" + System.currentTimeMillis();
        String CRLF = "\r\n";
        HttpURLConnection c = open(urlStr, tor);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
                for (Map.Entry<String, String> e : fields.entrySet()) {
                    out.writeBytes("--" + boundary + CRLF);
                    out.writeBytes("Content-Disposition: form-data; name=\"" + e.getKey() + "\"" + CRLF + CRLF);
                    out.write((e.getValue() == null ? "" : e.getValue()).getBytes("UTF-8"));
                    out.writeBytes(CRLF);
                }
                if (file != null && file.exists()) {
                    out.writeBytes("--" + boundary + CRLF);
                    out.writeBytes("Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                            + file.getName() + "\"" + CRLF);
                    out.writeBytes("Content-Type: " + fileMime + CRLF + CRLF);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = fis.read(buf)) != -1) out.write(buf, 0, n);
                    }
                    out.writeBytes(CRLF);
                }
                out.writeBytes("--" + boundary + "--" + CRLF);
            }
            return finish(c);
        } finally {
            c.disconnect();
        }
    }

    /** Simple GET. */
    public static String get(String urlStr, boolean tor) throws Exception {
        HttpURLConnection c = open(urlStr, tor);
        try {
            c.setRequestMethod("GET");
            return finish(c);
        } finally {
            c.disconnect();
        }
    }

    /** Raw PUT of bytes (ntfy). Headers must be ASCII-safe. */
    public static String put(String urlStr, Map<String, String> headers, byte[] body, boolean tor) throws Exception {
        HttpURLConnection c = open(urlStr, tor);
        try {
            c.setRequestMethod("PUT");
            c.setDoOutput(true);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    c.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            try (OutputStream os = c.getOutputStream()) {
                if (body != null) os.write(body);
            }
            return finish(c);
        } finally {
            c.disconnect();
        }
    }

    static byte[] readFile(File f) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static String finish(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String body = is == null ? "" : new String(HttpPoster.readAll(is), "UTF-8");
        if (code < 200 || code >= 300) throw new HttpException(code, body);
        return body;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
