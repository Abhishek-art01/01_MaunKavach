package com.maunkavach.security

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.view.WindowManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.security.MessageDigest

/**
 * Best-effort device/runtime integrity signals (spec sections 14-16). Read this comment block
 * before trusting any of these checks with something important:
 *
 * NONE of this is a hard security boundary. On a device an attacker fully controls (their own
 * rooted phone, a debugger they attached themselves, a patched Frida gadget), every check
 * below can in principle be patched out, hooked, or spoofed — that's true of any client-side
 * integrity check on any OS, not a flaw specific to this implementation. What these checks
 * *do* meaningfully raise the bar against: casual/automated tooling, opportunistic malware
 * scanning for unprotected apps, and an unsophisticated attacker with brief physical access.
 * Real security still rests on Keystore-backed (hardware-isolated, where available) keys +
 * AEAD + HMAC — the things that hold even if every one of these heuristics is bypassed.
 */
object DeviceIntegrity {

    // ---------------- Anti-debugging ----------------

    fun isDebuggerAttached(): Boolean = Debug.isDebuggerConnected() || Debug.waitingForDebugger()

    /** TracerPid in /proc/self/status is set by the kernel when something is ptrace-attached (debugger or Frida). */
    fun hasTracerPid(): Boolean {
        return try {
            File("/proc/self/status").bufferedReader().useLines { lines ->
                lines.any { line ->
                    line.startsWith("TracerPid:") && line.substringAfter(":").trim().toIntOrNull()?.let { it != 0 } == true
                }
            }
        } catch (e: Exception) {
            false // if we can't read /proc, fail open rather than false-flagging on permission-restricted devices
        }
    }

    // ---------------- Root detection ----------------

    fun isLikelyRooted(): Boolean {
        val suPaths = listOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
            "/su/bin/su", "/system/bin/.ext/.su", "/system/xbin/daemonsu"
        )
        if (suPaths.any { File(it).exists() }) return true
        if (Build.TAGS?.contains("test-keys") == true) return true
        return hasMagiskTraces()
    }

    private fun hasMagiskTraces(): Boolean {
        val magiskPaths = listOf("/sbin/.magisk", "/data/adb/magisk", "/cache/magisk.log")
        return magiskPaths.any { File(it).exists() }
    }

    // ---------------- Frida / instrumentation detection (best-effort, see class doc) ----------------

    /**
     * Heuristic only: checks for well-known Frida artifacts (default frida-server port,
     * commonly-named pipes/files, suspicious loaded library names). A motivated attacker using
     * a renamed/customized Frida gadget will not be caught by this — documenting the limit
     * rather than pretending otherwise.
     */
    fun hasFridaIndicators(): Boolean {
        if (hasTracerPid()) return true
        if (isFridaDefaultPortOpen()) return true
        if (hasSuspiciousMappedLibraries()) return true
        return false
    }

    private fun isFridaDefaultPortOpen(): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 27042), 200)
            socket.close()
            true // something is listening on Frida's default port
        } catch (e: Exception) {
            false
        }
    }

    private fun hasSuspiciousMappedLibraries(): Boolean {
        return try {
            File("/proc/self/maps").bufferedReader().useLines { lines ->
                lines.any { line ->
                    listOf("frida", "gadget", "gum-js-loop", "linjector").any { marker -> line.contains(marker, ignoreCase = true) }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // ---------------- Emulator detection ----------------

    fun isLikelyEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_gphone"))
    }

    // ---------------- App signature / tamper detection ----------------

    /**
     * Compares the running APK's signing certificate SHA-256 fingerprint against the known-good
     * one baked in at release-build time. [expectedFingerprint] must be the actual release
     * signing cert's fingerprint — generate it with:
     *   keytool -printcert -jarfile app-release.apk
     * and hardcode the SHA-256 (not the AES/Vault keys — a cert fingerprint is not a secret;
     * it's a public verification value, safe to ship in the APK per spec section 16).
     */
    @Suppress("DEPRECATION")
    fun verifySigningCertificate(context: Context, expectedFingerprintSha256: String): Boolean {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners ?: arrayOf()
            } else {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                info.signatures ?: arrayOf()
            }
            if (signatures.isEmpty()) return false
            val digest = MessageDigest.getInstance("SHA-256")
            val actual = digest.digest(signatures[0].toByteArray()).joinToString(":") { "%02X".format(it) }
            actual.equals(expectedFingerprintSha256, ignoreCase = true)
        } catch (e: Exception) {
            false // fail closed for signature checks specifically — an exception here is itself suspicious
        }
    }

    // ---------------- Accessibility service / overlay risk (spec section 14) ----------------

    /** Flags if any *third-party* accessibility service is currently enabled — a common malware vector for screen-reading/overlay attacks. */
    fun hasRiskyAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            // Any enabled service outside our own package counts as "risky" for paranoid mode purposes.
            enabled.split(":").any { it.isNotBlank() && !it.startsWith(context.packageName) }
        } catch (e: Exception) {
            false
        }
    }

    /** Best-effort: Android doesn't expose a direct "is something drawing an overlay over me" API pre-Q; this checks the canDrawOverlays permission state of other apps is not feasible from app-side, so this flags only our OWN overlay permission state as a placeholder signal. Real overlay-attack defense relies on FLAG_SECURE + touch filtering, not detection. */
    fun overlayPermissionGrantedToSelf(context: Context): Boolean {
        return android.provider.Settings.canDrawOverlays(context)
    }

    // ---------------- Screenshot / recording protection ----------------

    fun enableScreenshotProtection(activity: Activity) {
        activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    // ---------------- Clipboard ----------------

    fun scheduleClipboardClear(context: Context, delayMillis: Long = 10_000L) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }, delayMillis)
    }

    // ---------------- Aggregate risk snapshot for the Security Dashboard ----------------

    data class RiskSnapshot(
        val rooted: Boolean,
        val debuggerAttached: Boolean,
        val fridaIndicators: Boolean,
        val emulator: Boolean,
        val riskyAccessibilityService: Boolean,
        val backupDisabled: Boolean = true // hardcoded true: enforced via AndroidManifest android:allowBackup="false"
    ) {
        val anyHighRisk: Boolean get() = rooted || debuggerAttached || fridaIndicators
    }

    fun snapshot(context: Context): RiskSnapshot = RiskSnapshot(
        rooted = isLikelyRooted(),
        debuggerAttached = isDebuggerAttached() || hasTracerPid(),
        fridaIndicators = hasFridaIndicators(),
        emulator = isLikelyEmulator(),
        riskyAccessibilityService = hasRiskyAccessibilityServiceEnabled(context)
    )
}
