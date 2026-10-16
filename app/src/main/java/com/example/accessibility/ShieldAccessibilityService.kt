package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.accessibility.BrowserUrlExtractor
import com.example.core.accessibility.NsfwContentDetector
import com.example.core.accessibility.SettingsTamperGuard
import com.example.core.security.TamperLockManager
import com.example.ui.overlay.OverlayManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * ShieldAccessibilityService:
 * Real-time window content scraper and anti-tamper enforcement service.
 * - Extracts URLs and search terms from Chrome, Firefox, Brave, and Edge address bars.
 * - Detects NSFW keywords, adult URLs, and explicit search queries.
 * - Blocks Incognito / Private browsing windows.
 * - Intercepts unauthorized access to com.android.settings (App Info, Accessibility, VPN, Device Admin).
 * - Triggers GLOBAL_ACTION_BACK with a Jetpack Compose security warning overlay.
 */
class ShieldAccessibilityService : AccessibilityService() {

    private val browserUrlExtractor = BrowserUrlExtractor()
    private val nsfwContentDetector = NsfwContentDetector()
    private val settingsTamperGuard = SettingsTamperGuard()

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceActive.value = true
        Log.i(TAG, "ShieldAccessibilityService successfully connected and active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Respect active partner temporary bypass session
        if (TamperLockManager.isBypassActive(this)) {
            return
        }

        try {
            val rootNode: AccessibilityNodeInfo? = rootInActiveWindow

            // =================================================================
            // 1. BROWSER INSPECTION: Incognito & NSFW URL Extraction
            // =================================================================
            if (browserUrlExtractor.isSupportedBrowser(packageName)) {
                // A. Check for Incognito / Private Window
                if (isIncognitoBlockEnabled && browserUrlExtractor.isIncognitoOrPrivateWindow(packageName, rootNode)) {
                    Log.w(TAG, "Incognito mode detected in $packageName! Executing back action.")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val incognitoCount = _incognitoBlocks.incrementAndGet()
                    incognitoBlocksCount.value = incognitoCount
                    logEvent(packageName, "Incognito Window Blocked", "Private browsing is disabled")
                    OverlayManager.triggerWarning(
                        this,
                        alertType = "Incognito Mode Blocked",
                        message = "Private and Incognito browsing is strictly prohibited under ZevBlock system safeguards.",
                        detail = "Package: $packageName"
                    )
                    rootNode?.recycle()
                    return
                }

                // B. Extract URL / Search text & Check for NSFW content
                val extractedUrl = browserUrlExtractor.extractUrl(rootNode)
                if (!extractedUrl.isNullOrBlank() && isNsfwFilterEnabled) {
                    val nsfwResult = nsfwContentDetector.analyze(extractedUrl)
                    if (nsfwResult.isNsfw) {
                        Log.w(TAG, "NSFW content detected: '${nsfwResult.matchedTerm}' (${nsfwResult.category}) in URL: $extractedUrl")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        val nsfwCount = _nsfwBlocks.incrementAndGet()
                        nsfwBlocksCount.value = nsfwCount
                        logEvent(packageName, "NSFW Content Blocked", "Matched: ${nsfwResult.matchedTerm}")
                        OverlayManager.triggerWarning(
                            this,
                            alertType = "Explicit Content Blocked",
                            message = "Adult content, explicit keyword, or search query detected: '${nsfwResult.matchedTerm}'.",
                            detail = extractedUrl
                        )
                        rootNode?.recycle()
                        return
                    }
                }
            }

            // =================================================================
            // 2. SETTINGS GUARD: Prevent App Info, VPN, Admin, or Accessibility tampering
            // =================================================================
            if (isSettingsGuardEnabled && SettingsTamperGuard.SETTINGS_PACKAGES.contains(packageName)) {
                val tamperEvent = settingsTamperGuard.inspect(packageName, event.className?.toString(), rootNode)
                if (tamperEvent != null) {
                    Log.w(TAG, "Settings tamper detected: ${tamperEvent.target} - ${tamperEvent.reason}")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val settingsCount = _settingsBlocks.incrementAndGet()
                    settingsBlocksCount.value = settingsCount
                    logEvent(packageName, "Settings Tamper Blocked", tamperEvent.target)
                    OverlayManager.triggerWarning(
                        this,
                        alertType = "Settings Access Restricted",
                        message = tamperEvent.reason,
                        detail = tamperEvent.target
                    )
                    rootNode?.recycle()
                    return
                }
            }

            // =================================================================
            // 3. SYSTEM-WIDE ADULT CONTENT INSPECTION across any app
            // =================================================================
            if (isNsfwFilterEnabled && !isExemptPackage(packageName)) {
                // Check if the window is an unlisted browser with an address bar
                val extractedUrl = browserUrlExtractor.extractUrl(rootNode)
                if (!extractedUrl.isNullOrBlank()) {
                    val nsfwResult = nsfwContentDetector.analyze(extractedUrl)
                    if (nsfwResult.isNsfw) {
                        Log.w(TAG, "NSFW URL in unlisted browser/app $packageName: '${nsfwResult.matchedTerm}'")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        val nsfwCount = _nsfwBlocks.incrementAndGet()
                        nsfwBlocksCount.value = nsfwCount
                        logEvent(packageName, "Adult Content Blocked", "Matched: ${nsfwResult.matchedTerm}")
                        OverlayManager.triggerWarning(
                            this,
                            alertType = "Explicit Content Blocked",
                            message = "System-level adult content filter intercepted explicit URL: '${nsfwResult.matchedTerm}'.",
                            detail = extractedUrl
                        )
                        rootNode?.recycle()
                        return
                    }
                } else {
                    // Inspect on-screen window text for explicit adult titles or search results
                    val windowNsfw = inspectWindowForNsfw(rootNode, depth = 0)
                    if (windowNsfw != null) {
                        Log.w(TAG, "On-screen adult content detected in $packageName: '${windowNsfw.matchedTerm}'")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        val nsfwCount = _nsfwBlocks.incrementAndGet()
                        nsfwBlocksCount.value = nsfwCount
                        logEvent(packageName, "On-Screen Adult Content Blocked", "Matched: ${windowNsfw.matchedTerm}")
                        OverlayManager.triggerWarning(
                            this,
                            alertType = "Explicit Content Blocked",
                            message = "System safeguard intercepted explicit adult material on screen: '${windowNsfw.matchedTerm}'.",
                            detail = "App: $packageName"
                        )
                        rootNode?.recycle()
                        return
                    }
                }
            }

            rootNode?.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error in accessibility event processing", e)
        }
    }

