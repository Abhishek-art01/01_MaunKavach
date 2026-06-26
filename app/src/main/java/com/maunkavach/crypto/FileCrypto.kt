package com.maunkavach.crypto

import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Everything the spec's section 3 + 6 says the server is allowed to see for a file. */
data class EncryptedFilePackage(
    val fileId: String,
    val senderUuid: String,
    val receiverUuid: String,
    val encryptedBlobLocalPath: String,   // local path before upload; server gets random opaque name only
    val wrappedFileKeyBase64: String,      // per-file AES key, wrapped under contact master key
    val nonceBase64: String,
    val encryptedMetadataBase64: String,   // filename + mime + size-category, all encrypted together
    val hmacBase64: String,
    val paddedSizeBucket: Long,
    val keyVersion: Int
)

/**
 * Implements spec section 3 exactly:
 *
 *   read file bytes → encrypt filename/MIME/size-category/metadata → substitution + rotation
 *   on file bytes → generate random per-file AES-256-GCM key → encrypt file bytes
 *   → encrypt per-file key under contact master key → HMAC-SHA256 over the whole package
 *   → upload only the encrypted blob (+ package) to server/storage
 *
 * Receiver: download blob → verify HMAC → reject on failure → decrypt file key locally →
 * decrypt file locally → open only in the in-app secure viewer, never exported to
 * Downloads/Gallery automatically.
 */
object FileCrypto {

    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    fun encryptFile(
        sourceFile: File,
        originalName: String,
        mimeType: String,
        contact: ContactKeyBundle,
        senderUuid: String,
        receiverUuid: String,
        outputDir: File
    ): EncryptedFilePackage {
        val plainBytes = sourceFile.readBytes()
        val random = SecureRandom()

        // 1) Substitution + rotation on the raw file bytes (defense-in-depth, below AES).
        val substituted = contact.substitutionTable().substitute(plainBytes)
        val rotated = contact.rotationCipher().rotate(substituted)

        // 2) Generate a random one-time AES-256 key for this file only.
        val fileKeyBytes = ByteArray(32).also { random.nextBytes(it) }
        val fileKey = SecretKeySpec(fileKeyBytes, "AES")
        val fileNonce = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }

        val fileCipher = Cipher.getInstance(CryptoManager.AES_MODE)
        fileCipher.init(Cipher.ENCRYPT_MODE, fileKey, GCMParameterSpec(GCM_TAG_BITS, fileNonce))
        val fileCiphertext = fileCipher.doFinal(rotated)

        // 3) Wrap the one-time file key under the contact's master key (envelope encryption).
        val wrapNonce = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val wrapCipher = Cipher.getInstance(CryptoManager.AES_MODE)
        wrapCipher.init(Cipher.ENCRYPT_MODE, contact.masterKey(), GCMParameterSpec(GCM_TAG_BITS, wrapNonce))
        val wrappedFileKey = wrapCipher.doFinal(fileKeyBytes)
        // store nonce alongside ciphertext for the wrap, "nonce.ciphertext" wire format
        val wrappedFileKeyBase64 = b64(wrapNonce) + "." + b64(wrappedFileKey)

        // 4) Encrypt filename + mime + size-category + bucket together as one metadata blob.
        val sizeCategory = sizeCategoryOf(plainBytes.size.toLong())
        val paddedBucket = PaddingUtil.nextFileBucket(plainBytes.size.toLong())
        val metadataJson = org.json.JSONObject().apply {
            put("name", originalName)
            put("mime", mimeType)
            put("sizeCategory", sizeCategory)
        }.toString()
        val metadataNonce = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val metaCipher = Cipher.getInstance(CryptoManager.AES_MODE)
        metaCipher.init(Cipher.ENCRYPT_MODE, contact.masterKey(), GCMParameterSpec(GCM_TAG_BITS, metadataNonce))
        val metadataCiphertext = metaCipher.doFinal(metadataJson.toByteArray(Charsets.UTF_8))
        val encryptedMetadataBase64 = b64(metadataNonce) + "." + b64(metadataCiphertext)

        // 5) HMAC over the whole package so the receiver can detect any tampering before decrypting.
        val tag = HmacSigner.sign(
            contact.hmacKey(),
            fileCiphertext, fileNonce, wrappedFileKey, metadataCiphertext, longToBytes(paddedBucket)
        )

        // 6) Persist ciphertext under a random opaque local filename; this is what gets uploaded.
        if (!outputDir.exists()) outputDir.mkdirs()
        val opaqueName = UUID.randomUUID().toString() + ".bin"
        val outFile = File(outputDir, opaqueName)
        outFile.writeBytes(fileNonce + fileCiphertext) // nonce prefix kept with blob for this file's own AES layer

