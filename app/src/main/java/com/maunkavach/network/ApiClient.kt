package com.maunkavach.network

import com.maunkavach.BuildConfig
import com.maunkavach.crypto.EncryptedMessagePackage
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class AuthSession(
    val token: String,
    val username: String,
    val userId: String
)

data class ServerMessageRow(
    val messageId: String,
    val senderUuid: String,
    val receiverUuid: String,
    val counter: Long,
    val nonceBase64: String,
    val encryptedPayload: String,
    val encryptedMetadata: String?,
    val hmacBase64: String,
    val keyVersion: Int,
    val createdAt: String?
) {
    fun toPackage(timestampMillis: Long = System.currentTimeMillis()): EncryptedMessagePackage =
        EncryptedMessagePackage(
            messageId = messageId,
            senderUuid = senderUuid,
            receiverUuid = receiverUuid,
            counter = counter,
            timestampMillis = timestampMillis,
            nonceBase64 = nonceBase64,
            ciphertextBase64 = encryptedPayload,
            encryptedMetadataBase64 = encryptedMetadata,
            hmacBase64 = hmacBase64,
            keyVersion = keyVersion
        )
}

/**
 * Thin native HTTPS client for the real MaunKavach backend. This class never handles
 * plaintext message bodies except account credentials for the auth endpoints.
 */
object ApiClient {

    val baseUrl: String = BuildConfig.SERVER_BASE_URL.trimEnd('/')

    init {
        require(baseUrl.startsWith("https://")) { "Release server URL must use HTTPS." }
        require(!baseUrl.contains("example") && !baseUrl.contains("invalid.local")) {
            "Server URL is not configured."
        }
    }

    fun register(username: String, password: String): AuthSession {
        postJson("/auth/register", credentialsJson(username, password), token = null)
        return login(username, password)
    }

    fun login(username: String, password: String): AuthSession {
        val response = postJson("/auth/login", credentialsJson(username, password), token = null)
        return AuthSession(
            token = response.getString("token"),
            username = response.getString("username"),
            userId = response.getString("userId")
        )
    }

    fun sendMessage(token: String, pkg: EncryptedMessagePackage): JSONObject {
        val body = JSONObject().apply {
            put("message_id", pkg.messageId)
            put("receiver_uuid", pkg.receiverUuid)
            put("counter", pkg.counter)
            put("nonce_base64", pkg.nonceBase64)
            put("encrypted_payload", pkg.ciphertextBase64)
            put("encrypted_metadata", pkg.encryptedMetadataBase64)
            put("hmac_base64", pkg.hmacBase64)
            put("key_version", pkg.keyVersion)
        }
        return postJson("/messages", body, token)
    }

    fun fetchMessages(token: String, contactId: String): List<ServerMessageRow> {
        val response = getJson("/messages?with=${urlEncode(contactId)}", token)
        val rows = response.optJSONArray("rows") ?: JSONArray()
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ServerMessageRow(
                messageId = row.getString("message_id"),
                senderUuid = row.getString("sender_uuid"),
                receiverUuid = row.getString("receiver_uuid"),
                counter = row.getLong("counter"),
                nonceBase64 = row.getString("nonce_base64"),
                encryptedPayload = row.getString("encrypted_payload"),
                encryptedMetadata = row.optString("encrypted_metadata").takeIf { it.isNotBlank() && it != "null" },
                hmacBase64 = row.getString("hmac_base64"),
                keyVersion = row.optInt("key_version", 1),
                createdAt = row.optString("created_at").takeIf { it.isNotBlank() }
            )
        }
    }

    /** Uploads a raw encrypted file blob. Metadata and original filename must already be encrypted. */
    fun uploadEncryptedBlob(token: String, path: String, headers: Map<String, String>, cipherBytes: ByteArray): String {
        val conn = open(path, "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
        conn.outputStream.use { it.write(cipherBytes) }
        return readResponse(conn)
    }

    private fun credentialsJson(username: String, password: String): JSONObject =
        JSONObject().apply {
            put("username", username)
            put("password", password)
        }

    private fun postJson(path: String, body: JSONObject, token: String?): JSONObject {
        val conn = open(path, "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        return JSONObject(readResponse(conn))
    }

    private fun getJson(path: String, token: String): JSONObject {
        val conn = open(path, "GET", token)
        return JSONObject(readResponse(conn))
    }

    private fun open(path: String, method: String, token: String?): HttpsURLConnection {
        val conn = URL(baseUrl + path).openConnection() as HttpsURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (conn.responseCode !in 200..299) {
            val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Server error ${conn.responseCode}")
        }
        return body
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
