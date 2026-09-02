package org.havenapp.main.pairing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PairingPayloadTest {

    @Test
    public void encodeDecodeRoundTrip() {
        PairingPayload in = new PairingPayload("Pixel 7", "https://ntfy.sh",
                "haven-abc123", "s3cr3t", "abcd.onion", "onionpw");
        String wire = in.encode();
        assertTrue("has the versioned prefix", wire.startsWith("HVPAIR1:"));

        PairingPayload out = PairingPayload.decode(wire);
        assertEquals(in.name, out.name);
        assertEquals(in.ntfyServer, out.ntfyServer);
        assertEquals(in.ntfyTopic, out.ntfyTopic);
        assertEquals(in.cmdSecret, out.cmdSecret);
        assertEquals(in.onion, out.onion);
        assertEquals(in.onionPw, out.onionPw);
    }

    @Test
    public void nullOptionalFieldsBecomeEmptyStrings() {
        PairingPayload out = PairingPayload.decode(
                new PairingPayload("d", "s", "t", "k", null, null).encode());
        assertEquals("", out.onion);
        assertEquals("", out.onionPw);
    }

    @Test
    public void decodeRejectsGarbageAndWrongPrefix() {
        assertNull(PairingPayload.decode(null));
        assertNull(PairingPayload.decode(""));
        assertNull(PairingPayload.decode("not a pairing string"));
        assertNull(PairingPayload.decode("HVPAIR1:%%%not-base64%%%"));
        assertNull(PairingPayload.decode("HVPAIR2:" + "eyJ2IjoxfQ=="));
    }
}
