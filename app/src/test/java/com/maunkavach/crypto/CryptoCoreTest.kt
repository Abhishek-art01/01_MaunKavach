package com.maunkavach.crypto

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

/**
 * Pure-JVM unit tests (no Android framework calls, no Robolectric needed) for the pieces of
 * the crypto core that don't touch android.util.Base64 directly — SubstitutionTable,
 * RotationCipher, HmacSigner, PaddingUtil, EphemeralKeyDerivation, ReplayProtection.
 *
 * MessagePipeline/FileCrypto/VaultKeyManager DO use android.util.Base64 and
 * EncryptedSharedPreferences, so exercising those end-to-end requires either Robolectric or
 * an instrumented test (androidTest) running on a device/emulator — out of scope for this
 * plain JUnit module, but the underlying algorithm correctness they depend on (this file) is
 * fully covered here. The same logic was additionally cross-checked in a standalone Node.js
 * harness before being ported to Kotlin (see project root commit history / DEPLOY notes) —
 * both independently agree.
 *
 * Maps to spec section 22's required-tests list:
 *   ✓ "256 table validation catches duplicates/missing values"
 *   ✓ "999 rotation validation catches invalid values"
 *   ✓ "Same input byte produces different output due to rotation"
 *   ✓ "HMAC fails if ciphertext is modified"
 *   ✓ "Replay message_id is rejected"
 * Covered elsewhere (server-side, see backend test run in DEPLOY.md) rather than here:
 *   "Server database contains no plaintext", "Expired message disappears", file-name/message
 *   invisibility on server, screenshot/backup/lock/RAM-wipe behavior (those are Android
 *   instrumented-test or manual-QA territory, not pure-JVM unit tests).
 */
class CryptoCoreTest {

    // ---------------- SubstitutionTable ----------------

