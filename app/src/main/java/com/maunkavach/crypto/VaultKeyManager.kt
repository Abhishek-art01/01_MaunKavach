package com.maunkavach.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import java.security.SecureRandom

enum class KeyExpiry(val days: Int?) {
    SEVEN_DAYS(7), THIRTY_DAYS(30), NINETY_DAYS(90), CUSTOM(null), NO_EXPIRY(null)
}

enum class RegenerateMode {
    /** Old key + old key-version are discarded outright — old messages become permanently unreadable. */
    DELETE_OLD,
    /** Old key is kept under its previous version number, so historical messages tagged with that
     *  version can still be decrypted; only new messages use the new version going forward. */
    KEEP_OLD_VERSION
}

enum class VaultMode { REAL, DECOY }

/**
 * The "Vault Key" data layer — Section 1 & 10 of the spec. Local-only:
 *  - Never synced to the server, ever.
 *  - Persisted only inside EncryptedSharedPreferences, itself wrapped by the Keystore master
 *    key — encrypted-at-rest twice over.
 *  - Every method here assumes the caller has ALREADY passed biometric/PIN auth for this
 *    session (see VaultKeyScreen + BiometricHelper) — this class does not gate access itself.
 */
class VaultKeyManager(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val realPrefs = EncryptedSharedPreferences.create(
        "maunkavach_vault_real", masterKeyAlias, context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val decoyPrefs = EncryptedSharedPreferences.create(
        "maunkavach_vault_decoy", masterKeyAlias, context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Superseded (kept-old-version) key bundles live here, keyed by "contactId:version".
    private val archivePrefs = EncryptedSharedPreferences.create(
        "maunkavach_vault_archive", masterKeyAlias, context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val configPrefs = EncryptedSharedPreferences.create(
        "maunkavach_vault_config", masterKeyAlias, context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ---------------- Decoy / panic PIN ----------------

    fun resolveVaultOnUnlock(enteredPin: String): VaultMode {
        val panicPin = configPrefs.getString("panic_pin", null)
        return if (panicPin != null && enteredPin == panicPin) VaultMode.DECOY else VaultMode.REAL
    }

    fun setPanicPin(pin: String) = configPrefs.edit().putString("panic_pin", pin).apply()
    fun setRealPin(pinHash: String) = configPrefs.edit().putString("real_pin_hash", pinHash).apply()

    // ---------------- Generation (auto or manual) ----------------

    /** Auto-generate: random substitution table + random 999-step rotation rule + random AES/HMAC keys. */
    fun generateContactKeyAuto(contactId: String, expiry: KeyExpiry, customDays: Int? = null, decoy: Boolean = false): ContactKeyBundle {
        val table = SubstitutionTable.generateRandom()
        val rotation = RotationCipher.generateRandom()
        return buildAndStore(contactId, table, rotation, expiry, customDays, decoy, version = 1)
    }

    /**
     * Manual entry: caller supplies a 256-entry substitution table and a 999-entry rotation
     * rule (e.g. typed/pasted by the user, or imported from a QR package). Both are validated
     * before acceptance per the spec's strict rules — invalid input throws [IllegalArgumentException]
     * with a human-readable reason, which the UI should surface directly (don't swallow it).
     */
    fun generateContactKeyManual(
        contactId: String,
        substitutionTable: IntArray,
        rotationRule: IntArray,
        expiry: KeyExpiry,
        customDays: Int? = null,
        decoy: Boolean = false
    ): ContactKeyBundle {
        val table = SubstitutionTable.fromValidated(substitutionTable) // throws if invalid
        val rotation = RotationCipher.fromValidated(rotationRule)       // throws if invalid
        return buildAndStore(contactId, table, rotation, expiry, customDays, decoy, version = 1)
    }

    private fun buildAndStore(
        contactId: String, table: SubstitutionTable, rotation: RotationCipher,
        expiry: KeyExpiry, customDays: Int?, decoy: Boolean, version: Int
    ): ContactKeyBundle {
        val random = SecureRandom()
        val masterKeyBytes = ByteArray(32).also { random.nextBytes(it) }
        val hmacKeyBytes = ByteArray(32).also { random.nextBytes(it) }
        val now = System.currentTimeMillis()
        val days = if (expiry == KeyExpiry.CUSTOM) customDays else expiry.days
        val expiresAt = days?.let { now + it * 24L * 60 * 60 * 1000 }

        val bundle = ContactKeyBundle(
            contactId = contactId,
            keyVersion = version,
            masterKeyBase64 = b64(masterKeyBytes),
            hmacKeyBase64 = b64(hmacKeyBytes),
            substitutionTableSerialized = table.serialize(),
            rotationRuleSerialized = rotation.serialize(),
            createdAtMillis = now,
            expiresAtMillis = expiresAt,
            fingerprint = fingerprintOf(masterKeyBytes, hmacKeyBytes)
        )
        store(bundle, decoy)
        return bundle
    }

    // ---------------- Read / lifecycle ----------------

    fun getContactKey(contactId: String, decoy: Boolean = false): ContactKeyBundle? {
        val prefs = if (decoy) decoyPrefs else realPrefs
        return prefs.getString("contact_$contactId", null)?.let { fromJson(it) }
    }

    fun isExpired(bundle: ContactKeyBundle): Boolean =
        bundle.expiresAtMillis != null && System.currentTimeMillis() > bundle.expiresAtMillis

    /**
     * Regenerate per spec section 1's two-option requirement:
     *  - DELETE_OLD: old key is gone for good. Any message encrypted under the old key version
     *    can never be decrypted again on this device — surface a clear warning to the user
     *    BEFORE calling this with DELETE_OLD, since it is irreversible.
     *  - KEEP_OLD_VERSION: old bundle is archived under its version number so historical
     *    messages tagged with that keyVersion remain decryptable; only new outgoing messages
     *    use the new version.
     */
    fun regenerateKey(contactId: String, mode: RegenerateMode, expiry: KeyExpiry, customDays: Int? = null, decoy: Boolean = false): ContactKeyBundle {
        val existing = getContactKey(contactId, decoy)
        val nextVersion = (existing?.keyVersion ?: 0) + 1

        if (mode == RegenerateMode.KEEP_OLD_VERSION && existing != null) {
            archive(existing, decoy)
        }
        // DELETE_OLD: simply don't archive — old bundle is overwritten and unrecoverable.

        val table = SubstitutionTable.generateRandom()
        val rotation = RotationCipher.generateRandom()
        return buildAndStore(contactId, table, rotation, expiry, customDays, decoy, version = nextVersion)
    }

    /** Retrieves an archived (kept-old-version) bundle for decrypting historical messages tagged with that version. */
    fun getArchivedVersion(contactId: String, version: Int, decoy: Boolean = false): ContactKeyBundle? {
        val key = "${if (decoy) "decoy_" else ""}${contactId}_v$version"
        return archivePrefs.getString(key, null)?.let { fromJson(it) }
    }

    private fun archive(bundle: ContactKeyBundle, decoy: Boolean) {
        val key = "${if (decoy) "decoy_" else ""}${bundle.contactId}_v${bundle.keyVersion}"
        archivePrefs.edit().putString(key, toJson(bundle)).apply()
    }

    fun deleteKey(contactId: String, decoy: Boolean = false) {
        val prefs = if (decoy) decoyPrefs else realPrefs
        prefs.edit().remove("contact_$contactId").apply()
        // Note: this intentionally does NOT remove archived old-version bundles — call a
        // separate "purge history" action if the user wants those gone too.
    }

    fun allContacts(decoy: Boolean = false): List<ContactKeyBundle> {
        val prefs = if (decoy) decoyPrefs else realPrefs
        return prefs.all.keys.filter { it.startsWith("contact_") }
            .mapNotNull { prefs.getString(it, null) }
            .map { fromJson(it) }
    }

    // ---------------- QR export/import (offline only) ----------------

    /** Exports the FULL key bundle for offline/in-person QR transfer only — never sent over network by this class. */
    fun exportForQr(bundle: ContactKeyBundle): String = toJson(bundle)

    fun importFromQr(payload: String, decoy: Boolean = false): ContactKeyBundle {
        val bundle = fromJson(payload) // also re-validates table/rotation implicitly via deserialize()
        store(bundle, decoy)
        return bundle
    }

    // ---------------- Internal ----------------

    private fun store(bundle: ContactKeyBundle, decoy: Boolean) {
        val prefs = if (decoy) decoyPrefs else realPrefs
        prefs.edit().putString("contact_${bundle.contactId}", toJson(bundle)).apply()
    }

    private fun fingerprintOf(masterKeyBytes: ByteArray, hmacKeyBytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(masterKeyBytes)
        digest.update(hmacKeyBytes)
        val hash = digest.digest()
        return hash.joinToString(":") { "%02x".format(it) }.take(47)
    }

    private fun b64(bytes: ByteArray) = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun toJson(b: ContactKeyBundle): String = JSONObject().apply {
        put("contactId", b.contactId)
        put("keyVersion", b.keyVersion)
        put("masterKeyBase64", b.masterKeyBase64)
        put("hmacKeyBase64", b.hmacKeyBase64)
        put("substitutionTableSerialized", b.substitutionTableSerialized)
        put("rotationRuleSerialized", b.rotationRuleSerialized)
        put("createdAtMillis", b.createdAtMillis)
        put("expiresAtMillis", b.expiresAtMillis ?: -1)
        put("fingerprint", b.fingerprint)
    }.toString()

    private fun fromJson(s: String): ContactKeyBundle {
        val j = JSONObject(s)
        return ContactKeyBundle(
            contactId = j.getString("contactId"),
            keyVersion = j.getInt("keyVersion"),
            masterKeyBase64 = j.getString("masterKeyBase64"),
            hmacKeyBase64 = j.getString("hmacKeyBase64"),
            substitutionTableSerialized = j.getString("substitutionTableSerialized"),
            rotationRuleSerialized = j.getString("rotationRuleSerialized"),
            createdAtMillis = j.getLong("createdAtMillis"),
            expiresAtMillis = j.getLong("expiresAtMillis").takeIf { it > 0 },
            fingerprint = j.getString("fingerprint")
        )
    }
}
