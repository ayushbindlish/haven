package org.havenapp.main.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validates the on-disk backup format {@code "HVBK1"|salt(16)|iv(12)|AES-256-GCM} and its
 * PBKDF2 parameters, so the off-device restore script in DEV_ADB_NOTES.md stays correct.
 * Pure JCE &mdash; no Android.
 */
public class BackupCryptoTest {

    private static final byte[] MAGIC = "HVBK1".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 210_000;

    private static byte[] encrypt(byte[] plain, String passphrase) throws Exception {
        Method m = BackupManager.class.getDeclaredMethod("encrypt", byte[].class, String.class);
        m.setAccessible(true);
        return (byte[]) m.invoke(null, plain, passphrase);
    }

    /** Mirror of the documented restore recipe. */
    private static byte[] decrypt(byte[] blob, String passphrase) throws Exception {
        byte[] salt = Arrays.copyOfRange(blob, 5, 21);
        byte[] iv = Arrays.copyOfRange(blob, 21, 33);
        byte[] ct = Arrays.copyOfRange(blob, 33, blob.length);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256))
                .getEncoded();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(ct);
    }

    @Test
    public void formatHasMagicSaltIvAndGcmTag() throws Exception {
        byte[] plain = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        byte[] blob = encrypt(plain, "correct horse battery staple");

        assertArrayEquals("magic prefix", MAGIC, Arrays.copyOfRange(blob, 0, 5));
        assertEquals("5 magic + 16 salt + 12 iv + ciphertext + 16 GCM tag",
                5 + 16 + 12 + plain.length + 16, blob.length);
    }

    @Test
    public void roundTripsWithTheDocumentedRestoreParameters() throws Exception {
        byte[] plain = new byte[4096];
        for (int i = 0; i < plain.length; i++) plain[i] = (byte) (i * 7);
        byte[] blob = encrypt(plain, "s3cr3t-pass");

        assertArrayEquals(plain, decrypt(blob, "s3cr3t-pass"));
    }

    @Test
    public void saltAndIvAreFreshEachTime() throws Exception {
        byte[] a = encrypt("x".getBytes(), "p");
        byte[] b = encrypt("x".getBytes(), "p");
        assertTrue("salt differs", !Arrays.equals(
                Arrays.copyOfRange(a, 5, 21), Arrays.copyOfRange(b, 5, 21)));
        assertTrue("iv differs", !Arrays.equals(
                Arrays.copyOfRange(a, 21, 33), Arrays.copyOfRange(b, 21, 33)));
    }

    @Test
    public void wrongPassphraseFailsAuthentication() throws Exception {
        byte[] blob = encrypt("secret data".getBytes(StandardCharsets.UTF_8), "right");
        try {
            decrypt(blob, "wrong");
            fail("GCM tag check should have rejected the wrong key");
        } catch (javax.crypto.AEADBadTagException expected) {
            // good
        }
    }
}
