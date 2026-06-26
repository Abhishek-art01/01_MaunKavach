package com.maunkavach.crypto

import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

enum class DirectionFlag(val byteValue: Byte) { SENT(1), RECEIVED(2) }

/**
 * Per-message ephemeral key derivation, giving each message/file its own one-time AES key
 * derived from the contact's long-lived master key plus per-message context — never the
 * master key itself used directly for AES.
 *
 * Forward secrecy property: deriving a future ephemeral key from the master key + counter
 * does NOT let you recover *past* ephemeral keys (SHA-256 is one-way), so compromising "the
 * current state" doesn't retroactively decrypt earlier messages. What it does NOT give you
 * is the stronger guarantee of a real Double-Ratchet (where the master key itself evolves
 * forward so a master-key compromise wouldn't even expose *future* messages) — here the
 * contact master key is long-lived and rotated only via explicit Vault Key regeneration. If
 * you need the stronger guarantee, layer an actual DH ratchet on top of this; documenting the
 * gap here rather than overstating what this derivation provides.
 *
 * Nothing here is persisted — call sites derive, use once for AES, then let the key go out of
 * scope. Only the counter (ratchet state) is persisted per contact.
 */
object EphemeralKeyDerivation {

    fun derive(
        masterKey: ByteArray,
        counter: Long,
        nonce: ByteArray,
        direction: DirectionFlag,
        contextLabel: String
    ): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(masterKey)
        digest.update(longToBytes(counter))
        digest.update(nonce)
        digest.update(direction.byteValue)
        digest.update(contextLabel.toByteArray(Charsets.UTF_8))
        val keyBytes = digest.digest() // 32 bytes -> AES-256
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun longToBytes(x: Long): ByteArray {
        val b = ByteArray(8)
        var v = x
        for (i in 7 downTo 0) { b[i] = (v and 0xFF).toByte(); v = v ushr 8 }
        return b
    }
}

/** Per-contact ratchet state — only the counter persists; ephemeral keys are never stored. */
data class RatchetState(val contactId: String, var sendCounter: Long, var receiveCounter: Long)
