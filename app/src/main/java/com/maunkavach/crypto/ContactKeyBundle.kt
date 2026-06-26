package com.maunkavach.crypto

import javax.crypto.spec.SecretKeySpec

/**
 * Everything Section 1 of the spec asks Vault Key to store per contact, bundled together.
 * Persisted only inside EncryptedSharedPreferences (see VaultKeyManager) — never on the
 * server, never in the plain SQLite DB.
 */
data class ContactKeyBundle(
    val contactId: String,
    val keyVersion: Int,                 // bumped on regenerate; lets you keep old-version messages labeled, not decryptable without the discarded key
    val masterKeyBase64: String,         // contact master key — root of ephemeral per-message key derivation
    val hmacKeyBase64: String,           // separate key for HMAC signing, never reused for AES
    val substitutionTableSerialized: String,
    val rotationRuleSerialized: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long?,
    val fingerprint: String
) {
    fun masterKey(): SecretKeySpec = SecretKeySpec(b64decode(masterKeyBase64), "AES")
    fun hmacKey(): ByteArray = b64decode(hmacKeyBase64)
    fun substitutionTable(): SubstitutionTable = SubstitutionTable.deserialize(substitutionTableSerialized)
    fun rotationCipher(): RotationCipher = RotationCipher.deserialize(rotationRuleSerialized)

    companion object {
        private fun b64decode(s: String) = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
    }
}

/** The full on-wire/on-server package for one message, matching spec section 5 exactly. */
data class EncryptedMessagePackage(
    val messageId: String,
    val senderUuid: String,
    val receiverUuid: String,
    val counter: Long,
    val timestampMillis: Long,
    val nonceBase64: String,
    val ciphertextBase64: String,
    val encryptedMetadataBase64: String?, // e.g. self-destruct policy, padding-bucket info — itself encrypted
    val hmacBase64: String,
    val keyVersion: Int
)
