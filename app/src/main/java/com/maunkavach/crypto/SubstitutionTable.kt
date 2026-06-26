package com.maunkavach.crypto

/**
 * A per-contact 256-byte substitution table: a permutation of 0..255 used as a simple
 * byte-for-byte substitution cipher stage. This is NOT a cryptographic primitive on its own
 * (a monoalphabetic substitution is trivially breakable via frequency analysis on enough
 * data) — it is layered *underneath* AES-256-GCM per the spec, as an extra keyed transform
 * an attacker would have to also reverse even in a (hypothetical) AES-key-compromise
 * scenario. The real security guarantee remains AES-256-GCM + HMAC-SHA256.
 */
class SubstitutionTable private constructor(val forward: IntArray) {

    val inverse: IntArray by lazy {
        IntArray(256).also { inv -> forward.forEachIndexed { i, v -> inv[v] = i } }
    }

    fun substitute(data: ByteArray): ByteArray =
        ByteArray(data.size) { i -> forward[data[i].toInt() and 0xFF].toByte() }

    fun reverseSubstitute(data: ByteArray): ByteArray =
        ByteArray(data.size) { i -> inverse[data[i].toInt() and 0xFF].toByte() }

    fun serialize(): String = forward.joinToString(",")

    companion object {
        /** Validates per spec: must be exactly 256 entries, each 0-255, no duplicates, no missing values. */
        fun validate(table: IntArray): ValidationResult {
            if (table.size != 256) return ValidationResult.Invalid("Table must contain exactly 256 entries, got ${table.size}")
            val seen = BooleanArray(256)
            for (v in table) {
                if (v < 0 || v > 255) return ValidationResult.Invalid("Value $v out of range 0-255")
                if (seen[v]) return ValidationResult.Invalid("Duplicate output value: $v")
                seen[v] = true
            }
            val missing = seen.indices.filter { !seen[it] }
            if (missing.isNotEmpty()) return ValidationResult.Invalid("Missing values: ${missing.take(5)}${if (missing.size > 5) "…" else ""}")
            return ValidationResult.Valid
        }

        fun fromValidated(table: IntArray): SubstitutionTable {
            val result = validate(table)
            if (result is ValidationResult.Invalid) throw IllegalArgumentException(result.reason)
            return SubstitutionTable(table)
        }

        fun deserialize(s: String): SubstitutionTable =
            fromValidated(s.split(",").map { it.trim().toInt() }.toIntArray())

        /** Auto-generate: a cryptographically random permutation via Fisher-Yates using SecureRandom. */
        fun generateRandom(): SubstitutionTable {
            val table = IntArray(256) { it }
            val random = java.security.SecureRandom()
            for (i in 255 downTo 1) {
                val j = random.nextInt(i + 1)
                val tmp = table[i]; table[i] = table[j]; table[j] = tmp
            }
            return SubstitutionTable(table)
        }
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}