    private fun isExemptPackage(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName == "com.android.systemui") return true
        if (packageName.contains("launcher") || packageName.contains("inputmethod")) return true
        return false
    }

    private fun inspectWindowForNsfw(node: AccessibilityNodeInfo?, depth: Int): com.example.core.accessibility.NsfwDetectionResult? {
        if (node == null || depth > 5) return null

        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            val res = nsfwContentDetector.analyze(text)
            if (res.isNsfw) return res
        }

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            val res = nsfwContentDetector.analyze(desc)
            if (res.isNsfw) return res
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = inspectWindowForNsfw(child, depth + 1)
            child.recycle()
            if (found != null) return found
        }

        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "ShieldAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        Log.i(TAG, "ShieldAccessibilityService destroyed")
    }

    private fun logEvent(packageName: String, title: String, detail: String) {
        val entry = AccessibilityLogEntry(
            packageName = packageName,
            actionTitle = title,
            detail = detail
        )
        val current = _recentEvents.value.toMutableList()
        current.add(0, entry)
        if (current.size > MAX_RECENT_EVENTS) {
            current.removeAt(current.size - 1)
        }
        _recentEvents.value = current
    }

    companion object {
        const val TAG = "ShieldAccessibility"
        private const val MAX_RECENT_EVENTS = 40

        // Configurable Guard Toggles
        var isIncognitoBlockEnabled: Boolean = true
        var isNsfwFilterEnabled: Boolean = true
        var isSettingsGuardEnabled: Boolean = true

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _incognitoBlocks = AtomicLong(0L)
        val incognitoBlocksCount = MutableStateFlow(0L)

        private val _nsfwBlocks = AtomicLong(0L)
        val nsfwBlocksCount = MutableStateFlow(0L)

        private val _settingsBlocks = AtomicLong(0L)
        val settingsBlocksCount = MutableStateFlow(0L)

        private val _recentEvents = MutableStateFlow<List<AccessibilityLogEntry>>(emptyList())
        val recentEvents: StateFlow<List<AccessibilityLogEntry>> = _recentEvents.asStateFlow()

        fun openAccessibilitySettings(context: android.content.Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

data class AccessibilityLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val actionTitle: String,
    val detail: String
)
