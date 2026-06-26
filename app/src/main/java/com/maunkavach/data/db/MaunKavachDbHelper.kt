package com.maunkavach.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Native android.database.sqlite — NOT SQLCipher (per the strict native-only spec).
 * "Encrypted manually before saving": every column that could hold sensitive content stores
 * only ciphertext/base64 strings produced by MessagePipeline/FileCrypto. SQLite itself never
 * sees plaintext. The on-disk .db file is opaque without the Keystore master key needed to
 * decrypt the EncryptedSharedPreferences holding the actual contact key material (which never
 * lives in this DB at all).
 */
class MaunKavachDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                message_id TEXT PRIMARY KEY,           -- spec section 5: unique message_id
                contact_id TEXT NOT NULL,
                direction TEXT NOT NULL,
                sender_uuid TEXT NOT NULL,
                receiver_uuid TEXT NOT NULL,
                counter INTEGER NOT NULL,               -- ratchet counter, used for replay rejection
                nonce_base64 TEXT NOT NULL,
                ciphertext_base64 TEXT NOT NULL,        -- AES-256-GCM output, never plaintext
                encrypted_metadata_base64 TEXT,          -- self-destruct policy etc, itself encrypted
                hmac_base64 TEXT NOT NULL,               -- HMAC-SHA256 tag over ciphertext+nonce+counter+metadata
                key_version INTEGER NOT NULL DEFAULT 1,
                is_file INTEGER NOT NULL DEFAULT 0,
                encrypted_file_blob_url TEXT,             -- local opaque path or remote URL, never original name
                timestamp INTEGER NOT NULL,
                self_destruct_at INTEGER,
                delivery_status TEXT NOT NULL DEFAULT 'SENT'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE contacts (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,            -- UI label only, not a secret
                key_fingerprint TEXT NOT NULL,          -- fingerprint only, never the raw key
                key_version INTEGER NOT NULL DEFAULT 1,
                key_expires_at INTEGER,
                trusted_fingerprint_at INTEGER          -- when the user last manually verified this fingerprint (spec section 18)
            )
            """.trimIndent()
        )

        // Replay-protection state, persisted so it survives app restarts (spec section 5/22) —
        // ReplayProtection.kt's in-memory set alone would reset on every process restart.
        db.execSQL(
            """
            CREATE TABLE replay_state (
                contact_id TEXT PRIMARY KEY,
                last_received_counter INTEGER NOT NULL DEFAULT -1,
                recent_message_ids TEXT NOT NULL DEFAULT ''   -- comma-joined bounded window, newest last
            )
            """.trimIndent()
        )

        // NOTE: actual key material (master key, HMAC key, substitution table, rotation rule)
        // never lives in this DB at all — that lives only in EncryptedSharedPreferences via
        // VaultKeyManager, itself wrapped by the hardware Keystore master key. This DB never
        // stores: plaintext message, plaintext file, plaintext filename, MIME type, or any
        // encryption key — matching the spec's "must NOT store" list exactly.
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS contacts")
        db.execSQL("DROP TABLE IF EXISTS replay_state")
        onCreate(db)
    }

    companion object {
        const val DB_NAME = "maunkavach_encrypted.db"
        const val DB_VERSION = 2
    }
}
