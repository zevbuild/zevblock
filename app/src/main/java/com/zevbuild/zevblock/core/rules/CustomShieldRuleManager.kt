package com.zevbuild.zevblock.core.rules

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.zevbuild.zevblock.core.accessibility.NsfwContentDetector
import com.zevbuild.zevblock.core.dns.DomainBlocklistMatcher
import com.zevbuild.zevblock.core.security.TamperLockManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * CustomShieldRuleManager:
 * Manages user-defined custom blocked domains and keywords with a zero-bypass guarantee.
 *
 * Security Principles:
 * 1. Additive Shield: Users can add trigger words or distracting/harmful domains at any time.
 * 2. Permanent Baseline: Built-in adult blocklists and keywords can NEVER be unblocked or removed.
 * 3. Dual-Protection Removal: Removing a custom rule requires EITHER:
 *    - Valid 6-digit Partner TOTP authorization, OR
 *    - A 24-hour hardware-clock cooldown delay to prevent impulsive relapse.
 */
class CustomShieldRuleManager(
    context: Context,
    private val tamperLockManager: TamperLockManager,
    private val blocklistMatcher: DomainBlocklistMatcher,
    private val nsfwContentDetector: NsfwContentDetector
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _customDomains = MutableStateFlow<Set<String>>(emptySet())
    val customDomains: StateFlow<Set<String>> = _customDomains.asStateFlow()

    private val _customKeywords = MutableStateFlow<Set<String>>(emptySet())
    val customKeywords: StateFlow<Set<String>> = _customKeywords.asStateFlow()

    private val _pendingRemovals = MutableStateFlow<List<PendingRemovalItem>>(emptyList())
    val pendingRemovals: StateFlow<List<PendingRemovalItem>> = _pendingRemovals.asStateFlow()

    init {
        loadPersistedRules()
    }

    /**
     * Loads saved custom rules from disk and synchronizes with DNS sinkhole and screen scraper.
     */
    fun loadPersistedRules() {
        val domainSet = prefs.getStringSet(KEY_CUSTOM_DOMAINS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val keywordSet = prefs.getStringSet(KEY_CUSTOM_KEYWORDS, emptySet())?.toMutableSet() ?: mutableSetOf()

        _customDomains.value = domainSet.toSet()
        _customKeywords.value = keywordSet.toSet()

        // Sync with active filter engines
        blocklistMatcher.setCustomBlockedDomains(domainSet)
        nsfwContentDetector.setCustomKeywords(keywordSet)

        loadPendingRemovals()
        Log.i(TAG, "Loaded ${domainSet.size} custom domains and ${keywordSet.size} custom keywords.")
    }

    /**
     * Adds a custom domain to the DNS sinkhole.
     * Prevents adding duplicates or overriding built-in system adult rules.
     */
    fun addDomain(rawDomain: String): RuleOperationResult {
        val clean = rawDomain.lowercase().trim().trimEnd('.').removePrefix("https://").removePrefix("http://")
        if (clean.length < 3 || !clean.contains('.')) {
            return RuleOperationResult.InvalidInput("Please enter a valid domain format (e.g., example.com)")
        }

        // Built-in adult domains are permanently blocked by baseline OS safeguards
        if (blocklistMatcher.isHardcodedAdultDomain(clean)) {
            return RuleOperationResult.ProtectedBySystem("Domain '$clean' is already permanently blocked by ZevBlock system safeguards.")
        }

        val current = _customDomains.value.toMutableSet()
        if (current.contains(clean)) {
            return RuleOperationResult.AlreadyExists("Domain '$clean' is already in your custom blocklist.")
        }

        current.add(clean)
        prefs.edit().putStringSet(KEY_CUSTOM_DOMAINS, current).apply()
        _customDomains.value = current.toSet()
        blocklistMatcher.setCustomBlockedDomains(current)

        Log.i(TAG, "Added custom blocked domain: $clean")
        return RuleOperationResult.Success("Successfully blocked domain '$clean'")
    }

    /**
     * Adds a custom keyword for on-screen inspection and chat detection.
     */
    fun addKeyword(rawKeyword: String): RuleOperationResult {
        val clean = rawKeyword.lowercase().trim()
        if (clean.length < 2) {
            return RuleOperationResult.InvalidInput("Keyword must be at least 2 characters long.")
        }

        if (nsfwContentDetector.isBuiltInKeyword(clean)) {
            return RuleOperationResult.ProtectedBySystem("Keyword '$clean' is already permanently filtered by baseline safeguards.")
        }

        val current = _customKeywords.value.toMutableSet()
        if (current.contains(clean)) {
            return RuleOperationResult.AlreadyExists("Keyword '$clean' is already in your custom keyword shield.")
        }

        current.add(clean)
        prefs.edit().putStringSet(KEY_CUSTOM_KEYWORDS, current).apply()
        _customKeywords.value = current.toSet()
        nsfwContentDetector.setCustomKeywords(current)

        Log.i(TAG, "Added custom blocked keyword: $clean")
        return RuleOperationResult.Success("Successfully added keyword '$clean' to screen and chat shield.")
    }

    /**
     * Removes a custom domain immediately using Partner TOTP authentication.
     */
    fun removeDomainWithTotp(domain: String, totpCode: String): RuleOperationResult {
        if (!tamperLockManager.verifyPartnerTotp(totpCode)) {
            return RuleOperationResult.TotpFailed("Invalid Partner TOTP code. Removal denied.")
        }
        executeDomainRemoval(domain)
        cancelPendingRemoval(domain)
        return RuleOperationResult.Success("Partner verified: Domain '$domain' removed.")
    }

    /**
     * Removes a custom keyword immediately using Partner TOTP authentication.
     */
    fun removeKeywordWithTotp(keyword: String, totpCode: String): RuleOperationResult {
        if (!tamperLockManager.verifyPartnerTotp(totpCode)) {
            return RuleOperationResult.TotpFailed("Invalid Partner TOTP code. Removal denied.")
        }
        executeKeywordRemoval(keyword)
        cancelPendingRemoval(keyword)
        return RuleOperationResult.Success("Partner verified: Keyword '$keyword' removed.")
    }

    /**
     * Starts a 24-hour cooldown delay before a custom domain or keyword can be removed.
     */
    fun requestCooldownRemoval(item: String, type: RuleType): RuleOperationResult {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val removals = getPendingRemovalsMap().toMutableMap()

        if (removals.containsKey(item)) {
            val existing = removals[item]!!
            val (remaining, updated) = updateRemainingTime(existing)
            removals[item] = updated
            savePendingRemovalsMap(removals)
            refreshPendingRemovals()
            return RuleOperationResult.CooldownStarted(item, remaining)
        }

        val duration = COOLDOWN_DURATION_MS
        val newRecord = PendingRemovalRecord(
            item = item,
            type = type.name,
            requestedAtElapsed = nowElapsed,
            requestedAtWall = nowWall,
            lastCheckedElapsed = nowElapsed,
            lastCheckedWall = nowWall,
            remainingMs = duration,
            durationMs = duration
        )
        removals[item] = newRecord
        savePendingRemovalsMap(removals)
        refreshPendingRemovals()

        Log.i(TAG, "Started 24-hour tamper-proof cooldown removal for $type: $item")
        return RuleOperationResult.CooldownStarted(item, duration)
    }

    /**
     * Confirms and finalizes removal after the 24-hour cooldown has completely expired.
     */
    fun finalizeCooldownRemoval(item: String): RuleOperationResult {
        val removals = getPendingRemovalsMap().toMutableMap()
        val record = removals[item]
            ?: return RuleOperationResult.InvalidInput("No active cooldown found for '$item'.")

        val (remaining, updated) = updateRemainingTime(record)
        if (remaining > 0) {
            removals[item] = updated
            savePendingRemovalsMap(removals)
            refreshPendingRemovals()
            return RuleOperationResult.CooldownStarted(item, remaining)
        }

        if (record.type == RuleType.DOMAIN.name) {
            executeDomainRemoval(item)
        } else {
            executeKeywordRemoval(item)
        }

        removals.remove(item)
        savePendingRemovalsMap(removals)
        refreshPendingRemovals()

        Log.i(TAG, "Finalized cooldown removal for ${record.type}: $item")
        return RuleOperationResult.Success("Cooldown expired: '$item' has been removed.")
    }

    /**
     * Cancels a pending 24-hour cooldown removal request, keeping the shield intact.
     */
    fun cancelPendingRemoval(item: String) {
        val removals = getPendingRemovalsMap().toMutableMap()
        if (removals.remove(item) != null) {
            savePendingRemovalsMap(removals)
            refreshPendingRemovals()
            Log.i(TAG, "Cancelled pending cooldown removal for: $item")
        }
    }

    fun refreshPendingRemovals() {
        val map = getPendingRemovalsMap().toMutableMap()
        var changed = false
        val list = map.values.map { record ->
            val (remaining, updated) = updateRemainingTime(record)
            if (updated.remainingMs != record.remainingMs || updated.lastCheckedElapsed != record.lastCheckedElapsed) {
                map[record.item] = updated
                changed = true
            }
            PendingRemovalItem(
                item = record.item,
                type = record.type,
                requestedAtRealtime = record.requestedAtElapsed,
                durationMs = record.durationMs,
                remainingMs = remaining,
                isReady = remaining <= 0L
            )
        }
        if (changed) {
            savePendingRemovalsMap(map)
        }
        _pendingRemovals.value = list
    }

    private fun executeDomainRemoval(domain: String) {
        val current = _customDomains.value.toMutableSet()
        current.remove(domain)
        prefs.edit().putStringSet(KEY_CUSTOM_DOMAINS, current).apply()
        _customDomains.value = current.toSet()
        blocklistMatcher.setCustomBlockedDomains(current)
    }

    private fun executeKeywordRemoval(keyword: String) {
        val current = _customKeywords.value.toMutableSet()
        current.remove(keyword)
        prefs.edit().putStringSet(KEY_CUSTOM_KEYWORDS, current).apply()
        _customKeywords.value = current.toSet()
        nsfwContentDetector.setCustomKeywords(current)
    }

    /**
     * Accurately updates elapsed cooldown progress across device reboots and deep sleep.
     * Prevents time tampering by using monotonic hardware clock during uptime and
     * validating wall-clock jumps if a reboot occurred.
     */
    private fun updateRemainingTime(record: PendingRemovalRecord): Pair<Long, PendingRemovalRecord> {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        var remaining = record.remainingMs

        if (nowElapsed < record.lastCheckedElapsed) {
            // Hardware clock reset detected: phone was rebooted
            val deltaWall = nowWall - record.lastCheckedWall
            if (deltaWall in 1..MAX_SANE_WALL_CLOCK_JUMP_MS) {
                remaining = (remaining - deltaWall).coerceAtLeast(0L)
                Log.i(TAG, "Reboot detected for '${record.item}'. Credited $deltaWall ms of offline time.")
            } else if (deltaWall < 0) {
                // System clock rolled backward! Zero credit.
                Log.w(TAG, "System clock rollback detected for '${record.item}'. Zero time credited.")
            }
        } else {
            // Standard monotonic clock progression without reboot
            val deltaElapsed = nowElapsed - record.lastCheckedElapsed
            remaining = (remaining - deltaElapsed).coerceAtLeast(0L)
        }

        val updatedRecord = record.copy(
            lastCheckedElapsed = nowElapsed,
            lastCheckedWall = nowWall,
            remainingMs = remaining
        )
        return Pair(remaining, updatedRecord)
    }

    private fun getPendingRemovalsMap(): Map<String, PendingRemovalRecord> {
        val rawJson = prefs.getString(KEY_PENDING_REMOVALS, "{}") ?: "{}"
        val result = mutableMapOf<String, PendingRemovalRecord>()
        try {
            val root = JSONObject(rawJson)
            val keys = root.keys()
            val nowElapsed = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()

            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.getJSONObject(key)
                val item = obj.getString("item")
                val type = obj.getString("type")
                val durationMs = obj.optLong("durationMs", COOLDOWN_DURATION_MS)

                // Backward-compatible fallback reading
                val reqElapsed = obj.optLong("requestedAtElapsed", obj.optLong("requestedAt", nowElapsed))
                val reqWall = obj.optLong("requestedAtWall", nowWall)
                val lastElapsed = obj.optLong("lastCheckedElapsed", reqElapsed)
                val lastWall = obj.optLong("lastCheckedWall", reqWall)
                val remMs = obj.optLong("remainingMs", durationMs)

                result[key] = PendingRemovalRecord(
                    item = item,
                    type = type,
                    requestedAtElapsed = reqElapsed,
                    requestedAtWall = reqWall,
                    lastCheckedElapsed = lastElapsed,
                    lastCheckedWall = lastWall,
                    remainingMs = remMs,
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing pending removals JSON", e)
        }
        return result
    }

    private fun savePendingRemovalsMap(map: Map<String, PendingRemovalRecord>) {
        try {
            val root = JSONObject()
            for ((key, record) in map) {
                val obj = JSONObject().apply {
                    put("item", record.item)
                    put("type", record.type)
                    put("requestedAtElapsed", record.requestedAtElapsed)
                    put("requestedAtWall", record.requestedAtWall)
                    put("lastCheckedElapsed", record.lastCheckedElapsed)
                    put("lastCheckedWall", record.lastCheckedWall)
                    put("remainingMs", record.remainingMs)
                    put("durationMs", record.durationMs)
                    // Legacy field compatibility
                    put("requestedAt", record.requestedAtElapsed)
                }
                root.put(key, obj)
            }
            prefs.edit().putString(KEY_PENDING_REMOVALS, root.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pending removals JSON", e)
        }
    }

    private fun loadPendingRemovals() {
        refreshPendingRemovals()
    }

    companion object {
        private const val TAG = "CustomShieldRuleManager"
        private const val PREFS_NAME = "zevblock_custom_rules_prefs"
        private const val KEY_CUSTOM_DOMAINS = "custom_domains_set"
        private const val KEY_CUSTOM_KEYWORDS = "custom_keywords_set"
        private const val KEY_PENDING_REMOVALS = "pending_removals_json"

        // 24 Hours in Milliseconds
        const val COOLDOWN_DURATION_MS = 24 * 60 * 60 * 1000L
        private const val MAX_SANE_WALL_CLOCK_JUMP_MS = 7 * 24 * 3600 * 1000L // 7 days
    }
}

enum class RuleType {
    DOMAIN,
    KEYWORD
}

data class PendingRemovalRecord(
    val item: String,
    val type: String,
    val requestedAtElapsed: Long,
    val requestedAtWall: Long,
    val lastCheckedElapsed: Long,
    val lastCheckedWall: Long,
    val remainingMs: Long,
    val durationMs: Long
) {
    val requestedAt: Long get() = requestedAtElapsed
}

data class PendingRemovalItem(
    val item: String,
    val type: String,
    val requestedAtRealtime: Long,
    val durationMs: Long,
    val remainingMs: Long,
    val isReady: Boolean
)

sealed class RuleOperationResult {
    data class Success(val message: String) : RuleOperationResult()
    data class AlreadyExists(val message: String) : RuleOperationResult()
    data class ProtectedBySystem(val message: String) : RuleOperationResult()
    data class InvalidInput(val message: String) : RuleOperationResult()
    data class CooldownStarted(val item: String, val remainingMs: Long) : RuleOperationResult()
    data class TotpFailed(val message: String) : RuleOperationResult()
}
