package org.havenapp.main.security;

import android.content.Context;
import android.util.Log;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.integration.android.AndroidKeysetManager;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Central place for at-rest encryption. Two Tink keysets, each wrapped by a
 * non-exportable Android Keystore master key:
 *   - {@link #streamingAead()} — AES-256-GCM-HKDF streaming, for media files and backup
 *     archives (encrypt/decrypt without holding the whole file in memory).
 *   - {@link #aead()} — AES-256-GCM, for small blobs.
 */
public final class VaultCrypto {

    private static final String TAG = "VaultCrypto";
    private static final String PREF_FILE = "haven_tink_keyset";
    private static final String MASTER_KEY_URI = "android-keystore://haven_master_key";
    private static final String STREAM_KEYSET = "media_stream_keyset";
    private static final String AEAD_KEYSET = "blob_aead_keyset";

    private static volatile StreamingAead streaming;
    private static volatile Aead aead;
    private static boolean configDone;

    private VaultCrypto() {}

    private static synchronized void ensureConfig() {
        if (!configDone) {
            try {
                StreamingAeadConfig.register();
                AeadConfig.register();
            } catch (Exception e) {
                Log.e(TAG, "tink config", e);
            }
            configDone = true;
        }
    }

    public static StreamingAead streamingAead(Context context) {
        if (streaming == null) {
            synchronized (VaultCrypto.class) {
                if (streaming == null) {
                    ensureConfig();
                    try {
                        streaming = new AndroidKeysetManager.Builder()
                                .withSharedPref(context.getApplicationContext(), STREAM_KEYSET, PREF_FILE)
                                .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
                                .withMasterKeyUri(MASTER_KEY_URI)
                                .build()
                                .getKeysetHandle()
                                .getPrimitive(StreamingAead.class);
                    } catch (Exception e) {
                        Log.e(TAG, "streamingAead init failed", e);
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return streaming;
    }

    public static Aead aead(Context context) {
        if (aead == null) {
            synchronized (VaultCrypto.class) {
                if (aead == null) {
                    ensureConfig();
                    try {
                        aead = new AndroidKeysetManager.Builder()
                                .withSharedPref(context.getApplicationContext(), AEAD_KEYSET, PREF_FILE)
                                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                                .withMasterKeyUri(MASTER_KEY_URI)
                                .build()
                                .getKeysetHandle()
                                .getPrimitive(Aead.class);
                    } catch (Exception e) {
                        Log.e(TAG, "aead init failed", e);
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return aead;
    }

    /* ------------------------------------------------------------------- file I/O */

    public static void encryptFile(Context ctx, byte[] plaintext, File dest) throws Exception {
        try (OutputStream os = streamingAead(ctx).newEncryptingStream(
                new FileOutputStream(dest), assoc(dest))) {
            os.write(plaintext);
        }
    }

    public static OutputStream encryptingStream(Context ctx, File dest) throws Exception {
        return streamingAead(ctx).newEncryptingStream(new FileOutputStream(dest), assoc(dest));
    }

    public static InputStream decryptingStream(Context ctx, File src) throws Exception {
        return streamingAead(ctx).newDecryptingStream(new FileInputStream(src), assoc(src));
    }

    /** Decrypt {@code src} into {@code dest}; overwrites. */
    public static void decryptToFile(Context ctx, File src, File dest) throws Exception {
        try (InputStream in = decryptingStream(ctx, src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    /** Associated data binds a ciphertext to its filename (best-effort). */
    private static byte[] assoc(File f) {
        String name = f.getName();
        if (name.endsWith(".enc")) name = name.substring(0, name.length() - 4);
        return name.getBytes();
    }
}
