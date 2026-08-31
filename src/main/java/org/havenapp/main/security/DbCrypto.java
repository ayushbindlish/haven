package org.havenapp.main.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.security.SecureRandom;

/**
 * Manages the SQLCipher passphrase for the event database and the one-time migration of
 * an existing plaintext {@code haven.db}. The passphrase is a random 32-byte value stored
 * {@link VaultCrypto#aead(Context) AEAD}-encrypted in prefs (so it's protected by the
 * Keystore master key, same as the media vault).
 */
public final class DbCrypto {

    private static final String TAG = "DbCrypto";
    private static final String PREFS = "org.havenapp.main";
    private static final String KEY_PASS = "db_pass_enc";
    private static final String KEY_MIGRATED = "db_encrypted";

    private DbCrypto() {}

    public static byte[] passphrase(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = sp.getString(KEY_PASS, null);
        try {
            if (stored != null) {
                return VaultCrypto.aead(ctx).decrypt(Base64.decode(stored, Base64.NO_WRAP),
                        "havendb".getBytes());
            }
            byte[] pass = new byte[32];
            new SecureRandom().nextBytes(pass);
            byte[] wrapped = VaultCrypto.aead(ctx).encrypt(pass, "havendb".getBytes());
            sp.edit().putString(KEY_PASS, Base64.encodeToString(wrapped, Base64.NO_WRAP)).apply();
            return pass;
        } catch (Exception e) {
            throw new RuntimeException("db passphrase unavailable", e);
        }
    }

    public static boolean isMigrated(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MIGRATED, false);
    }

    private static void setMigrated(Context ctx, boolean v) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MIGRATED, v).apply();
    }

    /**
     * If a plaintext {@code haven.db} exists and we haven't migrated yet, export it to an
     * encrypted copy and swap. No-op if already migrated or there's nothing to migrate.
     * @return true if the DB is now (or was already) encrypted and usable.
     */
    public static boolean ensureEncrypted(Context ctx) {
        File db = ctx.getDatabasePath("haven.db");
        // net.zetetic:sqlcipher-android loads its native library on first use - no loadLibs().
        String pass = new String(passphrase(ctx));

        if (isMigrated(ctx) || !db.exists()) {
            setMigrated(ctx, true);
            return true;
        }

        // Is it plaintext? Try to open with an empty key.
        boolean plaintext = false;
        try (net.zetetic.database.sqlcipher.SQLiteDatabase test =
                     net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                             db.getPath(), "", null,
                             net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY, null)) {
            test.rawQuery("SELECT count(*) FROM sqlite_master", null).close();
            plaintext = true;
        } catch (Exception notPlain) {
            // already encrypted (or corrupt) - assume encrypted
            setMigrated(ctx, true);
            return true;
        }

        if (!plaintext) {
            setMigrated(ctx, true);
            return true;
        }

        File enc = new File(db.getParent(), "haven-enc.db");
        //noinspection ResultOfMethodCallIgnored
        enc.delete();
        try (net.zetetic.database.sqlcipher.SQLiteDatabase src =
                     net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                             db.getPath(), "", null,
                             net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE, null)) {
            src.rawExecSQL("ATTACH DATABASE ? AS encrypted KEY ?",
                    new Object[]{enc.getPath(), pass});
            src.rawExecSQL("SELECT sqlcipher_export('encrypted')");
            src.rawExecSQL("DETACH DATABASE encrypted");
        } catch (Exception e) {
            Log.e(TAG, "migration export failed", e);
            //noinspection ResultOfMethodCallIgnored
            enc.delete();
            return false;
        }

        // swap
        File bak = new File(db.getParent(), "haven-plain.bak");
        //noinspection ResultOfMethodCallIgnored
        bak.delete();
        if (db.renameTo(bak) && enc.renameTo(db)) {
            //noinspection ResultOfMethodCallIgnored
            bak.delete();
            new File(db.getPath() + "-wal").delete();
            new File(db.getPath() + "-shm").delete();
            setMigrated(ctx, true);
            Log.i(TAG, "database migrated to SQLCipher");
            return true;
        }
        Log.e(TAG, "migration swap failed");
        return false;
    }
}
