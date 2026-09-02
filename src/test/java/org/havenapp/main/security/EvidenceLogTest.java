package org.havenapp.main.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class EvidenceLogTest {

    private Context ctx;
    private File log;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        log = new File(ctx.getExternalFilesDir(null), "evidence.log");
        //noinspection ResultOfMethodCallIgnored
        log.delete();
    }

    private static boolean has(List<String> problems, String needle) {
        for (String p : problems) if (p.contains(needle)) return true;
        return false;
    }

    @Test
    public void freshChainVerifiesIntact() {
        assertTrue(has(EvidenceLog.verify(ctx), "No evidence log yet"));

        EvidenceLog.append(ctx, 1, null);
        EvidenceLog.append(ctx, 2, null);
        EvidenceLog.append(ctx, 6, null);

        assertEquals("no problems on an untouched chain", 0, EvidenceLog.verify(ctx).size());
    }

    @Test
    public void editingAnEarlierRowBreaksTheChain() throws Exception {
        EvidenceLog.append(ctx, 1, null);
        EvidenceLog.append(ctx, 2, null);
        EvidenceLog.append(ctx, 3, null);

        String[] lines = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8)
                .split("\n");
        lines[1] = lines[1].replaceFirst("\\|2\\|", "|9|"); // tamper with the type field
        Files.write(log.toPath(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));

        List<String> problems = EvidenceLog.verify(ctx);
        assertTrue("verify flags the tampered row", has(problems, "chain broken"));
    }

    @Test
    public void pipeAndNewlineInTheValueAreSanitisedSoTheChainSurvives() {
        EvidenceLog.append(ctx, 1, null);
        // a power event value historically broke the chain: "100% \nSTATE: Disconnected"
        EvidenceLog.append(ctx, 5, "100% \nSTATE: Disconnected | extra");
        EvidenceLog.append(ctx, 1, null);

        assertEquals(0, EvidenceLog.verify(ctx).size());
    }

    @Test
    public void alteringAReferencedMediaFileIsDetected() throws Exception {
        File media = new File(ctx.getExternalFilesDir(null), "shot.jpg");
        Files.write(media.toPath(), "original-bytes".getBytes(StandardCharsets.UTF_8));

        EvidenceLog.append(ctx, 1, media.getAbsolutePath());
        assertEquals("intact right after capture", 0, EvidenceLog.verify(ctx).size());

        Files.write(media.toPath(), "tampered-bytes!".getBytes(StandardCharsets.UTF_8));
        assertTrue("verify flags the altered media", has(EvidenceLog.verify(ctx), "media altered"));

        //noinspection ResultOfMethodCallIgnored
        media.delete();
        assertTrue("verify flags the missing media", has(EvidenceLog.verify(ctx), "media missing"));
    }
}
