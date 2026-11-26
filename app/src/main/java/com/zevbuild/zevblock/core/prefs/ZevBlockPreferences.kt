package com.zevbuild.zevblock.core.prefs

import android.content.Context
import android.content.SharedPreferences
import com.zevbuild.zevblock.accessibility.ShieldAccessibilityService
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ZevBlockPreferences:
 * Stores strictly non-identifying UI, notification, and guard configuration flags.
 * Per privacy rules, NEVER persists visited domains, URLs, search queries, or chat text.
 */
class ZevBlockPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _notificationMode = MutableStateFlow(prefs.getString(KEY_NOTIFICATION_MODE, NOTIF_CLEAN) ?: NOTIF_CLEAN)
    val notificationMode: StateFlow<String> = _notificationMode.asStateFlow()

    private val _devLabEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEV_LAB_ENABLED, false))
    val devLabEnabled: StateFlow<Boolean> = _devLabEnabled.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _incognitoBlockEnabled = MutableStateFlow(prefs.getBoolean(KEY_INCOGNITO_BLOCK, true))
    val incognitoBlockEnabled: StateFlow<Boolean> = _incognitoBlockEnabled.asStateFlow()

    private val _nsfwFilterEnabled = MutableStateFlow(prefs.getBoolean(KEY_NSFW_FILTER, true))
    val nsfwFilterEnabled: StateFlow<Boolean> = _nsfwFilterEnabled.asStateFlow()

    private val _chatWarningEnabled = MutableStateFlow(prefs.getBoolean(KEY_CHAT_WARNING, true))
    val chatWarningEnabled: StateFlow<Boolean> = _chatWarningEnabled.asStateFlow()

    private val _settingsGuardEnabled = MutableStateFlow(prefs.getBoolean(KEY_SETTINGS_GUARD, true))
    val settingsGuardEnabled: StateFlow<Boolean> = _settingsGuardEnabled.asStateFlow()

    private val _safeSearchEnabled = MutableStateFlow(prefs.getBoolean(KEY_SAFESEARCH_ENABLED, true))
    val safeSearchEnabled: StateFlow<Boolean> = _safeSearchEnabled.asStateFlow()

    init {
        applyToServices()
    }

    fun setThemeMode(mode: String) {
        val valid = when (mode) {
            THEME_LIGHT, THEME_SYSTEM, THEME_DARK -> mode
            else -> THEME_DARK
        }
        prefs.edit().putString(KEY_THEME_MODE, valid).apply()
        _themeMode.value = valid
    }

    fun setNotificationMode(mode: String) {
        val valid = if (mode == NOTIF_SPEED) NOTIF_SPEED else NOTIF_CLEAN
        prefs.edit().putString(KEY_NOTIFICATION_MODE, valid).apply()
        _notificationMode.value = valid
    }

    fun setDevLabEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_LAB_ENABLED, enabled).apply()
        _devLabEnabled.value = enabled
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
        _hasCompletedOnboarding.value = completed
    }

    fun setVpnExpectedActive(expected: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_EXPECTED_ACTIVE, expected).apply()
    }

    fun isVpnExpectedActive(): Boolean = prefs.getBoolean(KEY_VPN_EXPECTED_ACTIVE, false)

    fun setAccessibilityExpectedActive(expected: Boolean) {
        prefs.edit().putBoolean(KEY_A11Y_EXPECTED_ACTIVE, expected).apply()
    }

    fun isAccessibilityExpectedActive(): Boolean = prefs.getBoolean(KEY_A11Y_EXPECTED_ACTIVE, false)

    fun setIncognitoBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INCOGNITO_BLOCK, enabled).apply()
        _incognitoBlockEnabled.value = enabled
        ShieldAccessibilityService.isIncognitoBlockEnabled = enabled
    }

    fun setNsfwFilterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NSFW_FILTER, enabled).apply()
        _nsfwFilterEnabled.value = enabled
        ShieldAccessibilityService.isNsfwFilterEnabled = enabled
    }

    fun setChatWarningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHAT_WARNING, enabled).apply()
        _chatWarningEnabled.value = enabled
        ShieldAccessibilityService.isChatWarningEnabled = enabled
    }

    fun setSettingsGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SETTINGS_GUARD, enabled).apply()
        _settingsGuardEnabled.value = enabled
        ShieldAccessibilityService.isSettingsGuardEnabled = enabled
    }

    fun setSafeSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAFESEARCH_ENABLED, enabled).apply()
        _safeSearchEnabled.value = enabled
        DnsBlockerVpnService.safeSearchManager.isEnabled = enabled
    }

    fun applyToServices() {
        ShieldAccessibilityService.isIncognitoBlockEnabled = _incognitoBlockEnabled.value
        ShieldAccessibilityService.isNsfwFilterEnabled = _nsfwFilterEnabled.value
        ShieldAccessibilityService.isChatWarningEnabled = _chatWarningEnabled.value
        ShieldAccessibilityService.isSettingsGuardEnabled = _settingsGuardEnabled.value
        DnsBlockerVpnService.safeSearchManager.isEnabled = _safeSearchEnabled.value
    }

    companion object {
        const val PREFS_NAME = "zevblock_ui_preferences"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
        const val NOTIF_CLEAN = "clean"
        const val NOTIF_SPEED = "speed"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_NOTIFICATION_MODE = "notification_mode"
        private const val KEY_DEV_LAB_ENABLED = "dev_lab_enabled"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
        private const val KEY_VPN_EXPECTED_ACTIVE = "vpn_expected_active"
        private const val KEY_A11Y_EXPECTED_ACTIVE = "a11y_expected_active"
        private const val KEY_INCOGNITO_BLOCK = "incognito_block_enabled"
        private const val KEY_NSFW_FILTER = "nsfw_filter_enabled"
        private const val KEY_CHAT_WARNING = "chat_warning_enabled"
        private const val KEY_SETTINGS_GUARD = "settings_guard_enabled"
        private const val KEY_SAFESEARCH_ENABLED = "safesearch_enabled"

        fun applyFromStorage(context: Context) {
            ZevBlockPreferences(context).applyToServices()
        }

        fun getNotificationMode(context: Context): String {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_NOTIFICATION_MODE, NOTIF_CLEAN) ?: NOTIF_CLEAN
        }

        fun isVpnExpectedActive(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_VPN_EXPECTED_ACTIVE, false)
        }
    }
}
