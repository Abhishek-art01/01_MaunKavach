package com.maunkavach.network

import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Pure java.net.HttpsURLConnection — no Retrofit/OkHttp per the native-only spec. Certificate
 * pinning is enforced at the OS level via res/xml/network_security_config.xml, not in this
 * class, so this stays a thin wrapper.
 *
 * Every body sent/received here is already ciphertext (EncryptedBlob.serialize() strings,
 * or JSON wrapping those strings) — this class has no knowledge of plaintext or keys.
 */
object ApiClient {

    var baseUrl: String = "https://your-maunkavach-server.example"

    fun postEncrypted(path: String, jsonBody: String): String {
        val url = URL(baseUrl + path)
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    fun getEncrypted(path: String): String {
        val url = URL(baseUrl + path)
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /** Uploads a raw encrypted file blob (already AES-GCM ciphertext bytes) under an opaque random name. */
    fun uploadEncryptedBlob(path: String, opaqueFileName: String, cipherBytes: ByteArray): String {
        val url = URL(baseUrl + path)
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("X-Blob-Name", opaqueFileName) // random UUID, never original filename
        conn.outputStream.use { it.write(cipherBytes) }
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
