package org.havenapp.main.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Manages the SQLCipher passphrase for the event database and the one-time migration of
 * an existing plaintext {@code haven.db}. The passphrase is a 64-char lowercase hex string
 * (32 random bytes) stored {@link VaultCrypto#aead(Context) AEAD}-encrypted in prefs (so
 * it's protected by the Keystore master key, same as the media vault).
 *
 * <p>It is deliberately printable ASCII: SQLCipher's {@code SupportOpenHelperFactory(byte[])}
 * and the {@code ATTACH DATABASE ... KEY '...'} statement used for the migration must key
 * off the exact same bytes, and a raw random blob can't be embedded in SQL or round-tripped
 * through {@code new String(...)} without corruption.
 */
public final class DbCrypto {

    private static final String TAG = "DbCrypto";
    private static final String PREFS = "org.havenapp.main";
    private static final String KEY_PASS = "db_pass_enc";
    private static final String KEY_MIGRATED = "db_encrypted";

    private static volatile boolean sNativeLoaded = false;

    private DbCrypto() {}

    /**
     * net.zetetic:sqlcipher-android 4.6.x does NOT auto-load its native library — the old
     * {@code SQLiteDatabase.loadLibs()} is gone and there's no static loader. Call this
     * before opening any {@code net.zetetic.database.sqlcipher.SQLiteDatabase} or handing
     * a {@code SupportOpenHelperFactory} to Room.
     */
    public static synchronized boolean loadNativeLib() {
        if (sNativeLoaded) return true;
        try {
            System.loadLibrary("sqlcipher");
            sNativeLoaded = true;
        } catch (Throwable t) {
            Log.e(TAG, "libsqlcipher.so failed to load", t);
        }
        return sNativeLoaded;
    }

    private static boolean isHexKey(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
        }
        return true;
    }

    private static String newHexKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : raw) sb.append(Character.forDigit((b >> 4) & 0xf, 16))
                             .append(Character.forDigit(b & 0xf, 16));
        return sb.toString();
    }

    /**
     * The DB passphrase as bytes (64 ASCII hex chars). Generated and stored on first call.
     * A stored value in the old raw-blob format is regenerated as long as we haven't
     * migrated yet (nothing is encrypted with it).
     */
    public static byte[] passphrase(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = sp.getString(KEY_PASS, null);
        try {
            String key = null;
            if (stored != null) {
                byte[] plain = VaultCrypto.aead(ctx).decrypt(
                        Base64.decode(stored, Base64.NO_WRAP), "havendb".getBytes());
                String s = new String(plain, StandardCharsets.US_ASCII);
                if (isHexKey(s)) {
                    key = s;
                } else if (isMigrated(ctx)) {
                    // shouldn't happen, but don't throw away a key a live DB depends on
                    key = s;
                }
            }
            if (key == null) {
                key = newHexKey();
                byte[] wrapped = VaultCrypto.aead(ctx).encrypt(
                        key.getBytes(StandardCharsets.US_ASCII), "havendb".getBytes());
                sp.edit().putString(KEY_PASS, Base64.encodeToString(wrapped, Base64.NO_WRAP)).apply();
            }
            return key.getBytes(StandardCharsets.US_ASCII);
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

    private static void deleteWalShm(File db) {
        //noinspection ResultOfMethodCallIgnored
        new File(db.getPath() + "-wal").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(db.getPath() + "-shm").delete();
    }

    /**
     * If a plaintext {@code haven.db} exists and we haven't migrated yet, export it to an
     * encrypted copy and swap. No-op if already migrated or there's nothing to migrate.
     * @return true if the DB is now (or was already) encrypted and usable.
     */
    public static boolean ensureEncrypted(Context ctx) {
        if (!loadNativeLib()) return false;   // no libsqlcipher.so -> stay plaintext

        File db = ctx.getDatabasePath("haven.db");
        File bak = new File(db.getParent(), "haven-plain.bak");

        // Recover from a half-finished earlier attempt: data parked in the .bak while
        // haven.db is missing or was recreated empty.
        if (bak.exists() && (!db.exists() || db.length() <= 8192)) {
            deleteWalShm(db);
            //noinspection ResultOfMethodCallIgnored
            db.delete();
            if (bak.renameTo(db)) {
                Log.w(TAG, "recovered plaintext haven.db from interrupted migration");
            }
        }

        String pass = new String(passphrase(ctx), StandardCharsets.US_ASCII);

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
        deleteWalShm(enc);
        try (net.zetetic.database.sqlcipher.SQLiteDatabase src =
                     net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                             db.getPath(), "", null,
                             net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE
                                     | net.zetetic.database.sqlcipher.SQLiteDatabase.CREATE_IF_NECESSARY,
                             null)) {
            // NB: ATTACH inherits the main connection's open flags, so CREATE_IF_NECESSARY
            // above is what lets it create haven-enc.db. ATTACH ... KEY also doesn't accept
            // bound parameters here; the key is hex so it's safe to inline, and the path is
            // app-internal (no quote chars).
            src.rawExecSQL("ATTACH DATABASE '" + enc.getPath() + "' AS encrypted KEY '" + pass + "'");
            src.rawExecSQL("SELECT sqlcipher_export('encrypted')");
            src.rawExecSQL("DETACH DATABASE encrypted");
        } catch (Exception e) {
            Log.e(TAG, "migration export failed", e);
            //noinspection ResultOfMethodCallIgnored
            enc.delete();
            return false;
        }

        // Verify the encrypted copy actually opens with our key before we throw the
        // plaintext away.
        try (net.zetetic.database.sqlcipher.SQLiteDatabase check =
                     net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                             enc.getPath(), pass, null,
                             net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY, null)) {
            check.rawQuery("SELECT count(*) FROM sqlite_master", null).close();
        } catch (Exception e) {
            Log.e(TAG, "encrypted copy failed to reopen; keeping plaintext", e);
            //noinspection ResultOfMethodCallIgnored
            enc.delete();
            deleteWalShm(enc);
            return false;
        }

        // swap: plaintext -> .bak, encrypted -> haven.db, then drop the .bak
        //noinspection ResultOfMethodCallIgnored
        bak.delete();
        deleteWalShm(db);
        if (!db.renameTo(bak)) {
            Log.e(TAG, "migration swap failed (could not move plaintext aside)");
            //noinspection ResultOfMethodCallIgnored
            enc.delete();
            return false;
        }
        if (!enc.renameTo(db)) {
            Log.e(TAG, "migration swap failed (could not move encrypted into place); rolling back");
            //noinspection ResultOfMethodCallIgnored
            bak.renameTo(db);
            //noinspection ResultOfMethodCallIgnored
            enc.delete();
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        bak.delete();
        deleteWalShm(db); // stale plaintext WAL/SHM must not sit next to the encrypted db
        setMigrated(ctx, true);
        Log.i(TAG, "database migrated to SQLCipher");
        return true;
    }
}