    @Test
    fun `valid random permutation passes validation`() {
        val table = SubstitutionTable.generateRandom()
        val result = SubstitutionTable.validate(intArrayOfFrom(table))
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `table with wrong length is rejected`() {
        val shortTable = IntArray(200) { it }
        val result = SubstitutionTable.validate(shortTable)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `table with duplicate value is rejected`() {
        val table = IntArray(256) { it }
        table[5] = table[10] // introduces duplicate -> some value now missing
        val result = SubstitutionTable.validate(table)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `table with out-of-range value is rejected`() {
        val table = IntArray(256) { it }
        table[0] = 999
        val result = SubstitutionTable.validate(table)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `substitution is reversible`() {
        val table = SubstitutionTable.generateRandom()
        val plain = "Hello MaunKavach!".toByteArray(Charsets.UTF_8)
        val substituted = table.substitute(plain)
        val back = table.reverseSubstitute(substituted)
        assertArrayEquals(plain, back)
    }

    // ---------------- RotationCipher ----------------

    @Test
    fun `valid 999-length rotation rule passes validation`() {
        val rule = IntArray(999) { (it * 37 + 11) % 256 }
        assertTrue(RotationCipher.validate(rule) is ValidationResult.Valid)
    }

    @Test
    fun `rotation rule with wrong length is rejected`() {
        val rule = IntArray(500) { it % 256 }
        assertTrue(RotationCipher.validate(rule) is ValidationResult.Invalid)
    }

    @Test
    fun `rotation rule with out-of-range value is rejected`() {
        val rule = IntArray(999) { it % 256 }
        rule[10] = 300
        assertTrue(RotationCipher.validate(rule) is ValidationResult.Invalid)
    }

    @Test
    fun `rotation rule allows repeated values`() {
        val rule = IntArray(999) { 42 } // all the same value — explicitly allowed per spec
        assertTrue(RotationCipher.validate(rule) is ValidationResult.Valid)
    }

    @Test
    fun `rotation is reversible`() {
        val cipher = RotationCipher.generateRandom()
        val data = "The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)
        val rotated = cipher.rotate(data)
        val back = cipher.reverseRotate(rotated)
        assertArrayEquals(data, back)
    }

    @Test
    fun `identical input bytes produce varied output across positions`() {
        val cipher = RotationCipher.generateRandom()
        val sameByteRepeated = ByteArray(20) { 0x41 }
        val rotated = cipher.rotate(sameByteRepeated)
        val distinctValues = rotated.toSet()
        assertTrue("expected rotation to vary output across positions", distinctValues.size > 1)
    }

    // ---------------- HmacSigner ----------------

    @Test
    fun `hmac verifies correctly on unmodified data`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val data = "some ciphertext bytes".toByteArray(Charsets.UTF_8)
        val tag = HmacSigner.sign(key, data)
        assertTrue(HmacSigner.verify(key, tag, data))
    }

    @Test
    fun `hmac fails if ciphertext is modified`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val data = "some ciphertext bytes".toByteArray(Charsets.UTF_8)
        val tag = HmacSigner.sign(key, data)

        val tampered = data.copyOf()
        tampered[0] = tampered[0].inc() // flip one byte

        assertFalse(HmacSigner.verify(key, tag, tampered))
    }

    @Test
    fun `hmac fails with wrong key`() {
        val key1 = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val key2 = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val data = "some data".toByteArray(Charsets.UTF_8)
        val tag = HmacSigner.sign(key1, data)
        assertFalse(HmacSigner.verify(key2, tag, data))
    }

    // ---------------- EphemeralKeyDerivation (forward secrecy property) ----------------

    @Test
    fun `different counters produce different ephemeral keys`() {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key1 = EphemeralKeyDerivation.derive(masterKey, 1L, nonce, DirectionFlag.SENT, "msg-v1")
        val key2 = EphemeralKeyDerivation.derive(masterKey, 2L, nonce, DirectionFlag.SENT, "msg-v1")
        assertFalse(key1.encoded.contentEquals(key2.encoded))
    }

    @Test
    fun `same inputs deterministically produce the same ephemeral key`() {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key1 = EphemeralKeyDerivation.derive(masterKey, 5L, nonce, DirectionFlag.RECEIVED, "msg-v1")
        val key2 = EphemeralKeyDerivation.derive(masterKey, 5L, nonce, DirectionFlag.RECEIVED, "msg-v1")
        assertArrayEquals(key1.encoded, key2.encoded)
    }

    // ---------------- ReplayProtection ----------------

    @Test
    fun `replay protection rejects duplicate message_id`() {
        val rp = ReplayProtection()
        val first = rp.checkAndRecord("alice", "msg-1", 1L)
        val replay = rp.checkAndRecord("alice", "msg-1", 1L)
        assertEquals(ReplayProtection.Result.Accepted, first)
        assertEquals(ReplayProtection.Result.DuplicateMessageId, replay)
    }

    @Test
    fun `replay protection rejects stale or replayed counter`() {
        val rp = ReplayProtection()
        rp.checkAndRecord("alice", "msg-1", 5L)
        val staleResult = rp.checkAndRecord("alice", "msg-2", 3L) // counter went backwards
        assertEquals(ReplayProtection.Result.StaleOrReplayedCounter, staleResult)
    }

    @Test
    fun `replay protection accepts increasing counters with new message ids`() {
        val rp = ReplayProtection()
        assertEquals(ReplayProtection.Result.Accepted, rp.checkAndRecord("alice", "msg-1", 1L))
        assertEquals(ReplayProtection.Result.Accepted, rp.checkAndRecord("alice", "msg-2", 2L))
        assertEquals(ReplayProtection.Result.Accepted, rp.checkAndRecord("alice", "msg-3", 3L))
    }

    // ---------------- PaddingUtil ----------------

    @Test
    fun `padding then unpadding recovers original bytes`() {
        val original = "short message".toByteArray(Charsets.UTF_8)
        val bucket = PaddingUtil.nextBucket(original.size + 4, PaddingUtil.MESSAGE_BUCKETS)
        val padded = PaddingUtil.pad(original, bucket)
        val unpadded = PaddingUtil.unpad(padded)
        assertArrayEquals(original, unpadded)
    }

    @Test
    fun `padded size hides true message length within a bucket`() {
        val shortMsg = "hi".toByteArray(Charsets.UTF_8)
        val longerMsg = "hi there, this is longer".toByteArray(Charsets.UTF_8)
        val shortBucket = PaddingUtil.nextBucket(shortMsg.size + 4, PaddingUtil.MESSAGE_BUCKETS)
        val longerBucket = PaddingUtil.nextBucket(longerMsg.size + 4, PaddingUtil.MESSAGE_BUCKETS)
        // Both fall into the same smallest bucket (64) despite different lengths -> size is hidden.
        assertEquals(64, shortBucket)
        assertEquals(64, longerBucket)
    }

    // ---------------- Full pipeline composition (substitution -> rotation), sans Base64/AES ----------------

    @Test
    fun `substitution and rotation compose and reverse correctly together`() {
        val table = SubstitutionTable.generateRandom()
        val rotation = RotationCipher.generateRandom()
        val plain = "MaunKavach end-to-end pipeline composition test.".toByteArray(Charsets.UTF_8)

        val afterSub = table.substitute(plain)
        val afterRot = rotation.rotate(afterSub)

        val back1 = rotation.reverseRotate(afterRot)
        val back2 = table.reverseSubstitute(back1)

        assertArrayEquals(plain, back2)
    }

    private fun intArrayOfFrom(table: SubstitutionTable): IntArray = table.forward
}
