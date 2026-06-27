package com.maunkavach.data.db

import android.content.ContentValues
import com.maunkavach.crypto.EncryptedMessagePackage
import com.maunkavach.data.model.MessageDirection

/**
 * Every write/read here deals in ciphertext + HMAC fields only (an [EncryptedMessagePackage]
 * is exactly what the server is allowed to see too). Decryption happens strictly in the
 * UI/ViewModel layer after the Vault Key supplies the contact's key bundle — this DAO never
 * has access to keys and never decrypts or verifies anything itself.
 */
class MessageDao(private val dbHelper: MaunKavachDbHelper) {

    fun insert(pkg: EncryptedMessagePackage, contactId: String, direction: MessageDirection, isFile: Boolean = false, fileBlobUrl: String? = null) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("message_id", pkg.messageId)
            put("contact_id", contactId)
            put("direction", direction.name)
            put("sender_uuid", pkg.senderUuid)
            put("receiver_uuid", pkg.receiverUuid)
            put("counter", pkg.counter)
            put("nonce_base64", pkg.nonceBase64)
            put("ciphertext_base64", pkg.ciphertextBase64)
            put("encrypted_metadata_base64", pkg.encryptedMetadataBase64)
            put("hmac_base64", pkg.hmacBase64)
            put("key_version", pkg.keyVersion)
            put("is_file", if (isFile) 1 else 0)
            put("encrypted_file_blob_url", fileBlobUrl)
            put("timestamp", pkg.timestampMillis)
        }
        db.insertOrThrow("messages", null, values)
    }

    /** Returns raw (still-encrypted, still-unverified) rows — caller runs them through MessagePipeline.decryptMessage. */
    fun getEncryptedThread(contactId: String): List<EncryptedMessageRow> {
        val db = dbHelper.readableDatabase
        val rows = mutableListOf<EncryptedMessageRow>()
        db.query("messages", null, "contact_id = ?", arrayOf(contactId), null, null, "timestamp ASC").use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    EncryptedMessageRow(
                        pkg = EncryptedMessagePackage(
                            messageId = cursor.getString(cursor.getColumnIndexOrThrow("message_id")),
                            senderUuid = cursor.getString(cursor.getColumnIndexOrThrow("sender_uuid")),
                            receiverUuid = cursor.getString(cursor.getColumnIndexOrThrow("receiver_uuid")),
                            counter = cursor.getLong(cursor.getColumnIndexOrThrow("counter")),
                            timestampMillis = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                            nonceBase64 = cursor.getString(cursor.getColumnIndexOrThrow("nonce_base64")),
                            ciphertextBase64 = cursor.getString(cursor.getColumnIndexOrThrow("ciphertext_base64")),
                            encryptedMetadataBase64 = cursor.getString(cursor.getColumnIndexOrThrow("encrypted_metadata_base64")),
                            hmacBase64 = cursor.getString(cursor.getColumnIndexOrThrow("hmac_base64")),
                            keyVersion = cursor.getInt(cursor.getColumnIndexOrThrow("key_version"))
                        ),
                        direction = MessageDirection.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("direction"))),
                        isFile = cursor.getInt(cursor.getColumnIndexOrThrow("is_file")) == 1,
                        encryptedFileBlobUrl = cursor.getString(cursor.getColumnIndexOrThrow("encrypted_file_blob_url")),
                        deliveryStatus = cursor.getString(cursor.getColumnIndexOrThrow("delivery_status"))
                    )
                )
            }
        }
        return rows
    }

    fun latestRowsByContact(): Map<String, ChatPreviewRow> {
        val rows = mutableMapOf<String, ChatPreviewRow>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT m.contact_id, m.direction, m.timestamp, m.delivery_status, m.is_file,
                   (SELECT COUNT(*) FROM messages u
                    WHERE u.contact_id = m.contact_id
                      AND u.direction = 'RECEIVED'
                      AND u.delivery_status != 'READ') AS unread_count
            FROM messages m
            INNER JOIN (
                SELECT contact_id, MAX(timestamp) AS max_timestamp
                FROM messages
                GROUP BY contact_id
            ) latest
            ON latest.contact_id = m.contact_id AND latest.max_timestamp = m.timestamp
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getString(cursor.getColumnIndexOrThrow("contact_id"))
                rows[contactId] = ChatPreviewRow(
                    contactId = contactId,
                    direction = MessageDirection.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("direction"))),
                    timestampMillis = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    deliveryStatus = cursor.getString(cursor.getColumnIndexOrThrow("delivery_status")),
                    isFile = cursor.getInt(cursor.getColumnIndexOrThrow("is_file")) == 1,
                    unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow("unread_count"))
                )
            }
        }
        return rows
    }

    /** "Delete for both sides" locally — caller separately tells server to purge its row too. */
    fun deleteMessage(messageId: String) {
        dbHelper.writableDatabase.delete("messages", "message_id = ?", arrayOf(messageId))
    }

    fun purgeExpiredSelfDestruct(nowMillis: Long) {
        dbHelper.writableDatabase.delete(
            "messages", "self_destruct_at IS NOT NULL AND self_destruct_at <= ?", arrayOf(nowMillis.toString())
        )
    }

    // ---- Replay-protection persistence (spec section 5/22: must survive app restart) ----

    fun loadReplayState(contactId: String): Pair<Long, List<String>> {
        val db = dbHelper.readableDatabase
        db.query("replay_state", null, "contact_id = ?", arrayOf(contactId), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val last = cursor.getLong(cursor.getColumnIndexOrThrow("last_received_counter"))
                val ids = cursor.getString(cursor.getColumnIndexOrThrow("recent_message_ids"))
                    .split(",").filter { it.isNotBlank() }
                return last to ids
            }
        }
        return -1L to emptyList()
    }

    /** Keeps only the most recent [maxIds] message_ids to bound storage growth. */
    fun saveReplayState(contactId: String, lastCounter: Long, recentIds: List<String>, maxIds: Int = 200) {
        val bounded = recentIds.takeLast(maxIds)
        val values = ContentValues().apply {
            put("contact_id", contactId)
            put("last_received_counter", lastCounter)
            put("recent_message_ids", bounded.joinToString(","))
        }
        dbHelper.writableDatabase.insertWithOnConflict("replay_state", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }
}

data class ChatPreviewRow(
    val contactId: String,
    val direction: MessageDirection,
    val timestampMillis: Long,
    val deliveryStatus: String,
    val isFile: Boolean,
    val unreadCount: Int
)

data class EncryptedMessageRow(
    val pkg: EncryptedMessagePackage,
    val direction: MessageDirection,
    val isFile: Boolean,
    val encryptedFileBlobUrl: String?,
    val deliveryStatus: String
)
