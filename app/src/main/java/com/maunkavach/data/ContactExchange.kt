package com.maunkavach.data

import android.util.Base64
import com.maunkavach.crypto.ContactKeyBundle
import com.maunkavach.data.model.Contact
import org.json.JSONObject
import java.security.MessageDigest

data class SafeContactExchange(
    val username: String,
    val displayName: String,
    val keyBundle: ContactKeyBundle? = null
)

object ContactExchange {
    private const val TYPE = "maunkavach-contact"
    private const val FULL_PREFIX = "MKF1:"
    private const val VERSION = 1
    private val usernamePattern = Regex("^[A-Za-z0-9_.-]{3,64}$")

    fun create(username: String, displayName: String = username): String {
        val cleanUsername = username.trim()
        require(usernamePattern.matches(cleanUsername)) { "Invalid username for contact QR." }
        if (displayName.trim() == cleanUsername) return "MKC1:$cleanUsername"
        return JSONObject().apply {
            put("type", TYPE)
            put("version", VERSION)
            put("username", cleanUsername)
            put("displayName", displayName.trim().ifBlank { cleanUsername })
        }.toString()
    }

    fun createFull(username: String, bundle: ContactKeyBundle): String {
        val cleanUsername = username.trim()
        require(usernamePattern.matches(cleanUsername)) { "Invalid username for contact QR." }
        require(bundle.contactId == cleanUsername) { "QR key bundle must match the username." }

        val body = JSONObject().apply {
            put("u", cleanUsername)
            put("v", bundle.keyVersion)
            put("mk", bundle.masterKeyBase64)
            put("hk", bundle.hmacKeyBase64)
            put("st", b64(bytesFromCsv(bundle.substitutionTableSerialized)))
            put("rr", b64(bytesFromCsv(bundle.rotationRuleSerialized)))
            put("ca", bundle.createdAtMillis)
            put("ea", bundle.expiresAtMillis ?: -1)
            put("fp", bundle.fingerprint)
        }.toString()
        return FULL_PREFIX + b64(body.toByteArray(Charsets.UTF_8))
    }

    fun parse(payload: String): SafeContactExchange {
        val cleanPayload = payload.trim()
        if (cleanPayload.startsWith(FULL_PREFIX)) {
            val body = String(Base64.decode(cleanPayload.removePrefix(FULL_PREFIX), B64_FLAGS), Charsets.UTF_8)
            val json = JSONObject(body)
            val username = json.getString("u").trim()
            if (!usernamePattern.matches(username)) {
                throw IllegalArgumentException("QR contact username is invalid.")
            }
            val bundle = ContactKeyBundle(
                contactId = username,
                keyVersion = json.getInt("v"),
                masterKeyBase64 = json.getString("mk"),
                hmacKeyBase64 = json.getString("hk"),
                substitutionTableSerialized = csvFromBytes(Base64.decode(json.getString("st"), B64_FLAGS), 256),
                rotationRuleSerialized = csvFromBytes(Base64.decode(json.getString("rr"), B64_FLAGS), 999),
                createdAtMillis = json.getLong("ca"),
                expiresAtMillis = json.getLong("ea").takeIf { it > 0 },
                fingerprint = json.getString("fp")
            )
            bundle.substitutionTable()
            bundle.rotationCipher()
            return SafeContactExchange(username = username, displayName = username, keyBundle = bundle)
        }

        if (cleanPayload.startsWith("MKC1:")) {
            val username = cleanPayload.removePrefix("MKC1:").trim()
            if (!usernamePattern.matches(username)) {
                throw IllegalArgumentException("QR contact username is invalid.")
            }
            return SafeContactExchange(username = username, displayName = username)
        }

        val json = runCatching { JSONObject(cleanPayload) }.getOrElse {
            throw IllegalArgumentException("Scanned data is not a MaunKavach contact QR.")
        }
        if (json.optString("type") != TYPE || json.optInt("version") != VERSION) {
            throw IllegalArgumentException("Unsupported MaunKavach contact QR.")
        }
        val username = json.optString("username").trim()
        if (!usernamePattern.matches(username)) {
            throw IllegalArgumentException("QR contact username is invalid.")
        }
        val displayName = json.optString("displayName", username).trim().ifBlank { username }
        return SafeContactExchange(username = username, displayName = displayName)
    }

    fun toContact(exchange: SafeContactExchange): Contact =
        Contact(
            id = exchange.username,
            displayName = exchange.displayName,
            keyFingerprint = exchange.keyBundle?.fingerprint ?: publicContactFingerprint(exchange.username),
            keyExpiresAtMillis = exchange.keyBundle?.expiresAtMillis
        )

    fun fullBundleJson(bundle: ContactKeyBundle): String =
        JSONObject().apply {
            put("contactId", bundle.contactId)
            put("keyVersion", bundle.keyVersion)
            put("masterKeyBase64", bundle.masterKeyBase64)
            put("hmacKeyBase64", bundle.hmacKeyBase64)
            put("substitutionTableSerialized", bundle.substitutionTableSerialized)
            put("rotationRuleSerialized", bundle.rotationRuleSerialized)
            put("createdAtMillis", bundle.createdAtMillis)
            put("expiresAtMillis", bundle.expiresAtMillis ?: -1)
            put("fingerprint", bundle.fingerprint)
        }.toString()

    private fun publicContactFingerprint(username: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("maunkavach-contact:$username".toByteArray(Charsets.UTF_8))
        return digest.joinToString(":") { "%02x".format(it) }.take(47)
    }

    private fun bytesFromCsv(csv: String): ByteArray =
        csv.split(",").map { it.trim().toInt().toByte() }.toByteArray()

    private fun csvFromBytes(bytes: ByteArray, expectedSize: Int): String {
        if (bytes.size != expectedSize) throw IllegalArgumentException("QR key data has invalid length.")
        return bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, B64_FLAGS)

    private const val B64_FLAGS = Base64.NO_WRAP or Base64.URL_SAFE
}
