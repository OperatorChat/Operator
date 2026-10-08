/*
 * Operator: chat for keypad phones.
 * Copyright (C) 2026 Spanorak (https://www.reddit.com/user/Spanorak)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. It is distributed WITHOUT ANY WARRANTY; see
 * the GNU General Public License (LICENSE) for details.
 */
package chat.operator.core.matrix

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets that must not sit in plain files: the access token that keeps the
 * session signed in, and the passphrase for the encrypted Room store. Each
 * value is encrypted with AES-GCM under a key that is generated inside, and
 * never leaves, the phone's hardware-backed Android keystore. A copy of the
 * app's files without the phone is therefore useless. Cleared on sign-out.
 */
object SessionVault {
    private const val TAG = "SessionVault"
    private const val PREFS = "operator_vault"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEYSTORE_ALIAS = "operator_vault_key"

    @Volatile private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun seal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun open(sealed: String): String? = runCatching {
        val (iv, bytes) = sealed.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(bytes), Charsets.UTF_8)
    }.getOrNull()

    private fun read(ctx: Context, name: String): String? = prefs(ctx).getString(name, null)?.let { open(it) }

    private fun write(ctx: Context, name: String, value: String) {
        runCatching { prefs(ctx).edit().putString(name, seal(value)).apply() }
            .onFailure { Log.w(TAG, "could not store $name: ${it.javaClass.simpleName}") }
    }

    fun accessToken(ctx: Context): String? = read(ctx, KEY_ACCESS_TOKEN)

    fun setAccessToken(ctx: Context, token: String) = write(ctx, KEY_ACCESS_TOKEN, token)

    /** The database passphrase, created on first use: 32 random bytes as hex. */
    @Synchronized
    fun databasePassphrase(ctx: Context): String {
        read(ctx, KEY_DB_PASSPHRASE)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val passphrase = bytes.joinToString("") { "%02x".format(it) }
        write(ctx, KEY_DB_PASSPHRASE, passphrase)
        return passphrase
    }

    fun clear(ctx: Context) {
        runCatching { prefs(ctx).edit().clear().apply() }
    }

    @Synchronized
    fun loadSqlCipher() {
        if (loaded) return
        System.loadLibrary("sqlcipher")
        loaded = true
    }

    /**
     * Is this database file still in plain SQLite form (written before encryption)?
     * Missing or empty files count as fine: they will be created encrypted.
     */
    fun isPlaintextDatabase(dbFile: File): Boolean {
        if (!dbFile.exists() || dbFile.length() < 16) return false
        val header = ByteArray(16)
        RandomAccessFile(dbFile, "r").use { it.readFully(header) }
        return header.toString(Charsets.ISO_8859_1).startsWith("SQLite format 3")
    }

    /**
     * Converts a plaintext database to an encrypted one, carefully: export into
     * a new file with SQLCipher's own export, prove the new file opens with the
     * key and has tables, and only then swap the files, keeping the original as
     * a backup until the swap is complete. Returns true when the database at
     * [dbFile] is encrypted afterwards. On any failure the original is left
     * untouched and false is returned; the caller then opens it unencrypted.
     */
    @Synchronized
    fun encryptLegacyDatabase(dbFile: File, passphrase: String): Boolean {
        if (!isPlaintextDatabase(dbFile)) return true
        Log.i(TAG, "encrypting the existing database (one-time)")
        val target = File(dbFile.path + ".enc")
        val backup = File(dbFile.path + ".bak")
        target.delete(); backup.delete()
        try {
            loadSqlCipher()
            // Build the encrypted file first and pull the plaintext database into it
            // (SQLCipher's documented "export inward" form). Everything runs through
            // cursors on one connection; WAL is off so there is no connection pool.
            val enc = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(target.path, passphrase, null, null)
            try {
                enc.disableWriteAheadLogging()
                fun run(sql: String) = enc.rawQuery(sql, null).use { it.moveToFirst() }
                run("ATTACH DATABASE '${dbFile.path}' AS plaintext KEY ''")
                val attached = enc.rawQuery("PRAGMA database_list", null).use { c ->
                    generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                }
                Log.i(TAG, "attached databases: $attached")
                run("SELECT sqlcipher_export('main', 'plaintext')")
                run("DETACH DATABASE plaintext")
            } finally {
                enc.close()
            }
            if (!target.exists() || target.length() < 1024) error("export produced no file")
            // Prove the new file before touching the old one.
            val check = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                target.path, passphrase, null, net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY, null,
            )
            val tables = try {
                check.rawQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table'", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            } finally {
                check.close()
            }
            if (tables == 0L) error("export produced no tables")
            // Swap: stale WAL/SHM must not be applied to the encrypted file.
            listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            if (!dbFile.renameTo(backup)) error("could not set the original aside")
            if (!target.renameTo(dbFile)) {
                backup.renameTo(dbFile)
                error("could not move the encrypted file into place")
            }
            backup.delete()
            Log.i(TAG, "database encrypted ($tables tables)")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "database encryption failed, keeping the original unencrypted for now: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            target.delete()
            if (!dbFile.exists() && backup.exists()) backup.renameTo(dbFile)
            return false
        }
    }
}
