package com.maunkavach.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacSigner {
    private const val ALGO = "HmacSHA256"

    fun sign(hmacKey: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGO)
        mac.init(SecretKeySpec(hmacKey, ALGO))
        parts.forEach { mac.update(it) }
        return mac.doFinal()
    }

    /** Constant-time comparison — never use `==`/`Arrays.equals` for tag comparison (timing side-channel). */
    fun verify(hmacKey: ByteArray, expectedTag: ByteArray, vararg parts: ByteArray): Boolean {
        val computed = sign(hmacKey, *parts)
        return constantTimeEquals(computed, expectedTag)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
