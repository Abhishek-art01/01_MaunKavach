# MaunKavach — Encrypted Messenger

Native Android client (Kotlin + Jetpack Compose) + a Node.js/Postgres backend, implementing
contact-wise encryption keys, custom byte-substitution + 999-step rotation ciphers, per-message
forward-secret keys, HMAC tamper/replay protection, and a locked "Vault Key" settings section.

> ⚠️ This is an advanced security-hardened scaffold, not an audited production security
> product. The crypto core (substitution table, rotation cipher, HMAC, ephemeral key
> derivation, replay protection) has been logic-verified via an independent test harness and
> has a full JVM unit test suite — see "What's actually been verified" below. The
> physical/anti-tamper layer (root/Frida detection, signature checks) is real working code but
> is, by nature, advisory rather than a hard boundary — see the comment block atop
> `DeviceIntegrity.kt` for exactly what that does and doesn't guarantee.

## Folder layout

```
MaunKavach/
  app/         Android app (Kotlin, Jetpack Compose)
  backend/     Node.js + Postgres(Supabase) + Supabase Storage backend
  web/         Marketing site (Vite/React, deploy to Vercel)
  README.md, DEPLOY.md
```

## Crypto core (`app/src/main/java/com/maunkavach/crypto/`)

| File | What it does |
|---|---|
| `SubstitutionTable.kt` | 256-byte permutation cipher; strict validation (no duplicates, no missing values) |
| `RotationCipher.kt` | 999-step rotation rule, advances per byte, repeats allowed |
| `EphemeralKeyDerivation.kt` | Per-message forward-secret key from contact master key + counter + nonce + direction |
| `HmacSigner.kt` | HMAC-SHA256 sign/verify, constant-time comparison |
| `PaddingUtil.kt` | Size-bucket padding so ciphertext length doesn't leak true message/file size |
| `ReplayProtection.kt` | Tracks seen message_ids + per-contact counters; rejects duplicates/replays |
| `ContactKeyBundle.kt` | The full per-contact key material the spec asks Vault Key to store |
| `VaultKeyManager.kt` | Vault Key data layer: auto/manual key generation, expiry, regenerate (delete-old or keep-old-version), decoy vault, panic PIN, QR export/import |
| `MessagePipeline.kt` | Full message flow: substitution → rotation → ephemeral key → AES-256-GCM → HMAC |
| `FileCrypto.kt` | Full file flow: substitution → rotation → random per-file AES key → envelope-wrap → encrypted metadata → HMAC |
| `CryptoManager.kt` | Low-level AES-256-GCM + Android Keystore master key primitives |

## Security layer (`app/src/main/java/com/maunkavach/security/`)

- `BiometricHelper.kt` — Vault Key unlock gating, every time, no caching
- `DeviceIntegrity.kt` — root/TracerPid/Frida-port/Frida-library heuristics, emulator detection,
  signing-certificate verification, risky-accessibility-service detection, FLAG_SECURE, clipboard auto-clear
- `AutoLockManager.kt` — idle timeout, background/screen-off/reboot/integrity-failure locking,
  failed-PIN-attempt lockout policy (5 → temp lock, 10 → longer lock, optional paranoid-mode wipe)

## What's actually been verified (not just written)

I can't run Kotlin/Android in the sandbox that built this, so verification happened in two passes:

1. **Algorithm logic**, ported faithfully into Kotlin afterward: substitution-table validation,
   999-step rotation (forward+reverse, validation, "same byte → different output" property),
   ephemeral key derivation (forward-secrecy property: different counter → different key),
   HMAC tamper detection, and message_id/counter replay rejection — all independently verified
   in a Node.js test harness before being written as Kotlin.
2. **The actual Kotlin files**, via `app/src/test/java/com/maunkavach/crypto/CryptoCoreTest.kt`
   — a real JUnit suite covering every item above directly against the shipped Kotlin code
   (not a reimplementation). Run it with Android Studio's test runner or `./gradlew test`.
3. **The actual backend `server.js`**, via `backend/test/backend.test.js` — mocks Postgres and
   Supabase Storage, but exercises the real server code: registration, login, auth gating,
   exact-replay rejection (409), replayed-counter-under-new-id rejection (409), expired-message
   disappearance, and ciphertext-only storage. Run with `npm test` inside `backend/`.

What is NOT covered by an automated test (needs a real device/emulator or manual QA):
screenshot blocking, backup-disabled behavior, Vault locking on background, session-key RAM
wipe, and the root/Frida/debugger detection heuristics actually firing against real tooling.

## Honest limitations

- **Forward secrecy** here derives a fresh AES key per message from a long-lived contact
  master key + counter (one-way hash, so past ephemeral keys aren't recoverable from a future
  one) — it does **not** implement a full Double-Ratchet, so a master-key compromise *would*
  expose future messages too, not just past ones. Said plainly in `EphemeralKeyDerivation.kt`.
- **Root/Frida/debugger detection** raises the bar against casual tooling and opportunistic
  malware; none of it is unbypassable against an attacker who fully controls the device. The
  real guarantee is the Keystore-backed AES-256-GCM + HMAC layer underneath, which holds even
  if every heuristic above it is evaded.
- **App signature verification** needs your real release-signing cert's SHA-256 fingerprint
  hardcoded in before it does anything (`DeviceIntegrity.verifySigningCertificate`) — ships as
  a no-op-until-configured helper, not a default-on check.
- **QR sharing** renders a visually QR-like block matrix (no third-party QR lib, per spec) —
  it is not a real scannable ISO/IEC 18004 QR code.
- **`AutoLockManager`/`FailedAttemptPolicy`** are complete, usable classes but not yet wired
  into `MainActivity`'s lifecycle callbacks or a PIN-entry screen — hook `onAppBackgrounded()`/
  `onScreenOff()` into your Activity's lifecycle and call `FailedAttemptPolicy.recordFailure()`
  from wherever PIN entry happens.
- **App camouflage**, **fake traffic injection**, and **batch/random-delay sending** (spec
  sections 7, 8, 20) are documented with `TODO`s in the relevant screens but not implemented —
  flag if you want these built out next.

See `DEPLOY.md` for how to actually get this running against Supabase + Render.
