package com.maunkavach.network

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

/**
 * A minimal native WebSocket client built directly on java.net sockets + the RFC6455
 * handshake, since the spec disallows third-party networking/WS libraries (no OkHttp WS, no
 * Java-WebSocket, no Socket.IO). It handles the small encrypted JSON frames used by the
 * backend push channel; large binary payloads are transferred through the HTTPS file API.
 *
 * As with [ApiClient], every payload pushed through here is pre-encrypted ciphertext —
 * this class is transport-only and never touches plaintext or keys.
 */
class NativeWebSocketClient(
    private val host: String,
    private val port: Int,
    private val path: String,
    private val useTls: Boolean = true
) {

    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var reader: BufferedReader? = null
    @Volatile var isConnected: Boolean = false
        private set

    fun connect(onMessage: (String) -> Unit) {
        val raw = if (useTls) SSLSocketFactory.getDefault().createSocket(host, port) else Socket(host, port)
        socket = raw
        output = raw.getOutputStream()
        reader = BufferedReader(InputStreamReader(raw.getInputStream()))

        val key = Base64.getEncoder().encodeToString(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) })
        val handshake = buildString {
            append("GET $path HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $key\r\n")
            append("Sec-WebSocket-Version: 13\r\n\r\n")
        }
        output!!.write(handshake.toByteArray(Charsets.UTF_8))
        output!!.flush()

        val statusLine = reader!!.readLine()
        if (statusLine?.contains("101") != true) {
            close()
            throw IllegalStateException("Realtime connection rejected.")
        }

        // Consume HTTP 101 response headers.
        var line: String?
        do { line = reader!!.readLine() } while (line != null && line.isNotEmpty())

        isConnected = true
        Thread {
            try {
                while (isConnected) {
                    val frame = readFrame() ?: break
                    onMessage(frame)
                }
            } catch (_: Exception) {
                isConnected = false
            }
        }.start()
    }

    /** Sends an already-encrypted text frame (typically a JSON envelope around EncryptedBlob strings). */
    fun send(encryptedJsonPayload: String) {
        val payload = encryptedJsonPayload.toByteArray(Charsets.UTF_8)
        val out = output ?: return
        val maskKey = ByteArray(4).also { java.security.SecureRandom().nextBytes(it) }
        val masked = ByteArray(payload.size) { i -> (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte() }

        val header = mutableListOf<Byte>()
        header.add(0x81.toByte()) // FIN + text frame opcode
        when {
            payload.size <= 125 -> header.add((payload.size or 0x80).toByte())
            payload.size <= 65535 -> {
                header.add((126 or 0x80).toByte())
                header.add(((payload.size shr 8) and 0xFF).toByte())
                header.add((payload.size and 0xFF).toByte())
            }
            else -> throw IllegalArgumentException("Payload too large for this minimal client")
        }
        header.addAll(maskKey.toList())

        out.write(header.toByteArray())
        out.write(masked)
        out.flush()
    }

    private fun readFrame(): String? {
        val input = socket?.getInputStream() ?: return null
        val b0 = input.read()
        if (b0 == -1) return null
        val b1 = input.read()
        var len = b1 and 0x7F
        if (len == 126) {
            len = (input.read() shl 8) or input.read()
        } else if (len == 127) {
            var longLen = 0L
            repeat(8) {
                val next = input.read()
                if (next == -1) return null
                longLen = (longLen shl 8) or next.toLong()
            }
            if (longLen > Int.MAX_VALUE) throw IllegalArgumentException("WebSocket frame too large")
            len = longLen.toInt()
        }
        val payload = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(payload, read, len - read)
            if (n == -1) break
            read += n
        }
        return String(payload, Charsets.UTF_8)
    }

    fun close() {
        isConnected = false
        runCatching { socket?.close() }
    }

    private fun sha1Base64(s: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}
