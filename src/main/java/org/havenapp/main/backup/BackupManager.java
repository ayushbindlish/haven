package org.havenapp.main.backup;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.alerts.HttpPoster;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Bundles the event DB + evidence log + captured media into a ZIP, encrypts it with a
 * key derived from a user backup passphrase (so it can be decrypted off-device with a
 * short script - see DEV_ADB_NOTES.md), and PUTs it to a WebDAV endpoint (Nextcloud,
 * ownCloud, any WebDAV share).
 *
 * File format:  "HVBK1"(5) | salt(16) | iv(12) | AES-256-GCM(ciphertext+tag)
 * KDF: PBKDF2WithHmacSHA256, 210000 iterations, 256-bit key.
 */
public final class BackupManager {

    private static final String TAG = "BackupManager";
    private static final byte[] MAGIC = {'H', 'V', 'B', 'K', '1'};
    private static final int ITERATIONS = 210_000;

    private BackupManager() {}

    public static boolean configured(Context c) {
        PreferenceManager p = new PreferenceManager(c);
        return !isEmpty(p.getBackupUrl()) && !isEmpty(p.getBackupPassphrase());
    }

    /** @return null on success, otherwise an error message. */
    public static String runBackup(Context context) {
        PreferenceManager p = new PreferenceManager(context);
        if (!configured(context)) return "Backup not configured";
        try {
            byte[] zip = buildZip(context);
            byte[] enc = encrypt(zip, p.getBackupPassphrase());

            String base = p.getBackupUrl().replaceAll("/+$", "");
            String name = "haven-backup-" + android.os.Build.MODEL.replace(' ', '-') + "-"
                    + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                        .format(new java.util.Date()) + ".hvbk";
            Map<String, String> headers = new HashMap<>();
            if (!isEmpty(p.getBackupUser())) {
                String cred = p.getBackupUser() + ":" + p.getBackupPassword();
                headers.put("Authorization", "Basic "
                        + Base64.encodeToString(cred.getBytes("UTF-8"), Base64.NO_WRAP));
            }
            headers.put("Content-Type", "application/octet-stream");
            HttpPoster.put(base + "/" + name, headers, enc, false);
            new PreferenceManager(context).setLastBackup(System.currentTimeMillis());
            Log.i(TAG, "backup uploaded: " + name + " (" + enc.length + " bytes)");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "backup failed", e);
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    /* ------------------------------------------------------------------ archive */

    private static byte[] buildZip(Context ctx) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            File db = ctx.getDatabasePath("haven.db");
            if (db.exists()) addFile(zos, db, "haven.db");
            File ev = new File(ctx.getExternalFilesDir(null), "evidence.log");
            if (ev.exists()) addFile(zos, ev, "evidence.log");
            File media = new File(ctx.getExternalFilesDir(null),
                    new PreferenceManager(ctx).getBaseStoragePath());
            if (media.isDirectory()) addTree(zos, media, "media");
        }
        return bos.toByteArray();
    }

    private static void addTree(ZipOutputStream zos, File dir, String prefix) throws Exception {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String entry = prefix + "/" + f.getName();
            if (f.isDirectory()) addTree(zos, f, entry);
            else if (!f.getName().equals(".nomedia")) addFile(zos, f, entry);
        }
    }

    private static void addFile(ZipOutputStream zos, File f, String entry) throws Exception {
        zos.putNextEntry(new ZipEntry(entry));
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) zos.write(buf, 0, n);
        }
        zos.closeEntry();
    }

    /* --------------------------------------------------------------- encryption */

    private static byte[] encrypt(byte[] plain, String passphrase) throws Exception {
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);

        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC);
        out.write(salt);
        out.write(iv);
        out.write(ct);
        return out.toByteArray();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
