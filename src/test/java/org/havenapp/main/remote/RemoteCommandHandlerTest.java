package org.havenapp.main.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.havenapp.main.TestPrefs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RemoteCommandHandlerTest {

    /** 8 chars, so a "<secret> CMD" message has a space exactly at index secret.length(). */
    private static final String SECRET = "AbC123xy";

    private Context ctx;
    private String lastReply;
    private final RemoteCommandHandler.Reply reply = t -> lastReply = t;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        lastReply = null;
        TestPrefs.put(ctx, "remote_commands_enabled", true);
        TestPrefs.put(ctx, "supervised_enabled", false);
        TestPrefs.put(ctx, "remote_command_secret", SECRET);
        TestPrefs.put(ctx, "remote_cmd_lockout_until", 0L);
        TestPrefs.put(ctx, "remote_cmd_bad_count", 0);
        TestPrefs.put(ctx, "remote_cmd_window_start", 0L);
    }

    private boolean handle(String msg) {
        return RemoteCommandHandler.handle(ctx, msg, reply);
    }

    @Test
    public void ignoredWhenTheChannelIsOff() {
        TestPrefs.put(ctx, "remote_commands_enabled", false);
        assertFalse(handle(SECRET + " STATUS"));
        assertNull(lastReply);
    }

    @Test
    public void ignoredWhenNoSecretConfigured() {
        TestPrefs.put(ctx, "remote_command_secret", "");
        assertFalse(handle(SECRET + " STATUS"));
    }

    @Test
    public void authedStatusReplies() {
        assertTrue(handle(SECRET + " STATUS"));
        assertTrue(lastReply.startsWith("Haven:"));
        assertTrue("MonitorService.getInstance() is null in a unit test", lastReply.contains("disarmed"));
    }

    @Test
    public void unknownCommandListsTheCommands() {
        assertTrue(handle(SECRET + " frobnicate"));
        assertTrue(lastReply.contains("STATUS"));
        assertTrue(lastReply.contains("WIPE CONFIRM"));
    }

    @Test
    public void wipeNeedsExplicitConfirmation() {
        assertTrue(handle(SECRET + " WIPE"));
        assertTrue(lastReply.contains("WIPE CONFIRM"));
    }

    @Test
    public void secretIsCaseSensitive() {
        assertFalse(handle(SECRET.toLowerCase() + " STATUS"));
        assertNull("a wrong-case secret is not a handled command", lastReply);
    }

    @Test
    public void messageWithoutASpaceAfterTheSecretIsNotAnAttempt() {
        assertFalse(handle(SECRET + "STATUS"));
        assertEquals("not command-shaped -> no bad attempt recorded",
                0, TestPrefs.of(ctx).getInt("remote_cmd_bad_count", 0));
    }

    @Test
    public void fiveWrongSecretsLockOutEvenACorrectOne() {
        for (int i = 0; i < 5; i++) {
            assertFalse(handle("WrongSec STATUS")); // "WrongSec" is 8 chars, like SECRET
        }
        long until = TestPrefs.of(ctx).getLong("remote_cmd_lockout_until", 0L);
        assertTrue("lock-out window is set into the future", until > System.currentTimeMillis());

        assertFalse("correct secret is refused while locked out", handle(SECRET + " STATUS"));
        assertNull(lastReply);
    }
}
