package org.havenapp.main.pairing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PairedStoreTest {

    private PairedStore store;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        store = new PairedStore(ctx);
    }

    private static PairingPayload payload(String name, String topic, String secret) {
        return new PairingPayload(name, "https://ntfy.sh", topic, secret, "", "");
    }

    @Test
    public void addThenReadBack() {
        assertEquals(0, store.all().size());
        store.addOrReplace(payload("Watch phone", "haven-t1", "k1"));

        List<PairedStore.Device> all = store.all();
        assertEquals(1, all.size());
        assertEquals("Watch phone", all.get(0).name);
        assertEquals("haven-t1", all.get(0).topic);
        assertEquals("k1", all.get(0).secret);
        assertEquals("https://ntfy.sh", all.get(0).server);
    }

    @Test
    public void addOrReplaceDedupesByTopic() {
        store.addOrReplace(payload("old name", "haven-t1", "k1"));
        store.addOrReplace(payload("new name", "haven-t1", "k2")); // same topic
        store.addOrReplace(payload("other", "haven-t2", "k3"));

        List<PairedStore.Device> all = store.all();
        assertEquals(2, all.size());
        boolean replaced = false;
        for (PairedStore.Device d : all) {
            if (d.topic.equals("haven-t1")) {
                assertEquals("new name", d.name);
                assertEquals("k2", d.secret);
                replaced = true;
            }
        }
        assertTrue(replaced);
    }

    @Test
    public void removeByTopic() {
        store.addOrReplace(payload("a", "haven-t1", "k1"));
        store.addOrReplace(payload("b", "haven-t2", "k2"));
        store.removeByTopic("haven-t1");

        List<PairedStore.Device> all = store.all();
        assertEquals(1, all.size());
        assertEquals("haven-t2", all.get(0).topic);
    }
}
