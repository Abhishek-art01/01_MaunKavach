package com.maunkavach.crypto

/**
 * Pads plaintext (pre-encryption) up to the nearest size bucket, so the encrypted blob's
 * length doesn't leak the exact original length to a network observer or the server.
 */
object PaddingUtil {

    // Message buckets: small text messages get padded to coarse buckets.
    val MESSAGE_BUCKETS = intArrayOf(64, 256, 1024, 4096, 16384)

    // File buckets: coarser buckets up to common file sizes.
    val FILE_BUCKETS = longArrayOf(
        16 * 1024, 64 * 1024, 256 * 1024, 1024 * 1024, 4L * 1024 * 1024,
        16L * 1024 * 1024, 64L * 1024 * 1024, 256L * 1024 * 1024
    )

    fun nextBucket(size: Int, buckets: IntArray): Int =
        buckets.firstOrNull { it >= size } ?: run {
            val largest = buckets.last()
            ((size + largest - 1) / largest) * largest
        }

    fun nextFileBucket(size: Long): Long =
        FILE_BUCKETS.firstOrNull { it >= size } ?: run {
            val largest = FILE_BUCKETS.last()
            ((size + largest - 1) / largest) * largest
        }

    /** Pads [data] to [targetSize] with a 4-byte big-endian original-length prefix, then random filler bytes. */
    fun pad(data: ByteArray, targetSize: Int): ByteArray {
        require(targetSize >= data.size + 4) { "target size too small to hold length prefix + data" }
        val out = ByteArray(targetSize)
        java.security.SecureRandom().nextBytes(out) // fill everything with randomness first
        out[0] = (data.size ushr 24).toByte()
        out[1] = (data.size ushr 16).toByte()
        out[2] = (data.size ushr 8).toByte()
        out[3] = data.size.toByte()
        System.arraycopy(data, 0, out, 4, data.size)
        return out
    }

    fun unpad(padded: ByteArray): ByteArray {
        val originalLength = ((padded[0].toInt() and 0xFF) shl 24) or
            ((padded[1].toInt() and 0xFF) shl 16) or
            ((padded[2].toInt() and 0xFF) shl 8) or
            (padded[3].toInt() and 0xFF)
        require(originalLength in 0..(padded.size - 4)) { "corrupt padding length prefix" }
        return padded.copyOfRange(4, 4 + originalLength)
    }
}
