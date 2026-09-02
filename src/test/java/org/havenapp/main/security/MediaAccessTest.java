package org.havenapp.main.security;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.havenapp.main.TestPrefs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class MediaAccessTest {

    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void isEncryptedTracksTheSuffix() {
        assertTrue(MediaAccess.isEncrypted("/x/y/shot.jpg.enc"));
        assertFalse(MediaAccess.isEncrypted("/x/y/shot.jpg"));
        assertFalse(MediaAccess.isEncrypted(null));
    }

    @Test
    public void outputFileGetsEncSuffixOnlyWhenEncryptionIsOn() {
        File plain = new File(ctx.getExternalFilesDir(null), "session/2026_shot.jpg");

        TestPrefs.put(ctx, "encrypt_media", false);
        assertEquals(plain.getPath(), MediaAccess.outputFile(ctx, plain).getPath());

        TestPrefs.put(ctx, "encrypt_media", true);
        assertEquals(plain.getPath() + ".enc", MediaAccess.outputFile(ctx, plain).getPath());
    }

    @Test
    public void openPlainReadsANonEncryptedFileVerbatim() throws Exception {
        File f = new File(ctx.getExternalFilesDir(null), "clip.bin");
        byte[] data = {1, 2, 3, 4, 5, 42};
        try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
            os.write(data);
        }
        assertArrayEquals(data, MediaAccess.readAll(MediaAccess.openPlain(ctx, f.getAbsolutePath())));
    }

    @Test
    public void readAllDrainsAStream() throws Exception {
        byte[] data = new byte[200_000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        assertArrayEquals(data, MediaAccess.readAll(new ByteArrayInputStream(data)));
    }
}