        return EncryptedFilePackage(
            fileId = UUID.randomUUID().toString(),
            senderUuid = senderUuid,
            receiverUuid = receiverUuid,
            encryptedBlobLocalPath = outFile.absolutePath,
            wrappedFileKeyBase64 = wrappedFileKeyBase64,
            nonceBase64 = b64(fileNonce),
            encryptedMetadataBase64 = encryptedMetadataBase64,
            hmacBase64 = b64(tag),
            paddedSizeBucket = paddedBucket,
            keyVersion = contact.keyVersion
        )
    }

    sealed class DecryptResult {
        data class Success(val plainBytes: ByteArray, val originalName: String, val mimeType: String) : DecryptResult()
        object HmacVerificationFailed : DecryptResult()
        object DecryptionFailed : DecryptResult()
    }

    /** Runs only on the receiver's device, after Vault Key unlock and HMAC verification. */
    fun decryptFile(pkg: EncryptedFilePackage, encryptedBlobBytes: ByteArray, contact: ContactKeyBundle): DecryptResult {
        return try {
            val fileNonce = b64decode(pkg.nonceBase64)
            val fileCiphertext = encryptedBlobBytes.copyOfRange(fileNonce.size, encryptedBlobBytes.size)

            val (wrapNonceB64, wrappedKeyB64) = pkg.wrappedFileKeyBase64.split(".", limit = 2)
            val wrapNonce = b64decode(wrapNonceB64)
            val wrappedKey = b64decode(wrappedKeyB64)

            val (metaNonceB64, metaCtB64) = pkg.encryptedMetadataBase64.split(".", limit = 2)
            val metaNonce = b64decode(metaNonceB64)
            val metaCiphertext = b64decode(metaCtB64)

            // 1) Verify HMAC FIRST.
            val tag = b64decode(pkg.hmacBase64)
            val valid = HmacSigner.verify(
                contact.hmacKey(), tag,
                fileCiphertext, fileNonce, wrappedKey, metaCiphertext, longToBytes(pkg.paddedSizeBucket)
            )
            if (!valid) return DecryptResult.HmacVerificationFailed

            // 2) Unwrap the file key.
            val unwrapCipher = Cipher.getInstance(CryptoManager.AES_MODE)
            unwrapCipher.init(Cipher.DECRYPT_MODE, contact.masterKey(), GCMParameterSpec(GCM_TAG_BITS, wrapNonce))
            val fileKeyBytes = unwrapCipher.doFinal(wrappedKey)
            val fileKey = SecretKeySpec(fileKeyBytes, "AES")

            // 3) Decrypt the file body, then reverse rotation + substitution.
            val fileCipher = Cipher.getInstance(CryptoManager.AES_MODE)
            fileCipher.init(Cipher.DECRYPT_MODE, fileKey, GCMParameterSpec(GCM_TAG_BITS, fileNonce))
            val rotated = fileCipher.doFinal(fileCiphertext)
            val substituted = contact.rotationCipher().reverseRotate(rotated)
            val plainBytes = contact.substitutionTable().reverseSubstitute(substituted)

            // 4) Decrypt metadata.
            val metaCipher = Cipher.getInstance(CryptoManager.AES_MODE)
            metaCipher.init(Cipher.DECRYPT_MODE, contact.masterKey(), GCMParameterSpec(GCM_TAG_BITS, metaNonce))
            val metadataJson = String(metaCipher.doFinal(metaCiphertext), Charsets.UTF_8)
            val metaObj = org.json.JSONObject(metadataJson)
            val name = metaObj.optString("name", "file")
            val mime = metaObj.optString("mime", "application/octet-stream")

            DecryptResult.Success(plainBytes, name, mime)
        } catch (e: Exception) {
            DecryptResult.DecryptionFailed
        }
    }

    private fun sizeCategoryOf(bytes: Long): String = when {
        bytes < 100 * 1024 -> "small"
        bytes < 5L * 1024 * 1024 -> "medium"
        bytes < 50L * 1024 * 1024 -> "large"
        else -> "very_large"
    }

    private fun longToBytes(x: Long): ByteArray {
        val b = ByteArray(8); var v = x
        for (i in 7 downTo 0) { b[i] = (v and 0xFF).toByte(); v = v ushr 8 }
        return b
    }
    private fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
    private fun b64decode(s: String) = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
}
