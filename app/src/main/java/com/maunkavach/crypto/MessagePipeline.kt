package com.maunkavach.crypto

import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Implements spec section 2 exactly:
 *
 *   Plaintext → UTF-8 → substitution(table) → rotation(rule, advances per byte)
 *   → derive ephemeral key(masterKey, counter, nonce, direction, "msg-v1")
 *   → AES-256-GCM encrypt → attach nonce/counter/message_id/encrypted_metadata
 *   → HMAC-SHA256(ciphertext + nonce + counter + metadata) → send package
 *
 * Receiver:
 *   verify HMAC → reject on failure → reject duplicate message_id/stale counter
 *   → AES-256-GCM decrypt → reverse rotation → reverse substitution → UTF-8 decode
 */
object MessagePipeline {

    private const val CONTEXT_LABEL = "msg-v1"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    sealed class DecryptResult {
        data class Success(val plaintext: String) : DecryptResult()
        object HmacVerificationFailed : DecryptResult()
        object ReplayRejected : DecryptResult()
        object DecryptionFailed : DecryptResult()
    }

    fun encryptMessage(
        plainText: String,
        contact: ContactKeyBundle,
        senderUuid: String,
        receiverUuid: String,
        sendCounter: Long,
        encryptedMetadata: ByteArray? = null,
        padToBucket: Boolean = true
    ): EncryptedMessagePackage {
        val utf8 = plainText.toByteArray(Charsets.UTF_8)
        val padded = if (padToBucket) {
            PaddingUtil.pad(utf8, PaddingUtil.nextBucket(utf8.size + 4, PaddingUtil.MESSAGE_BUCKETS))
        } else utf8

        val substituted = contact.substitutionTable().substitute(padded)
        val rotated = contact.rotationCipher().rotate(substituted)

        val nonce = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val ephemeralKey = EphemeralKeyDerivation.derive(
            contact.masterKey().encoded, sendCounter, nonce, DirectionFlag.SENT, CONTEXT_LABEL
        )

        val cipher = Cipher.getInstance(CryptoManager.AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, ephemeralKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(rotated)

        val counterBytes = longToBytes(sendCounter)
        val metadataBytes = encryptedMetadata ?: ByteArray(0)
        val tag = HmacSigner.sign(contact.hmacKey(), ciphertext, nonce, counterBytes, metadataBytes)

        return EncryptedMessagePackage(
            messageId = UUID.randomUUID().toString(),
            senderUuid = senderUuid,
            receiverUuid = receiverUuid,
            counter = sendCounter,
            timestampMillis = System.currentTimeMillis(),
            nonceBase64 = b64(nonce),
            ciphertextBase64 = b64(ciphertext),
            encryptedMetadataBase64 = encryptedMetadata?.let { b64(it) },
            hmacBase64 = b64(tag),
            keyVersion = contact.keyVersion
        )
    }

    fun decryptMessage(
        pkg: EncryptedMessagePackage,
        contact: ContactKeyBundle,
        replayProtection: ReplayProtection
    ): DecryptResult {
        val nonce = b64decode(pkg.nonceBase64)
        val ciphertext = b64decode(pkg.ciphertextBase64)
        val metadataBytes = pkg.encryptedMetadataBase64?.let { b64decode(it) } ?: ByteArray(0)
        val counterBytes = longToBytes(pkg.counter)

        // 1) Verify HMAC FIRST — never attempt decryption on unauthenticated ciphertext.
        val tag = b64decode(pkg.hmacBase64)
        val valid = HmacSigner.verify(contact.hmacKey(), tag, ciphertext, nonce, counterBytes, metadataBytes)
        if (!valid) return DecryptResult.HmacVerificationFailed

        // 2) Replay protection — duplicate message_id or stale/replayed counter.
        val replayCheck = replayProtection.checkAndRecord(pkg.senderUuid, pkg.messageId, pkg.counter)
        if (replayCheck != ReplayProtection.Result.Accepted) return DecryptResult.ReplayRejected

        return try {
            // The package was encrypted with the sender-side message direction. The receiver
            // must derive the same wire key; using local RECEIVED here creates a different AES key.
            val ephemeralKey = EphemeralKeyDerivation.derive(
                contact.masterKey().encoded, pkg.counter, nonce, DirectionFlag.SENT, CONTEXT_LABEL
            )
            val cipher = Cipher.getInstance(CryptoManager.AES_MODE)
            cipher.init(Cipher.DECRYPT_MODE, ephemeralKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
            val rotated = cipher.doFinal(ciphertext)

            val substituted = contact.rotationCipher().reverseRotate(rotated)
            val padded = contact.substitutionTable().reverseSubstitute(substituted)
            val utf8 = PaddingUtil.unpad(padded)

            DecryptResult.Success(String(utf8, Charsets.UTF_8))
        } catch (e: Exception) {
            DecryptResult.DecryptionFailed
        }
    }

    private fun longToBytes(x: Long): ByteArray {
        val b = ByteArray(8)
        var v = x
        for (i in 7 downTo 0) { b[i] = (v and 0xFF).toByte(); v = v ushr 8 }
        return b
    }

    private fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
    private fun b64decode(s: String) = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
}
