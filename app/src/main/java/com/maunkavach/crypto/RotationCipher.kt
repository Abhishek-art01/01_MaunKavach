package com.maunkavach.crypto

/**
 * A per-contact 999-step rotation rule: 999 integers (0-255, repeats allowed), each reduced
 * mod 8 to give a bit-rotation amount for one byte. The rule index advances by one for every
 * byte processed and wraps after 999 bytes, so a long message keeps cycling through the full
 * rule rather than repeating after only a few bytes. Like SubstitutionTable, this sits below
 * AES-256-GCM as defense-in-depth, not as the primary cryptographic guarantee.
 */
class RotationCipher private constructor(private val rule: IntArray) {

    fun rotate(data: ByteArray): ByteArray = transform(data, forward = true)
    fun reverseRotate(data: ByteArray): ByteArray = transform(data, forward = false)

    private fun transform(data: ByteArray, forward: Boolean): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) {
            val shift = rule[i % rule.size] % 8
            val b = data[i].toInt() and 0xFF
            val r = if (forward) {
                ((b shl shift) or (b ushr (8 - shift))) and 0xFF
            } else {
                ((b ushr shift) or (b shl (8 - shift))) and 0xFF
            }
            out[i] = r.toByte()
        }
        return out
    }

    fun serialize(): String = rule.joinToString(",")

    companion object {
        const val RULE_LENGTH = 999

        /** Validates per spec: exactly 999 numbers, each 0-255. Repeats are explicitly allowed. */
        fun validate(rule: IntArray): ValidationResult {
            if (rule.size != RULE_LENGTH) return ValidationResult.Invalid("Rotation rule must contain exactly $RULE_LENGTH numbers, got ${rule.size}")
            for (v in rule) {
                if (v < 0 || v > 255) return ValidationResult.Invalid("Rotation number $v out of range 0-255")
            }
            return ValidationResult.Valid
        }

        fun fromValidated(rule: IntArray): RotationCipher {
            val result = validate(rule)
            if (result is ValidationResult.Invalid) throw IllegalArgumentException(result.reason)
            return RotationCipher(rule)
        }

        fun deserialize(s: String): RotationCipher =
            fromValidated(s.split(",").map { it.trim().toInt() }.toIntArray())

        fun generateRandom(): RotationCipher {
            val random = java.security.SecureRandom()
            val rule = IntArray(RULE_LENGTH) { random.nextInt(256) }
            return RotationCipher(rule)
        }
    }
}
