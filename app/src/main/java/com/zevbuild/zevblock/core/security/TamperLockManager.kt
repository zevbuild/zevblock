package com.zevbuild.zevblock.core.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.min

/**
 * TamperLockManager handles:
 * 1. RFC 6238 TOTP verification with replay protection & brute-force lockout.
 * 2. Android Keystore-backed encryption for partner secrets and device integrity keys.
 * 3. Monotonic-clock tamper-proof cooldown timers with constant-time HMAC validation.
 * 4. Temporary partner-authorized bypass windows that fail closed on any integrity corruption.
 */
class TamperLockManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyPlaintextStorage()
    }

    // =========================================================================
    // SECTION 1: RFC 6238 TOTP ENGINE (Accountability Partner)
    // =========================================================================

    /**
     * Verifies a 6-digit candidate code against the partner's TOTP secret.
     * Features:
     * - Replay-resistant: Rejects any code matching an already accepted time step.
     * - Brute-force throttling: Enforces temporary lockouts after repeated failed attempts.
     * - Clock skew tolerant: Allows ±1 step (±30s) without accepting stale steps.
     * - Constant-time comparison: Prevents timing side-channel leaks.
     * - Zero-leak logging: Never prints secret keys or submitted OTP codes to logcat.
     */
    @Synchronized
    fun verifyPartnerTotp(candidateCode: String): Boolean {
        // Enforce strict 6-digit formatting
        val trimmed = candidateCode.trim()
        if (trimmed.length != 6 || !trimmed.all { it.isDigit() }) {
            Log.w(TAG, "Partner TOTP rejected: input must be strictly 6 numeric digits.")
            recordFailedAttempt()
            return false
        }

        // Check if currently locked out due to excessive failed attempts
        val lockoutRemainingMs = getLockoutRemainingMs()
        if (lockoutRemainingMs > 0) {
            Log.w(TAG, "Partner TOTP rejected: temporarily locked out for ${lockoutRemainingMs / 1000}s.")
            return false
        }

        val secret = getPartnerSecret()
        if (secret.isNullOrBlank()) {
            Log.w(TAG, "Partner TOTP verification failed: no partner secret paired.")
            return false
        }

        val currentEpochSeconds = System.currentTimeMillis() / 1000L
        val currentStep = currentEpochSeconds / TOTP_TIME_STEP_SECONDS
        val lastAcceptedStep = prefs.getLong(KEY_LAST_ACCEPTED_STEP, -1L)

        // Test step offsets in priority order: current (0), preceding (-1), succeeding (+1)
        val candidateOffsets = listOf(0L, -1L, 1L)

        for (stepOffset in candidateOffsets) {
            val step = currentStep + stepOffset

            // Replay protection: Step must be strictly greater than the last consumed step
            if (step <= lastAcceptedStep) {
                continue
            }

            val expectedCode = generateTotpCodeForStep(secret, step)
            if (isConstantTimeEqual(expectedCode, trimmed)) {
                // Successfully authenticated! Atomically commit the consumed step and reset failed attempts
                prefs.edit()
                    .putLong(KEY_LAST_ACCEPTED_STEP, step)
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
                    .putLong(KEY_LOCKOUT_UNTIL_WALL, 0L)
                    .commit()

                Log.i(TAG, "Partner TOTP successfully verified and step $step consumed (replay-locked).")
                return true
            }
        }

        Log.w(TAG, "Partner TOTP verification failed for input code.")
        recordFailedAttempt()
        return false
    }

    /**
     * Checks if TOTP verification is currently throttled / locked out.
     */
    @Synchronized
    fun getLockoutRemainingMs(): Long {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        val lockoutElapsed = prefs.getLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
        val lockoutWall = prefs.getLong(KEY_LOCKOUT_UNTIL_WALL, 0L)

        if (lockoutElapsed <= 0L && lockoutWall <= 0L) return 0L

        val remainingElapsed = max(0L, lockoutElapsed - nowElapsed)
        val remainingWall = max(0L, lockoutWall - nowWall)
        val remaining = max(remainingElapsed, remainingWall)

        if (remaining <= 0L) {
            // Lockout expired
            prefs.edit()
                .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
                .putLong(KEY_LOCKOUT_UNTIL_WALL, 0L)
                .apply()
        }
        return remaining
    }

    private fun recordFailedAttempt() {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        val lockoutDurationMs = when {
            attempts >= 15 -> 15 * 60 * 1000L // 15 minutes lockout
            attempts >= 10 -> 5 * 60 * 1000L  // 5 minutes lockout
            attempts >= 5 -> 30 * 1000L       // 30 seconds throttle
            else -> 0L
        }

        if (lockoutDurationMs > 0) {
            editor.putLong(KEY_LOCKOUT_UNTIL_ELAPSED, nowElapsed + lockoutDurationMs)
            editor.putLong(KEY_LOCKOUT_UNTIL_WALL, nowWall + lockoutDurationMs)
            Log.w(TAG, "Repeated invalid TOTP attempts ($attempts); lockout engaged for ${lockoutDurationMs / 1000}s")
        }
        editor.commit()
    }

    /**
     * Generates a new cryptographically secure 160-bit Base32 secret key,
     * encrypts it using Android Keystore, and returns the Base32 string.
     */
    @Synchronized
    fun setupNewPartnerSecret(): String {
        val randomBytes = ByteArray(20) // 160 bits (recommended for SHA1 HMAC)
        SecureRandom().nextBytes(randomBytes)
        val base32Secret = Base32.encode(randomBytes)
        savePartnerSecret(base32Secret)
        Log.i(TAG, "Generated and stored new accountability partner TOTP secret (Keystore-encrypted)")
        return base32Secret
    }

    /**
     * Retrieves the active partner Base32 secret by decrypting it from the Keystore,
     * or returns null if not paired / decryption fails (fail closed).
     */
    @Synchronized
    fun getPartnerSecret(): String? {
        val encryptedPayload = prefs.getString(KEY_PARTNER_SECRET_ENCRYPTED, null)
        if (encryptedPayload != null) {
            val decrypted = CryptoKeyManager.decrypt(encryptedPayload)
            if (decrypted == null) {
                Log.e(TAG, "CRITICAL: Failed to decrypt partner secret from Keystore! Failing closed.")
            }
            return decrypted
        }

        // Check if legacy plaintext secret exists and migrate
        val legacySecret = prefs.getString(KEY_PARTNER_SECRET_LEGACY, null)
        if (!legacySecret.isNullOrBlank()) {
            savePartnerSecret(legacySecret)
            prefs.edit().remove(KEY_PARTNER_SECRET_LEGACY).commit()
            return legacySecret
        }
        return null
    }

    /**
     * Clears partner pairing secret (only allowed when unlocked).
     */
    @Synchronized
    fun clearPartnerSecret() {
        prefs.edit()
            .remove(KEY_PARTNER_SECRET_ENCRYPTED)
            .remove(KEY_PARTNER_SECRET_LEGACY)
            .remove(KEY_LAST_ACCEPTED_STEP)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL_ELAPSED)
            .remove(KEY_LOCKOUT_UNTIL_WALL)
            .commit()
    }

    /**
     * Constructs a standard `otpauth://totp/...` URI for generating QR codes
     * scannable by Google Authenticator, Aegis, Bitwarden, 2FAS, etc.
     */
    fun getPartnerTotpUri(partnerLabel: String = "Accountability Partner"): String? {
        val secret = getPartnerSecret() ?: return null
        val encodedLabel = URLEncoder.encode("ZevBlock:$partnerLabel", "UTF-8")
        val encodedIssuer = URLEncoder.encode("ZevBlock", "UTF-8")
        return "otpauth://totp/$encodedLabel?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=6&period=30"
    }

    /**
     * RFC 6238 HMAC-SHA1 calculation for a given time step.
     */
    fun generateTotpCodeForStep(base32Secret: String, step: Long): String {
        val keyBytes = Base32.decode(base32Secret)
        val data = ByteBuffer.allocate(8).putLong(step).array()

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hash = mac.doFinal(data)

        // Dynamic truncation (RFC 4226 section 5.4)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 1_000_000
        return String.format(java.util.Locale.US, "%06d", otp)
    }

    private fun savePartnerSecret(secret: String) {
        val encrypted = CryptoKeyManager.encrypt(secret)
        prefs.edit()
            .putString(KEY_PARTNER_SECRET_ENCRYPTED, encrypted)
            .remove(KEY_PARTNER_SECRET_LEGACY)
            .commit()
    }

    // =========================================================================
    // SECTION 2: MONOTONIC ANTI-TAMPER COOLDOWN TIMER
    // =========================================================================

    /**
     * Starts a cooldown countdown of duration [durationMs].
     * Uses [SystemClock.elapsedRealtime()] (monotonic hardware clock) as primary baseline.
     */
    @Synchronized
    fun startCooldown(durationMs: Long = DEFAULT_COOLDOWN_MS) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        prefs.edit()
            .putBoolean(KEY_COOLDOWN_ACTIVE, true)
            .putBoolean(KEY_COOLDOWN_COMPLETED, false)
            .putLong(KEY_COOLDOWN_DURATION, durationMs)
            .putLong(KEY_COOLDOWN_REMAINING, durationMs)
            .putLong(KEY_LAST_CHECKED_ELAPSED, nowElapsed)
            .putLong(KEY_LAST_CHECKED_WALL, nowWall)
            .putLong(KEY_COOLDOWN_START_WALL, nowWall)
            .putString(KEY_COOLDOWN_SIGNATURE, createCooldownSignature(durationMs, durationMs, nowElapsed, nowWall))
            .commit()

        Log.i(TAG, "Anti-tamper cooldown timer started: duration=$durationMs ms ($durationMs / 60000 min)")
    }

    /**
     * Returns remaining milliseconds on the cooldown timer.
     * Correctly handles sleep, deep-idle, and detects system clock manipulations.
     */
    @Synchronized
    fun getRemainingCooldownMs(): Long {
        if (!isCooldownActive()) return 0L

        // Validate integrity signature & state bounds
        if (!verifyCooldownSignature()) {
            Log.e(TAG, "Cooldown integrity check failed! Storage tampered. Enforcing safety lock.")
            return DEFAULT_COOLDOWN_MS
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        val lastElapsed = prefs.getLong(KEY_LAST_CHECKED_ELAPSED, nowElapsed)
        val lastWall = prefs.getLong(KEY_LAST_CHECKED_WALL, nowWall)
        var remaining = prefs.getLong(KEY_COOLDOWN_REMAINING, 0L)

        if (nowElapsed < lastElapsed) {
            // Monotonic clock reset indicates device was rebooted.
            val deltaWall = nowWall - lastWall
            if (deltaWall in 1..MAX_SANE_WALL_CLOCK_JUMP_MS) {
                remaining = max(0L, remaining - deltaWall)
                Log.i(TAG, "Reboot detected. Deducted $deltaWall ms of offline wall time.")
            } else if (deltaWall < 0) {
                Log.w(TAG, "System clock rollback detected! Zero time credited.")
            }
        } else {
            val deltaElapsed = nowElapsed - lastElapsed
            remaining = max(0L, remaining - deltaElapsed)
        }

        if (remaining <= 0L) {
            markCooldownCompleted()
            return 0L
        }

        val duration = prefs.getLong(KEY_COOLDOWN_DURATION, DEFAULT_COOLDOWN_MS)
        prefs.edit()
            .putLong(KEY_COOLDOWN_REMAINING, remaining)
            .putLong(KEY_LAST_CHECKED_ELAPSED, nowElapsed)
            .putLong(KEY_LAST_CHECKED_WALL, nowWall)
            .putString(KEY_COOLDOWN_SIGNATURE, createCooldownSignature(duration, remaining, nowElapsed, nowWall))
            .apply()

        return remaining
    }

    fun isCooldownActive(): Boolean {
        return prefs.getBoolean(KEY_COOLDOWN_ACTIVE, false)
    }

    fun isCooldownFinished(): Boolean {
        return prefs.getBoolean(KEY_COOLDOWN_COMPLETED, false)
    }

    @Synchronized
    fun cancelCooldown() {
        prefs.edit()
            .putBoolean(KEY_COOLDOWN_ACTIVE, false)
            .putBoolean(KEY_COOLDOWN_COMPLETED, false)
            .putLong(KEY_COOLDOWN_REMAINING, 0L)
            .remove(KEY_COOLDOWN_SIGNATURE)
            .commit()
        Log.i(TAG, "Cooldown timer cancelled")
    }

    @Synchronized
    private fun markCooldownCompleted() {
        prefs.edit()
            .putBoolean(KEY_COOLDOWN_ACTIVE, false)
            .putBoolean(KEY_COOLDOWN_COMPLETED, true)
            .putLong(KEY_COOLDOWN_REMAINING, 0L)
            .commit()
        Log.i(TAG, "Cooldown timer reached 0: Unlock authorized.")
    }

    // =========================================================================
    // SECTION 2B: TEMPORARY BYPASS MANAGER (Partner TOTP Authorized)
    // =========================================================================

    @Synchronized
    fun activateBypassWithTotp(totpCode: String, durationMs: Long): Boolean {
        if (!verifyPartnerTotp(totpCode)) {
            Log.w(TAG, "Cannot activate bypass: invalid or replayed partner TOTP code.")
            return false
        }
        startTemporaryBypass(durationMs)
        return true
    }

    @Synchronized
    fun startTemporaryBypass(durationMs: Long) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        prefs.edit()
            .putBoolean(KEY_BYPASS_ACTIVE, true)
            .putLong(KEY_BYPASS_DURATION, durationMs)
            .putLong(KEY_BYPASS_REMAINING, durationMs)
            .putLong(KEY_BYPASS_LAST_ELAPSED, nowElapsed)
            .putLong(KEY_BYPASS_LAST_WALL, nowWall)
            .putString(KEY_BYPASS_SIGNATURE, createBypassSignature(durationMs, durationMs, nowElapsed, nowWall))
            .commit()

        cachedBypassActive = true
        Log.i(TAG, "Temporary bypass activated for $durationMs ms ($durationMs / 60000 min)")
    }

    @Synchronized
    fun getRemainingBypassMs(): Long {
        if (!isBypassActive()) return 0L

        if (!verifyBypassSignature()) {
            Log.e(TAG, "Bypass signature validation failed! Cancelling bypass.")
            cancelTemporaryBypass()
            return 0L
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        val lastElapsed = prefs.getLong(KEY_BYPASS_LAST_ELAPSED, nowElapsed)
        val lastWall = prefs.getLong(KEY_BYPASS_LAST_WALL, nowWall)
        var remaining = prefs.getLong(KEY_BYPASS_REMAINING, 0L)

        if (nowElapsed < lastElapsed) {
            val deltaWall = nowWall - lastWall
            if (deltaWall in 1..MAX_SANE_WALL_CLOCK_JUMP_MS) {
                remaining = max(0L, remaining - deltaWall)
            } else if (deltaWall < 0) {
                remaining = 0L
            }
        } else {
            val deltaElapsed = nowElapsed - lastElapsed
            remaining = max(0L, remaining - deltaElapsed)
        }

        if (remaining <= 0L) {
            cancelTemporaryBypass()
            return 0L
        }

        val duration = prefs.getLong(KEY_BYPASS_DURATION, remaining)
        prefs.edit()
            .putLong(KEY_BYPASS_REMAINING, remaining)
            .putLong(KEY_BYPASS_LAST_ELAPSED, nowElapsed)
            .putLong(KEY_BYPASS_LAST_WALL, nowWall)
            .putString(KEY_BYPASS_SIGNATURE, createBypassSignature(duration, remaining, nowElapsed, nowWall))
            .apply()

        return remaining
    }

    fun isBypassActive(): Boolean {
        val active = prefs.getBoolean(KEY_BYPASS_ACTIVE, false)
        cachedBypassActive = active
        return active
    }

    @Synchronized
    fun cancelTemporaryBypass() {
        prefs.edit()
            .putBoolean(KEY_BYPASS_ACTIVE, false)
            .putLong(KEY_BYPASS_REMAINING, 0L)
            .remove(KEY_BYPASS_SIGNATURE)
            .commit()
        cachedBypassActive = false
        Log.i(TAG, "Temporary bypass terminated: protections fully re-engaged.")
    }

    private fun createBypassSignature(
        duration: Long,
        remaining: Long,
        lastElapsed: Long,
        lastWall: Long
    ): String {
        val payload = "BYPASS:D:$duration|R:$remaining|E:$lastElapsed|W:$lastWall"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(getOrCreateDeviceKey(), "HmacSHA256"))
        val sig = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return sig.joinToString("") { "%02x".format(it) }
    }

    private fun verifyBypassSignature(): Boolean {
        val duration = prefs.getLong(KEY_BYPASS_DURATION, -1L)
        val remaining = prefs.getLong(KEY_BYPASS_REMAINING, -1L)
        val lastElapsed = prefs.getLong(KEY_BYPASS_LAST_ELAPSED, -1L)
        val lastWall = prefs.getLong(KEY_BYPASS_LAST_WALL, -1L)
        val storedSig = prefs.getString(KEY_BYPASS_SIGNATURE, null) ?: return false

        // State bounds validation
        if (duration <= 0L || duration > 24 * 3_600_000L) return false
        if (remaining < 0L || remaining > duration) return false
        if (lastElapsed <= 0L || lastWall <= 0L) return false

        val computedSig = createBypassSignature(duration, remaining, lastElapsed, lastWall)
        return isConstantTimeEqual(computedSig, storedSig)
    }

    // =========================================================================
    // SECTION 3: CRYPTOGRAPHIC INTEGRITY SEALING
    // =========================================================================

    @Synchronized
    private fun getOrCreateDeviceKey(): ByteArray {
        val encryptedKey = prefs.getString(KEY_DEVICE_INTEGRITY_KEY_ENCRYPTED, null)
        if (encryptedKey != null) {
            val decrypted = CryptoKeyManager.decryptBytes(encryptedKey)
            if (decrypted != null && decrypted.size == 32) {
                return decrypted
            }
            Log.e(TAG, "Corrupt device integrity key; regenerating new hardware key.")
        }

        // Migrate legacy plaintext key if found
        val legacyHex = prefs.getString(KEY_DEVICE_INTEGRITY_KEY_LEGACY, null)
        if (legacyHex != null && legacyHex.length == 64) {
            val keyBytes = legacyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            saveDeviceKey(keyBytes)
            prefs.edit().remove(KEY_DEVICE_INTEGRITY_KEY_LEGACY).commit()
            return keyBytes
        }

        // Generate brand new 256-bit secure key
        val newKey = ByteArray(32)
        SecureRandom().nextBytes(newKey)
        saveDeviceKey(newKey)
        return newKey
    }

    private fun saveDeviceKey(key: ByteArray) {
        val encrypted = CryptoKeyManager.encryptBytes(key)
        prefs.edit()
            .putString(KEY_DEVICE_INTEGRITY_KEY_ENCRYPTED, encrypted)
            .remove(KEY_DEVICE_INTEGRITY_KEY_LEGACY)
            .commit()
    }

    private fun createCooldownSignature(
        duration: Long,
        remaining: Long,
        lastElapsed: Long,
        lastWall: Long
    ): String {
        val payload = "D:$duration|R:$remaining|E:$lastElapsed|W:$lastWall"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(getOrCreateDeviceKey(), "HmacSHA256"))
        val sig = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return sig.joinToString("") { "%02x".format(it) }
    }

    private fun verifyCooldownSignature(): Boolean {
        val duration = prefs.getLong(KEY_COOLDOWN_DURATION, -1L)
        val remaining = prefs.getLong(KEY_COOLDOWN_REMAINING, -1L)
        val lastElapsed = prefs.getLong(KEY_LAST_CHECKED_ELAPSED, -1L)
        val lastWall = prefs.getLong(KEY_LAST_CHECKED_WALL, -1L)
        val storedSig = prefs.getString(KEY_COOLDOWN_SIGNATURE, null) ?: return false

        // State bounds validation
        if (duration <= 0L || duration > 30 * 86_400_000L) return false
        if (remaining < 0L || remaining > duration) return false
        if (lastElapsed <= 0L || lastWall <= 0L) return false

        val computedSig = createCooldownSignature(duration, remaining, lastElapsed, lastWall)
        return isConstantTimeEqual(computedSig, storedSig)
    }

    private fun isConstantTimeEqual(a: String, b: String): Boolean {
        val bytesA = a.toByteArray(Charsets.UTF_8)
        val bytesB = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(bytesA, bytesB)
    }

    private fun migrateLegacyPlaintextStorage() {
        // Migrate partner secret
        val legacySecret = prefs.getString(KEY_PARTNER_SECRET_LEGACY, null)
        if (!legacySecret.isNullOrBlank() && !prefs.contains(KEY_PARTNER_SECRET_ENCRYPTED)) {
            Log.i(TAG, "Migrating legacy plaintext partner secret into Keystore-backed encryption...")
            savePartnerSecret(legacySecret)
        }

        // Migrate device key
        val legacyKey = prefs.getString(KEY_DEVICE_INTEGRITY_KEY_LEGACY, null)
        if (!legacyKey.isNullOrBlank() && !prefs.contains(KEY_DEVICE_INTEGRITY_KEY_ENCRYPTED)) {
            Log.i(TAG, "Migrating legacy plaintext device key into Keystore-backed encryption...")
            val keyBytes = legacyKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            saveDeviceKey(keyBytes)
        }
    }

    // =========================================================================
    // SECTION 4: RFC 4648 BASE32 ENCODER / DECODER
    // =========================================================================

    object Base32 {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        private val DECODE_TABLE = IntArray(128) { -1 }

        init {
            for (i in ALPHABET.indices) {
                DECODE_TABLE[ALPHABET[i].code] = i
                val lower = ALPHABET[i].lowercaseChar().code
                if (lower < 128) {
                    DECODE_TABLE[lower] = i
                }
            }
        }

        fun encode(data: ByteArray): String {
            val sb = java.lang.StringBuilder()
            var buffer = 0
            var bitsLeft = 0

            for (b in data) {
                buffer = (buffer shl 8) or (b.toInt() and 0xFF)
                bitsLeft += 8
                while (bitsLeft >= 5) {
                    val index = (buffer shr (bitsLeft - 5)) and 0x1F
                    bitsLeft -= 5
                    sb.append(ALPHABET[index])
                }
            }

            if (bitsLeft > 0) {
                val index = (buffer shl (5 - bitsLeft)) and 0x1F
                sb.append(ALPHABET[index])
            }

            return sb.toString()
        }

        fun decode(encoded: String): ByteArray {
            val clean = encoded.trim().replace("-", "").replace(" ", "").uppercase()
            val out = java.io.ByteArrayOutputStream()
            var buffer = 0
            var bitsLeft = 0

            for (ch in clean) {
                val code = ch.code
                if (code >= 128 || DECODE_TABLE[code] < 0) continue
                val value = DECODE_TABLE[code]
                buffer = (buffer shl 5) or value
                bitsLeft += 5

                if (bitsLeft >= 8) {
                    val b = (buffer shr (bitsLeft - 8)) and 0xFF
                    bitsLeft -= 8
                    out.write(b)
                }
            }

            return out.toByteArray()
        }
    }

    companion object {
        private const val TAG = "TamperLockManager"
        private const val PREFS_NAME = "shieldguard_tamper_lock"

        private const val TOTP_TIME_STEP_SECONDS = 30L
        const val DEFAULT_COOLDOWN_MS = 300_000L // 5 minutes default
        const val COOLDOWN_1_HOUR_MS = 3_600_000L
        const val COOLDOWN_24_HOURS_MS = 86_400_000L

        const val BYPASS_15_MIN_MS = 15 * 60 * 1000L
        const val BYPASS_30_MIN_MS = 30 * 60 * 1000L
        const val BYPASS_1_HOUR_MS = 60 * 60 * 1000L

        private const val MAX_SANE_WALL_CLOCK_JUMP_MS = 7 * 24 * 3600 * 1000L // 7 days

        // Preference keys
        private const val KEY_PARTNER_SECRET_LEGACY = "partner_totp_secret"
        private const val KEY_PARTNER_SECRET_ENCRYPTED = "partner_totp_secret_enc"
        private const val KEY_LAST_ACCEPTED_STEP = "totp_last_accepted_step"
        private const val KEY_FAILED_ATTEMPTS = "totp_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_ELAPSED = "totp_lockout_until_elapsed"
        private const val KEY_LOCKOUT_UNTIL_WALL = "totp_lockout_until_wall"

        private const val KEY_COOLDOWN_ACTIVE = "cooldown_active"
        private const val KEY_COOLDOWN_COMPLETED = "cooldown_completed"
        private const val KEY_COOLDOWN_DURATION = "cooldown_duration"
        private const val KEY_COOLDOWN_REMAINING = "cooldown_remaining"
        private const val KEY_COOLDOWN_START_WALL = "cooldown_start_wall"
        private const val KEY_LAST_CHECKED_ELAPSED = "cooldown_last_elapsed"
        private const val KEY_LAST_CHECKED_WALL = "cooldown_last_wall"
        private const val KEY_COOLDOWN_SIGNATURE = "cooldown_signature"
        private const val KEY_DEVICE_INTEGRITY_KEY_LEGACY = "device_integrity_key"
        private const val KEY_DEVICE_INTEGRITY_KEY_ENCRYPTED = "device_integrity_key_enc"

        private const val KEY_BYPASS_ACTIVE = "bypass_active"
        private const val KEY_BYPASS_DURATION = "bypass_duration"
        private const val KEY_BYPASS_REMAINING = "bypass_remaining"
        private const val KEY_BYPASS_LAST_ELAPSED = "bypass_last_elapsed"
        private const val KEY_BYPASS_LAST_WALL = "bypass_last_wall"
        private const val KEY_BYPASS_SIGNATURE = "bypass_signature"

        @Volatile
        private var cachedBypassActive: Boolean = false

        fun isBypassActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_BYPASS_ACTIVE, false)
        }
    }
}
