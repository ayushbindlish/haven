package org.havenapp.main.security;

import android.content.Context;

import org.havenapp.main.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * One indirection point for reading/writing capture media, so the rest of the app doesn't
 * care whether at-rest encryption is on. Encrypted files carry a {@code .enc} suffix;
 * for viewing they're transparently decrypted into {@code cacheDir/vault/} (app-private,
 * auto-evicted).
 */
public final class MediaAccess {

    private MediaAccess() {}

    public static boolean encryptionEnabled(Context c) {
        return new PreferenceManager(c).getEncryptMedia();
    }

    public static boolean isEncrypted(String path) {
        return path != null && path.endsWith(".enc");
    }

    /** Where a capture should be written: same name, plus {@code .enc} when encrypting. */
    public static File outputFile(Context c, File plainTarget) {
        return encryptionEnabled(c) ? new File(plainTarget.getPath() + ".enc") : plainTarget;
    }

    /** Persist {@code data} to {@code target} (which may already have the {@code .enc} suffix). */
    public static void write(Context c, byte[] data, File target) throws Exception {
        if (target.getName().endsWith(".enc")) {
            VaultCrypto.encryptFile(c, data, target);
        } else {
            try (OutputStream os = new FileOutputStream(target)) {
                os.write(data);
            }
        }
    }

    /** Plaintext stream regardless of on-disk form (alert uploads, hashing, cleanup). */
    public static InputStream openPlain(Context c, String path) throws Exception {
        File f = new File(path);
        return isEncrypted(path) ? VaultCrypto.decryptingStream(c, f) : new FileInputStream(f);
    }

    /**
     * A real filesystem path a media viewer can open. For encrypted media this decrypts
     * into the cache once and returns that; otherwise the original path.
     */
    public static String resolveForViewing(Context c, String path) {
        if (!isEncrypted(path)) return path;
        try {
            File src = new File(path);
            String base = src.getName();
            base = base.substring(0, base.length() - 4); // strip .enc
            File dir = new File(c.getCacheDir(), "vault");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, Integer.toHexString(path.hashCode()) + "_" + base);
            if (!out.exists() || out.length() == 0) {
                VaultCrypto.decryptToFile(c, src, out);
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            return path; // fall back; viewer will just fail gracefully
        }
    }

    public static byte[] readAll(InputStream in) throws Exception {
        try (InputStream i = in) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = i.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    /** Best-effort wipe of the decrypted cache (call on lock / background). */
    public static void clearViewCache(Context c) {
        File dir = new File(c.getCacheDir(), "vault");
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
