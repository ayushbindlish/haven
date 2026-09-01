package org.havenapp.main.security;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only, hash-chained record of every event and captured file, kept separate from
 * the (editable) Room database. Each line is
 * {@code <epochMs>|<type>|<path>|<fileSha256>|<chainSha256>} where the chain hash folds in
 * the previous line, so deleting or altering any earlier row breaks {@link #verify}.
 */
public final class EvidenceLog {

    private static final String FILE = "evidence.log";
    private static final String TAG = "EvidenceLog";

    /** Cache of the last line's chain hash so append() doesn't re-read the whole log. */
    private static String cachedChain = null;

    private EvidenceLog() {}

    private static File file(Context c) {
        return new File(c.getExternalFilesDir(null), FILE);
    }

    public static synchronized void append(Context context, int type, String path) {
        try {
            File f = file(context);
            String prevChain = cachedChain != null ? cachedChain : lastChain(f);
            String fileHash = "";
            if (path != null && new File(path).isFile()) {
                fileHash = sha256Plain(context, path);
            }
            // The record is one physical line with '|' field separators, so the free-text
            // path/value must not contain '|' or a line break or it corrupts the chain.
            String safePath = path == null ? "" : path.replaceAll("[\\r\\n|]+", " ").trim();
            String core = System.currentTimeMillis() + "|" + type + "|"
                    + safePath + "|" + fileHash;
            String chain = sha256(prevChain + core);
            try (FileWriter w = new FileWriter(f, true)) {
                w.append(core).append('|').append(chain).append('\n');
            }
            cachedChain = chain;
        } catch (Exception e) {
            cachedChain = null; // force a re-read next time
            Log.w(TAG, "could not append to evidence log", e);
        }
    }

    /** @return list of problems; empty means the chain is intact. */
    public static synchronized List<String> verify(Context context) {
        cachedChain = null; // re-read the tail after a verify
        List<String> problems = new ArrayList<>();
        File f = file(context);
        if (!f.exists()) {
            problems.add("No evidence log yet");
            return problems;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            String prevChain = "";
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                int lastPipe = line.lastIndexOf('|');
                if (lastPipe < 0) { problems.add("Line " + n + " malformed"); continue; }
                String core = line.substring(0, lastPipe);
                String stored = line.substring(lastPipe + 1);
                String expect = sha256(prevChain + core);
                if (!expect.equals(stored)) {
                    problems.add("Line " + n + ": chain broken (row edited or an earlier row removed)");
                }
                String[] parts = core.split("\\|", -1);
                if (parts.length >= 4 && !parts[2].isEmpty() && !parts[3].isEmpty()) {
                    File media = new File(parts[2]);
                    if (!media.exists()) {
                        problems.add("Line " + n + ": media missing (" + media.getName() + ")");
                    } else if (!sha256Plain(context, parts[2]).equals(parts[3])) {
                        problems.add("Line " + n + ": media altered (" + media.getName() + ")");
                    }
                }
                prevChain = stored;
            }
            if (n == 0) problems.add("Evidence log is empty");
        } catch (Exception e) {
            problems.add("Could not read log: " + e.getMessage());
        }
        return problems;
    }

    private static String lastChain(File f) throws Exception {
        if (!f.exists() || f.length() == 0) return "";
        // Seek from the end and read back to the previous newline instead of scanning
        // the whole file (which grows without bound on a long-running guard device).
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            long pos = len - 1;
            // skip trailing newlines
            while (pos >= 0) {
                raf.seek(pos);
                int c = raf.read();
                if (c != '\n' && c != '\r') break;
                pos--;
            }
            long end = pos + 1;
            while (pos >= 0) {
                raf.seek(pos);
                int c = raf.read();
                if (c == '\n' || c == '\r') break;
                pos--;
            }
            long start = pos + 1;
            byte[] buf = new byte[(int) (end - start)];
            raf.seek(start);
            raf.readFully(buf);
            String last = new String(buf, "UTF-8");
            int p = last.lastIndexOf('|');
            return p < 0 ? "" : last.substring(p + 1);
        }
    }

    private static String sha256(String s) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")));
    }

    /** SHA-256 of the plaintext content, whether the file on disk is encrypted or not. */
    private static String sha256Plain(Context context, String path) {
        try (InputStream in = MediaAccess.openPlain(context, path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return hex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16))
                .append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
