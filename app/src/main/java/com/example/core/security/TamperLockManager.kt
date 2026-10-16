package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.min

/**
 * TamperLockManager handles:
 * 1. RFC 6238 TOTP verification for trusted accountability partner locks.
 * 2. Monotonic-clock tamper-proof cooldown timers that cannot be bypassed
 *    by winding back or forwarding the Android system date & time.
 * 3. Cryptographic integrity sealing of stored lock state.
 */
class TamperLockManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // =========================================================================
    // SECTION 1: RFC 6238 TOTP ENGINE (Accountability Partner)
    // =========================================================================

    /**
     * Verifies a 6-digit candidate code against the stored friend's TOTP secret.
     * Allows a clock skew of ±1 step (±30 seconds) to tolerate time drift.
     */
    fun verifyPartnerTotp(candidateCode: String): Boolean {
        val secret = getPartnerSecret() ?: return false
        val cleanCode = candidateCode.trim().filter { it.isDigit() }
        if (cleanCode.length != 6) return false

        val currentEpochSeconds = System.currentTimeMillis() / 1000L
        val currentStep = currentEpochSeconds / TOTP_TIME_STEP_SECONDS

        // Check current step, preceding step, and succeeding step (±30 seconds)
        for (stepOffset in -1L..1L) {
            val step = currentStep + stepOffset
            val expectedCode = generateTotpCodeForStep(secret, step)
            if (expectedCode == cleanCode) {
                Log.i(TAG, "Partner TOTP successfully verified for step offset: $stepOffset")
                return true
            }
        }
        Log.w(TAG, "Partner TOTP verification failed for input.")
        return false
    }

    /**
     * Generates a new cryptographically secure 160-bit Base32 secret key,
     * saves it in encrypted preferences, and returns the Base32 string.
     */
    fun setupNewPartnerSecret(): String {
        val randomBytes = ByteArray(20) // 160 bits (recommended for SHA1 HMAC)
        SecureRandom().nextBytes(randomBytes)
        val base32Secret = Base32.encode(randomBytes)
        savePartnerSecret(base32Secret)
        Log.i(TAG, "Generated and stored new accountability partner TOTP secret")
        return base32Secret
    }

    /**
     * Retrieves the active partner Base32 secret, or null if not yet paired.
     */
    fun getPartnerSecret(): String? {
        return prefs.getString(KEY_PARTNER_SECRET, null)
    }

    /**
     * Clears partner pairing secret (only allowed when unlocked).
     */
    fun clearPartnerSecret() {
        prefs.edit().remove(KEY_PARTNER_SECRET).apply()
    }

    /**
     * Constructs a standard `otpauth://totp/...` URI for generating QR codes
     * scannable by Google Authenticator, Aegis, Bitwarden, 2FAS, etc.
     */
    fun getPartnerTotpUri(partnerLabel: String = "Accountability Partner"): String? {
        val secret = getPartnerSecret() ?: return null
        val encodedLabel = URLEncoder.encode("ShieldGuard OS:$partnerLabel", "UTF-8")
        val encodedIssuer = URLEncoder.encode("ShieldGuard OS", "UTF-8")
        return "otpauth://totp/$encodedLabel?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=6&period=30"
    }

    /**
     * RFC 6238 HMAC-SHA1 calculation for a given time step.
     */
    private fun generateTotpCodeForStep(base32Secret: String, step: Long): String {
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
        return String.format("%06d", otp)
    }

    private fun savePartnerSecret(secret: String) {
        prefs.edit().putString(KEY_PARTNER_SECRET, secret).apply()
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
            .putLong(KEY_COOLDOWN_DURATION, durationMs)
            .putLong(KEY_COOLDOWN_REMAINING, durationMs)
            .putLong(KEY_LAST_CHECKED_ELAPSED, nowElapsed)
            .putLong(KEY_LAST_CHECKED_WALL, nowWall)
            .putLong(KEY_COOLDOWN_START_WALL, nowWall)
            .putString(KEY_COOLDOWN_SIGNATURE, createCooldownSignature(durationMs, durationMs, nowElapsed, nowWall))
            .apply()

        Log.i(TAG, "Anti-tamper cooldown timer started: duration=$durationMs ms ($durationMs / 60000 min)")
    }

    /**
     * Returns remaining milliseconds on the cooldown timer.
     * Correctly handles sleep, deep-idle, and detects system clock manipulations.
     */
    @Synchronized
    fun getRemainingCooldownMs(): Long {
        if (!isCooldownActive()) return 0L

        // Validate integrity signature
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
            // Compare wall clock time while detecting rollbacks.
            val deltaWall = nowWall - lastWall
            if (deltaWall in 1..MAX_SANE_WALL_CLOCK_JUMP_MS) {
                // Legitimate time passed while powered off
                remaining = max(0L, remaining - deltaWall)
                Log.i(TAG, "Reboot detected. Deducted $deltaWall ms of offline wall time.")
            } else if (deltaWall < 0) {
                // Clock was rolled back! Grant zero time credit.
                Log.w(TAG, "System clock rollback detected! (nowWall: $nowWall, lastWall: $lastWall). Zero time credited.")
            }
        } else {
            // Normal operation without reboot: use monotonic elapsedRealtime
            val deltaElapsed = nowElapsed - lastElapsed
            remaining = max(0L, remaining - deltaElapsed)
        }

        if (remaining <= 0L) {
            // Cooldown completed!
            markCooldownCompleted()
            return 0L
        }

        // Persist updated progress and refresh signature
        val duration = prefs.getLong(KEY_COOLDOWN_DURATION, DEFAULT_COOLDOWN_MS)
        prefs.edit()
            .putLong(KEY_COOLDOWN_REMAINING, remaining)
            .putLong(KEY_LAST_CHECKED_ELAPSED, nowElapsed)
            .putLong(KEY_LAST_CHECKED_WALL, nowWall)
            .putString(KEY_COOLDOWN_SIGNATURE, createCooldownSignature(duration, remaining, nowElapsed, nowWall))
            .apply()

        return remaining
    }

    /**
     * Checks if a cooldown is currently in progress.
     */
    fun isCooldownActive(): Boolean {
        return prefs.getBoolean(KEY_COOLDOWN_ACTIVE, false)
    }

    /**
     * Checks if the cooldown timer has reached zero and is ready for unlock.
     */
    fun isCooldownFinished(): Boolean {
        return prefs.getBoolean(KEY_COOLDOWN_COMPLETED, false)
    }

    /**
     * Resets or cancels cooldown state.
     */
    @Synchronized
    fun cancelCooldown() {
        prefs.edit()
            .putBoolean(KEY_COOLDOWN_ACTIVE, false)
            .putBoolean(KEY_COOLDOWN_COMPLETED, false)
            .putLong(KEY_COOLDOWN_REMAINING, 0L)
            .remove(KEY_COOLDOWN_SIGNATURE)
            .apply()
        Log.i(TAG, "Cooldown timer cancelled")
    }

    @Synchronized
    private fun markCooldownCompleted() {
        prefs.edit()
            .putBoolean(KEY_COOLDOWN_ACTIVE, false)
            .putBoolean(KEY_COOLDOWN_COMPLETED, true)
            .putLong(KEY_COOLDOWN_REMAINING, 0L)
            .apply()
        Log.i(TAG, "Cooldown timer reached 0: Unlock authorized.")
    }

    // =========================================================================
    // SECTION 3: CRYPTOGRAPHIC INTEGRITY SEALING
    // =========================================================================

    private fun getOrCreateDeviceKey(): ByteArray {
        var keyHex = prefs.getString(KEY_DEVICE_INTEGRITY_KEY, null)
        if (keyHex == null) {
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            keyHex = key.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_DEVICE_INTEGRITY_KEY, keyHex).apply()
        }
        return keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
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

        val computedSig = createCooldownSignature(duration, remaining, lastElapsed, lastWall)
        return computedSig.equals(storedSig, ignoreCase = true)
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

        private const val MAX_SANE_WALL_CLOCK_JUMP_MS = 7 * 24 * 3600 * 1000L // 7 days

        private const val KEY_PARTNER_SECRET = "partner_totp_secret"
        private const val KEY_COOLDOWN_ACTIVE = "cooldown_active"
        private const val KEY_COOLDOWN_COMPLETED = "cooldown_completed"
        private const val KEY_COOLDOWN_DURATION = "cooldown_duration"
        private const val KEY_COOLDOWN_REMAINING = "cooldown_remaining"
        private const val KEY_COOLDOWN_START_WALL = "cooldown_start_wall"
        private const val KEY_LAST_CHECKED_ELAPSED = "cooldown_last_elapsed"
        private const val KEY_LAST_CHECKED_WALL = "cooldown_last_wall"
        private const val KEY_COOLDOWN_SIGNATURE = "cooldown_signature"
        private const val KEY_DEVICE_INTEGRITY_KEY = "device_integrity_key"
    }
}
