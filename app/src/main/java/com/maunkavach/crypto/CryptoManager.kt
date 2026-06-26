package com.maunkavach.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * All raw crypto primitives live here. Nothing above this layer ever touches a key's raw
 * bytes for the *master* key — that key never leaves the hardware-backed Android Keystore.
 * Per-contact keys (managed by [com.maunkavach.crypto.VaultKeyManager]) are themselves wrapped
 * (encrypted) by this master key before being persisted to the local encrypted DB.
 */
object CryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "maunkavach_master_key"
    const val AES_MODE = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
    const val GCM_IV_BYTES = 12

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /** Returns the hardware-backed master key, generating it on first run. Never exported. */
    fun getOrCreateMasterKey(requireBiometric: Boolean = true): SecretKey {
        (keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(requireBiometric)
            .apply {
                if (requireBiometric) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            0, // 0 = require auth on every use (no grace timeout)
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }
            }
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    /** Generates a fresh random AES-256 key entirely in software (for per-contact / per-file keys). */
    fun generateRandomAesKey(): SecretKey {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return SecretKeySpec(bytes, "AES")
    }

    fun randomIv(): ByteArray = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }

    /** Encrypts [plaintext] under [key]; output format = iv || ciphertext || tag (tag is appended by GCM). */
    fun encrypt(plaintext: ByteArray, key: SecretKey, aad: ByteArray? = null): EncryptedBlob {
        val iv = randomIv()
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedBlob(iv = iv, ciphertext = ciphertext)
    }

    fun decrypt(blob: EncryptedBlob, key: SecretKey, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(blob.ciphertext)
    }

    /** SHA-256 fingerprint of a key's raw bytes — shown in the UI instead of the full key. */
    fun fingerprint(key: SecretKey): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.encoded)
        return hash.joinToString(":") { "%02x".format(it) }.take(47) // first ~8 bytes, colon-grouped
    }
}

data class EncryptedBlob(val iv: ByteArray, val ciphertext: ByteArray) {
    /** Wire/storage format: base64(iv) + "." + base64(ciphertext). Server only ever sees this. */
    fun serialize(): String =
        android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP) + "." +
            android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)

    companion object {
        fun deserialize(s: String): EncryptedBlob {
            val (ivB64, ctB64) = s.split(".", limit = 2)
            return EncryptedBlob(
                iv = android.util.Base64.decode(ivB64, android.util.Base64.NO_WRAP),
                ciphertext = android.util.Base64.decode(ctB64, android.util.Base64.NO_WRAP)
            )
        }
    }
}
