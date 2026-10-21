package com.zevbuild.zevblock.core.stats

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.zevbuild.zevblock.core.security.TamperLockManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ProtectionStatsManager:
 * Persists, aggregates, and visualizes daily and weekly protection activity.
 *
 * Security Principles:
 * 1. Tamper-Proof Tracking: Every DNS sinkhole event, screen intercept, incognito attempt,
 *    and chat warning is permanently aggregated into daily records.
 * 2. Immutable History: Statistics cannot be reset without Partner TOTP verification.
 * 3. Streak Integrity: Calculates unbroken days of protection and clean accountability.
 */
class ProtectionStatsManager(
    context: Context,
    private val tamperLockManager: TamperLockManager
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val formatLock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayNameFormat = SimpleDateFormat("EEE", Locale.US)

    private val _past7Days = MutableStateFlow<List<DayStat>>(emptyList())
    val past7Days: StateFlow<List<DayStat>> = _past7Days.asStateFlow()

    private val _todayStat = MutableStateFlow(DayStat("Today", getTodayDateString(), 0, 0, 0, 0, 0, 0))
    val todayStat: StateFlow<DayStat> = _todayStat.asStateFlow()

    private val _allTimeTotalBlocks = MutableStateFlow(0L)
    val allTimeTotalBlocks: StateFlow<Long> = _allTimeTotalBlocks.asStateFlow()

    private val _streakDays = MutableStateFlow(1)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private var pendingTotalQueries: Long = 0L
    private var pendingDnsBlocked: Long = 0L
    private var pendingSafeSearch: Long = 0L
    private var lastDnsFlushTime: Long = 0L

    init {
        ensureStreakInitialized()
        refreshStats()
    }

    private fun getTodayDateString(): String {
        return synchronized(formatLock) {
            dateFormat.format(Date())
        }
    }

    private fun ensureStreakInitialized() {
        if (!prefs.contains(KEY_STREAK_START_TIMESTAMP)) {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_STREAK_START_TIMESTAMP, now)
                .putLong(KEY_FIRST_INSTALL_TIMESTAMP, now)
                .apply()
        }
    }

    @Synchronized
    fun recordDnsQuery(isBlocked: Boolean, isSafeSearch: Boolean) {
        pendingTotalQueries++
        if (isBlocked) {
            pendingDnsBlocked++
        }
        if (isSafeSearch) {
            pendingSafeSearch++
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastDnsFlushTime >= DNS_FLUSH_INTERVAL_MS) {
            flushDnsCounters()
        }
    }

    @Synchronized
    fun flushDnsCounters() {
        lastDnsFlushTime = SystemClock.elapsedRealtime()
        if (pendingTotalQueries == 0L && pendingDnsBlocked == 0L && pendingSafeSearch == 0L) {
            return
        }

        val totalToAdd = pendingTotalQueries
        val blockedToAdd = pendingDnsBlocked
        val safeSearchToAdd = pendingSafeSearch

        pendingTotalQueries = 0L
        pendingDnsBlocked = 0L
        pendingSafeSearch = 0L

        val today = getTodayDateString()
        val record = getDayRecord(today)

        record.totalQueries += totalToAdd
        record.dnsBlocked += blockedToAdd
        record.safeSearchRewrites += safeSearchToAdd

        if (totalToAdd > 0L) {
            addCumulative(KEY_CUMULATIVE_TOTAL_QUERIES, totalToAdd)
        }
        if (blockedToAdd > 0L) {
            addCumulative(KEY_CUMULATIVE_DNS_BLOCKED, blockedToAdd)
        }
        if (safeSearchToAdd > 0L) {
            addCumulative(KEY_CUMULATIVE_SAFESEARCH, safeSearchToAdd)
        }

        saveDayRecord(today, record)
        refreshStats()
    }

    @Synchronized
    fun recordScreenBlock() {
        if (pendingTotalQueries > 0L || pendingDnsBlocked > 0L || pendingSafeSearch > 0L) {
            flushDnsCounters()
        }
        val today = getTodayDateString()
        val record = getDayRecord(today)
        record.screenNsfwBlocked++
        incrementCumulative(KEY_CUMULATIVE_SCREEN_NSFW)
        saveDayRecord(today, record)
        refreshStats()
    }

    @Synchronized
    fun recordIncognitoBlock() {
        if (pendingTotalQueries > 0L || pendingDnsBlocked > 0L || pendingSafeSearch > 0L) {
            flushDnsCounters()
        }
        val today = getTodayDateString()
        val record = getDayRecord(today)
        record.incognitoBlocked++
        incrementCumulative(KEY_CUMULATIVE_INCOGNITO)
        saveDayRecord(today, record)
        refreshStats()
    }

    @Synchronized
    fun recordChatWarning() {
        if (pendingTotalQueries > 0L || pendingDnsBlocked > 0L || pendingSafeSearch > 0L) {
            flushDnsCounters()
        }
        val today = getTodayDateString()
        val record = getDayRecord(today)
        record.chatWarnings++
        incrementCumulative(KEY_CUMULATIVE_CHAT_WARNINGS)
        saveDayRecord(today, record)
        refreshStats()
    }

    @Synchronized
    fun recordSettingsTamper() {
        if (pendingTotalQueries > 0L || pendingDnsBlocked > 0L || pendingSafeSearch > 0L) {
            flushDnsCounters()
        }
        val today = getTodayDateString()
        val record = getDayRecord(today)
        record.settingsTamperBlocked++
        incrementCumulative(KEY_CUMULATIVE_SETTINGS_TAMPER)
        saveDayRecord(today, record)
        refreshStats()
    }

    @Synchronized
    fun refreshStats() {
        val pastDaysList = mutableListOf<DayStat>()
        val todayStr = getTodayDateString()

        // Generate past 7 days (from 6 days ago up to today)
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = synchronized(formatLock) { dateFormat.format(cal.time) }
            val dayName = if (dateStr == todayStr) "Today" else synchronized(formatLock) { dayNameFormat.format(cal.time) }

            val rec = getDayRecord(dateStr)
            val totalBlocks = rec.dnsBlocked + rec.screenNsfwBlocked + rec.incognitoBlocked + rec.chatWarnings + rec.settingsTamperBlocked

            val stat = DayStat(
                dayLabel = dayName,
                dateStr = dateStr,
                totalBlocks = totalBlocks,
                dnsBlocked = rec.dnsBlocked,
                screenBlocked = rec.screenNsfwBlocked,
                incognitoBlocked = rec.incognitoBlocked,
                chatWarnings = rec.chatWarnings,
                settingsBlocked = rec.settingsTamperBlocked
            )
            pastDaysList.add(stat)

            if (dateStr == todayStr) {
                _todayStat.value = stat
            }
        }

        _past7Days.value = pastDaysList

        // Calculate all-time blocks
        val dns = prefs.getLong(KEY_CUMULATIVE_DNS_BLOCKED, 0L)
        val screen = prefs.getLong(KEY_CUMULATIVE_SCREEN_NSFW, 0L)
        val incognito = prefs.getLong(KEY_CUMULATIVE_INCOGNITO, 0L)
        val chat = prefs.getLong(KEY_CUMULATIVE_CHAT_WARNINGS, 0L)
        val settings = prefs.getLong(KEY_CUMULATIVE_SETTINGS_TAMPER, 0L)
        _allTimeTotalBlocks.value = dns + screen + incognito + chat + settings

        // Calculate Protection Streak in Days
        val streakStart = prefs.getLong(KEY_STREAK_START_TIMESTAMP, System.currentTimeMillis())
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - streakStart) + 1
        _streakDays.value = daysElapsed.toInt().coerceAtLeast(1)
    }

    /**
     * Resets statistics only upon successful Partner TOTP authorization.
     */
    fun resetStatsWithPartner(totpCode: String): Boolean {
        if (!tamperLockManager.verifyPartnerTotp(totpCode)) {
            Log.w(TAG, "Unauthorized attempt to reset protection statistics")
            return false
        }

        pendingTotalQueries = 0L
        pendingDnsBlocked = 0L
        pendingSafeSearch = 0L
        lastDnsFlushTime = 0L

        prefs.edit()
            .clear()
            .putLong(KEY_STREAK_START_TIMESTAMP, System.currentTimeMillis())
            .putLong(KEY_FIRST_INSTALL_TIMESTAMP, System.currentTimeMillis())
            .apply()

        refreshStats()
        Log.i(TAG, "Protection statistics successfully reset with Partner TOTP verification.")
        return true
    }

    fun addCumulative(key: String, by: Long) {
        if (by <= 0L) return
        val current = prefs.getLong(key, 0L)
        prefs.edit().putLong(key, current + by).apply()
    }

    private fun incrementCumulative(key: String) {
        addCumulative(key, 1L)
    }

    private fun getDayRecord(dateStr: String): RawDayRecord {
        val raw = prefs.getString("stats_$dateStr", null)
        if (raw == null) {
            return RawDayRecord(dateStr)
        }
        return try {
            val json = JSONObject(raw)
            RawDayRecord(
                dateStr = dateStr,
                totalQueries = json.optLong("totalQueries", 0L),
                dnsBlocked = json.optLong("dnsBlocked", 0L),
                safeSearchRewrites = json.optLong("safeSearchRewrites", 0L),
                screenNsfwBlocked = json.optLong("screenNsfwBlocked", 0L),
                incognitoBlocked = json.optLong("incognitoBlocked", 0L),
                chatWarnings = json.optLong("chatWarnings", 0L),
                settingsTamperBlocked = json.optLong("settingsTamperBlocked", 0L)
            )
        } catch (e: Exception) {
            RawDayRecord(dateStr)
        }
    }

    private fun saveDayRecord(dateStr: String, record: RawDayRecord) {
        try {
            val json = JSONObject().apply {
                put("date", record.dateStr)
                put("totalQueries", record.totalQueries)
                put("dnsBlocked", record.dnsBlocked)
                put("safeSearchRewrites", record.safeSearchRewrites)
                put("screenNsfwBlocked", record.screenNsfwBlocked)
                put("incognitoBlocked", record.incognitoBlocked)
                put("chatWarnings", record.chatWarnings)
                put("settingsTamperBlocked", record.settingsTamperBlocked)
            }
            prefs.edit().putString("stats_$dateStr", json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save daily record for $dateStr", e)
        }
    }

    companion object {
        private const val TAG = "ProtectionStatsManager"
        private const val PREFS_NAME = "zevblock_protection_stats_prefs"

        private const val DNS_FLUSH_INTERVAL_MS = 10_000L // 10 seconds

        private const val KEY_STREAK_START_TIMESTAMP = "streak_start_timestamp"
        private const val KEY_FIRST_INSTALL_TIMESTAMP = "first_install_timestamp"

        private const val KEY_CUMULATIVE_TOTAL_QUERIES = "cum_total_queries"
        private const val KEY_CUMULATIVE_DNS_BLOCKED = "cum_dns_blocked"
        private const val KEY_CUMULATIVE_SAFESEARCH = "cum_safe_search"
        private const val KEY_CUMULATIVE_SCREEN_NSFW = "cum_screen_nsfw"
        private const val KEY_CUMULATIVE_INCOGNITO = "cum_incognito"
        private const val KEY_CUMULATIVE_CHAT_WARNINGS = "cum_chat_warnings"
        private const val KEY_CUMULATIVE_SETTINGS_TAMPER = "cum_settings_tamper"
    }
}

data class DayStat(
    val dayLabel: String,
    val dateStr: String,
    val totalBlocks: Long,
    val dnsBlocked: Long,
    val screenBlocked: Long,
    val incognitoBlocked: Long,
    val chatWarnings: Long,
    val settingsBlocked: Long
)

internal data class RawDayRecord(
    val dateStr: String,
    var totalQueries: Long = 0L,
    var dnsBlocked: Long = 0L,
    var safeSearchRewrites: Long = 0L,
    var screenNsfwBlocked: Long = 0L,
    var incognitoBlocked: Long = 0L,
    var chatWarnings: Long = 0L,
    var settingsTamperBlocked: Long = 0L
)
