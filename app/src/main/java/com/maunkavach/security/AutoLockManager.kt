package com.maunkavach.security

import android.os.Handler
import android.os.Looper
import java.util.Arrays

/**
 * Implements spec section 11's lock policy. This is app-level state, not a crypto primitive —
 * it decides *when* to forget decrypted material and re-demand authentication; the actual key
 * material it's protecting lives in VaultKeyManager/EphemeralKeyDerivation, which already
 * never persist ephemeral keys and only hold the Vault unlocked in EncryptedSharedPreferences
 * (itself Keystore-wrapped).
 *
 * "Wipe session keys from RAM on lock" — for keys represented as raw ByteArray, call
 * [wipeBytes] on them as soon as they're no longer needed; for javax.crypto.SecretKeySpec
 * there's no JVM-level guarantee the backing array can be located and zeroed (the JVM may have
 * copied it), so the strongest *practical* version of this guarantee is: don't hold long-lived
 * references to decrypted key material outside a short-lived local scope, and let it become
 * eligible for GC immediately. wipeBytes() helps for the ByteArrays you do control directly
 * (e.g. before a SecretKeySpec is constructed from them, or scratch buffers).
 */
class AutoLockManager(
    private val idleTimeoutMillis: Long = 45_000L,
    private val onLock: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null

    @Volatile var isLocked: Boolean = true
        private set

    fun unlock() {
        isLocked = false
        resetIdleTimer()
    }

    fun lockNow(reason: LockReason) {
        isLocked = true
        cancelIdleTimer()
        onLock()
    }

    fun resetIdleTimer() {
        cancelIdleTimer()
        val r = Runnable { lockNow(LockReason.IDLE_TIMEOUT) }
        idleRunnable = r
        handler.postDelayed(r, idleTimeoutMillis)
    }

    private fun cancelIdleTimer() {
        idleRunnable?.let { handler.removeCallbacks(it) }
        idleRunnable = null
    }

    /** Call from Activity.onPause()/ProcessLifecycleOwner background callback. */
    fun onAppBackgrounded() = lockNow(LockReason.APP_BACKGROUNDED)

    /** Call from a SCREEN_OFF broadcast receiver. */
    fun onScreenOff() = lockNow(LockReason.SCREEN_OFF)

    /** Call once at process start if you detect this is a fresh process after a reboot (e.g. via a boot-time marker). */
    fun onDeviceRebootDetected() = lockNow(LockReason.REBOOT)

    fun onIntegrityCheckFailed() = lockNow(LockReason.INTEGRITY_CHECK_FAILED)

    enum class LockReason { IDLE_TIMEOUT, APP_BACKGROUNDED, SCREEN_OFF, REBOOT, INTEGRITY_CHECK_FAILED, MANUAL, FAILED_ATTEMPTS }

    companion object {
        /** Best-effort zeroing of a byte array holding key/secret material once it's no longer needed. */
        fun wipeBytes(vararg arrays: ByteArray?) {
            arrays.forEach { it?.let { arr -> Arrays.fill(arr, 0) } }
        }
    }
}

/**
 * Failed-PIN-attempt policy per spec section 11: 5 wrong -> temporary lock, 10 wrong -> longer
 * lock, optional paranoid-mode wipe. Persist [failedCount]/[lockedUntilMillis] in
 * EncryptedSharedPreferences (via VaultKeyManager's configPrefs) so a force-restart doesn't
 * reset the counter.
 */
class FailedAttemptPolicy(
    private var failedCount: Int = 0,
    private var lockedUntilMillis: Long = 0L,
    private val paranoidModeEnabled: Boolean = false,
    private val paranoidWipeThreshold: Int = 15,
    private val onWipeVaultKeys: () -> Unit
) {
    sealed class AttemptResult {
        object Allowed : AttemptResult()
        data class TemporarilyLocked(val untilMillis: Long) : AttemptResult()
        object VaultWiped : AttemptResult()
    }

    fun recordFailure(nowMillis: Long = System.currentTimeMillis()): AttemptResult {
        failedCount++

        if (paranoidModeEnabled && failedCount >= paranoidWipeThreshold) {
            onWipeVaultKeys()
            return AttemptResult.VaultWiped
        }

        val lockDurationMillis = when {
            failedCount >= 10 -> 30 * 60 * 1000L  // 30 min after 10 failures
            failedCount >= 5 -> 60 * 1000L         // 1 min after 5 failures
            else -> 0L
        }
        if (lockDurationMillis > 0) {
            lockedUntilMillis = nowMillis + lockDurationMillis
            return AttemptResult.TemporarilyLocked(lockedUntilMillis)
        }
        return AttemptResult.Allowed
    }

    fun recordSuccess() {
        failedCount = 0
        lockedUntilMillis = 0L
    }

    fun isCurrentlyLocked(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis < lockedUntilMillis
    fun remainingLockMillis(nowMillis: Long = System.currentTimeMillis()): Long = (lockedUntilMillis - nowMillis).coerceAtLeast(0)
}
