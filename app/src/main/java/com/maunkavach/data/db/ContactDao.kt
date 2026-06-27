package com.maunkavach.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.maunkavach.data.model.Contact

class ContactDao(private val dbHelper: MaunKavachDbHelper) {

    fun upsert(contact: Contact) {
        val values = ContentValues().apply {
            put("id", contact.id)
            put("display_name", contact.displayName)
            put("key_fingerprint", contact.keyFingerprint)
            put("key_version", 1)
            put("key_expires_at", contact.keyExpiresAtMillis)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            "contacts",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun get(id: String): Contact? {
        dbHelper.readableDatabase.query(
            "contacts",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return Contact(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                keyFingerprint = cursor.getString(cursor.getColumnIndexOrThrow("key_fingerprint")),
                keyExpiresAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("key_expires_at")).takeIf { it > 0 }
            )
        }
    }

    fun all(): List<Contact> {
        val rows = mutableListOf<Contact>()
        dbHelper.readableDatabase.query(
            "contacts",
            null,
            null,
            null,
            null,
            null,
            "display_name COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    Contact(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                        keyFingerprint = cursor.getString(cursor.getColumnIndexOrThrow("key_fingerprint")),
                        keyExpiresAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("key_expires_at")).takeIf { it > 0 }
                    )
                )
            }
        }
        return rows
    }
}
