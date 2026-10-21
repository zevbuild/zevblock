package com.zevbuild.zevblock.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.zevbuild.zevblock.core.accessibility.BrowserUrlExtractor
import com.zevbuild.zevblock.core.accessibility.NsfwContentDetector
import com.zevbuild.zevblock.core.accessibility.SettingsTamperGuard
import com.zevbuild.zevblock.core.diagnostics.CrashGuardManager
import com.zevbuild.zevblock.core.security.TamperLockManager
import com.zevbuild.zevblock.ui.overlay.OverlayManager
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
 * - Displays non-intrusive heads-up warning alerts for vulgar keywords in chat apps without interrupting conversation.
 * - Triggers GLOBAL_ACTION_BACK with a Jetpack Compose security warning overlay for browsers and settings.
 */
class ShieldAccessibilityService : AccessibilityService() {

    private val browserUrlExtractor = BrowserUrlExtractor()
    private val nsfwContentDetector: NsfwContentDetector
        get() = NsfwContentDetector.shared
    private val settingsTamperGuard = SettingsTamperGuard()
    private var lastChatWarningTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastKeywords: Set<String>? = null
    private var pendingPackageName: String? = null
    private val scanRunnable = Runnable {
        val pkg = pendingPackageName ?: return@Runnable
        scanWindow(pkg)
    }

    private fun ensureDependencies() {
        val tamperLockManager = TamperLockManager(applicationContext)
        if (statsManager == null) {
            statsManager = com.zevbuild.zevblock.core.stats.ProtectionStatsManager(
                applicationContext,
                tamperLockManager
            )
        }
        if (customRuleManager == null) {
            customRuleManager = com.zevbuild.zevblock.core.rules.CustomShieldRuleManager(
                applicationContext,
                tamperLockManager,
                com.zevbuild.zevblock.vpn.DnsBlockerVpnService.blocklistMatcher,
                nsfwContentDetector
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureDependencies()
        _isServiceActive.value = true
        Log.i(TAG, "ShieldAccessibilityService successfully connected and active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Skip exempt packages at the top of onAccessibilityEvent
        if (isExemptPackage(packageName)) return

        ensureDependencies()

        // Call nsfwContentDetector.setCustomKeywords() only when the keyword set actually changed
        customRuleManager?.let {
            val currentKeywords = it.customKeywords.value
            if (currentKeywords != lastKeywords) {
                lastKeywords = currentKeywords.toSet()
                nsfwContentDetector.setCustomKeywords(currentKeywords)
            }
        }

        // Keep the messaging-app typing check immediate (it is cheap)
        if (isNsfwFilterEnabled && isMessagingApp(packageName)) {
            try {
                val isBypassActive = TamperLockManager.isBypassActive(this)
                val eventTexts = event.text
                if (!eventTexts.isNullOrEmpty()) {
                    for (charSeq in eventTexts) {
                        val typed = charSeq?.toString()
                        if (!typed.isNullOrBlank()) {
                            val nsfwResult = nsfwContentDetector.analyze(typed)
                            val shouldWarn = if (isBypassActive) nsfwResult.category != "Custom Blocked Keyword" else true
                            if (nsfwResult.isNsfw && shouldWarn) {
                                Log.w(TAG, "Vulgar keyword in $packageName typing event: '${nsfwResult.matchedTerm}'")
                                showChatWarning(nsfwResult.matchedTerm ?: "explicit keyword", packageName)
                                val chatCount = _chatWarnings.incrementAndGet()
                                chatWarningsCount.value = chatCount
                                val nsfwCount = _nsfwBlocks.incrementAndGet()
                                nsfwBlocksCount.value = nsfwCount
                                logEvent(packageName, "Vulgar Word Warning (Chat)", "Matched: ${nsfwResult.matchedTerm}")
                                statsManager?.recordChatWarning()
                                return
                            }
                        }
                    }
                }

                val sourceNode = event.source
                if (sourceNode != null) {
                    try {
                        if (!sourceNode.isPassword) {
                            val sourceText = sourceNode.text?.toString()
                            if (!sourceText.isNullOrBlank()) {
                                val nsfwResult = nsfwContentDetector.analyze(sourceText)
                                val shouldWarn = if (isBypassActive) nsfwResult.category != "Custom Blocked Keyword" else true
                                if (nsfwResult.isNsfw && shouldWarn) {
                                    Log.w(TAG, "Vulgar keyword in $packageName source node: '${nsfwResult.matchedTerm}'")
                                    showChatWarning(nsfwResult.matchedTerm ?: "explicit keyword", packageName)
                                    val chatCount = _chatWarnings.incrementAndGet()
                                    chatWarningsCount.value = chatCount
                                    val nsfwCount = _nsfwBlocks.incrementAndGet()
                                    nsfwBlocksCount.value = nsfwCount
                                    logEvent(packageName, "Vulgar Word Warning (Chat)", "Matched: ${nsfwResult.matchedTerm}")
                                    statsManager?.recordChatWarning()
                                    return
                                }
                            }
                        }
                    } finally {
                        safeRecycle(sourceNode)
                    }
                }
            } catch (t: Throwable) {
                CrashGuardManager.recordHandledException("ShieldAccessibilityService.onEvent", t, autoRecovered = true)
            }
        }

        // Debounce the heavy scan: store latest package name, removeCallbacks, then postDelayed(600ms) on mainHandler
        pendingPackageName = packageName
        mainHandler.removeCallbacks(scanRunnable)
        mainHandler.postDelayed(scanRunnable, 600L)
    }

    private fun scanWindow(packageName: String) {
        val isBypassActive = TamperLockManager.isBypassActive(this)

        try {
            val rootNode: AccessibilityNodeInfo? = rootInActiveWindow

            // =================================================================
            // 1. BROWSER INSPECTION: Incognito & NSFW URL Extraction
            // =================================================================
            if (browserUrlExtractor.isSupportedBrowser(packageName)) {
                // A. Check for Incognito / Private Window (IMMUTABLE - never bypassed)
                if (isIncognitoBlockEnabled && browserUrlExtractor.isIncognitoOrPrivateWindow(packageName, rootNode)) {
                    Log.w(TAG, "Incognito mode detected in $packageName! Executing back action.")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val incognitoCount = _incognitoBlocks.incrementAndGet()
                    incognitoBlocksCount.value = incognitoCount
                    logEvent(packageName, "Incognito Window Blocked", "Private browsing is disabled")
                    statsManager?.recordIncognitoBlock()
                    OverlayManager.triggerWarning(
                        this,
                        alertType = "Incognito Mode Blocked",
                        message = "Private and Incognito browsing is strictly prohibited under ZevBlock system safeguards.",
                        detail = "Package: $packageName"
                    )
                    safeRecycle(rootNode)
                    return
                }

                // B. Extract URL / Search text & Check for NSFW content
                val extractedUrl = browserUrlExtractor.extractUrl(rootNode)
                if (!extractedUrl.isNullOrBlank() && isNsfwFilterEnabled) {
                    val nsfwResult = nsfwContentDetector.analyze(extractedUrl)
                    val shouldBlockNsfw = if (isBypassActive) {
                        nsfwResult.isNsfw && nsfwResult.category != "Custom Blocked Keyword"
                    } else {
                        nsfwResult.isNsfw
                    }
                    if (shouldBlockNsfw) {
                        Log.w(TAG, "NSFW content detected: '${nsfwResult.matchedTerm}' (${nsfwResult.category}) in URL: $extractedUrl")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        val nsfwCount = _nsfwBlocks.incrementAndGet()
                        nsfwBlocksCount.value = nsfwCount
                        logEvent(packageName, "NSFW Content Blocked", "Matched: ${nsfwResult.matchedTerm}")
                        statsManager?.recordScreenBlock()
                        OverlayManager.triggerWarning(
                            this,
                            alertType = "Explicit Content Blocked",
                            message = "Adult content, explicit keyword, or search query detected: '${nsfwResult.matchedTerm}'.",
                            detail = extractedUrl
                        )
                        safeRecycle(rootNode)
                        return
                    }
                }
            }

            // =================================================================
            // 2. SETTINGS GUARD: Prevent App Info, VPN, Admin, or Accessibility tampering
            // =================================================================
            if (isSettingsGuardEnabled && !isBypassActive && SettingsTamperGuard.SETTINGS_PACKAGES.contains(packageName)) {
                val tamperEvent = settingsTamperGuard.inspect(packageName, null, rootNode)
                if (tamperEvent != null) {
                    Log.w(TAG, "Settings tamper detected: ${tamperEvent.target} - ${tamperEvent.reason}")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val settingsCount = _settingsBlocks.incrementAndGet()
                    settingsBlocksCount.value = settingsCount
                    logEvent(packageName, "Settings Tamper Blocked", tamperEvent.target)
                    statsManager?.recordSettingsTamper()
                    OverlayManager.triggerWarning(
                        this,
                        alertType = "Settings Access Restricted",
                        message = tamperEvent.reason,
                        detail = tamperEvent.target
                    )
                    safeRecycle(rootNode)
                    return
                }
            }

            // =================================================================
            // 3. SYSTEM-WIDE ADULT CONTENT INSPECTION across any app
            // Zero-Bypass Guarantee: Built-in explicit content is ALWAYS blocked/warned,
            // even during partner temporary bypass sessions.
            // =================================================================
            if (isNsfwFilterEnabled && !isExemptPackage(packageName)) {
                // Check if this is a messaging / chat app (WhatsApp, Telegram, Signal, etc.)
                // In chat apps, monitor for vulgar keywords and display a warning toast/notification
                // WITHOUT kicking the user out of the app.
                if (isMessagingApp(packageName)) {
                    val windowNsfw = inspectWindowForNsfw(rootNode, depth = 0)
                    val shouldWarn = if (windowNsfw != null) {
                        if (isBypassActive) windowNsfw.category != "Custom Blocked Keyword" else true
                    } else false

                    if (shouldWarn && windowNsfw != null) {
                        Log.w(TAG, "Vulgar keyword in messaging app $packageName: '${windowNsfw.matchedTerm}' (warning alert only)")
                        showChatWarning(windowNsfw.matchedTerm ?: "explicit keyword", packageName)
                        val chatCount = _chatWarnings.incrementAndGet()
                        chatWarningsCount.value = chatCount
                        val nsfwCount = _nsfwBlocks.incrementAndGet()
                        nsfwBlocksCount.value = nsfwCount
                        logEvent(packageName, "Vulgar Word Warning (Chat)", "Matched: ${windowNsfw.matchedTerm}")
                        statsManager?.recordChatWarning()
                        safeRecycle(rootNode)
                        return
                    }
                } else {
                    // Check if the window is an unlisted browser with an address bar
                    val extractedUrl = browserUrlExtractor.extractUrl(rootNode)
                    if (!extractedUrl.isNullOrBlank()) {
                        val nsfwResult = nsfwContentDetector.analyze(extractedUrl)
                        val shouldBlockNsfw = if (isBypassActive) {
                            nsfwResult.isNsfw && nsfwResult.category != "Custom Blocked Keyword"
                        } else {
                            nsfwResult.isNsfw
                        }
                        if (shouldBlockNsfw) {
                            Log.w(TAG, "NSFW URL in unlisted browser/app $packageName: '${nsfwResult.matchedTerm}'")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            val nsfwCount = _nsfwBlocks.incrementAndGet()
                            nsfwBlocksCount.value = nsfwCount
                            logEvent(packageName, "Adult Content Blocked", "Matched: ${nsfwResult.matchedTerm}")
                            statsManager?.recordScreenBlock()
                            OverlayManager.triggerWarning(
                                this,
                                alertType = "Explicit Content Blocked",
                                message = "System-level adult content filter intercepted explicit URL: '${nsfwResult.matchedTerm}'.",
                                detail = extractedUrl
                            )
                            safeRecycle(rootNode)
                            return
                        }
                    } else {
                        // Inspect on-screen window text for explicit adult titles or search results
                        val windowNsfw = inspectWindowForNsfw(rootNode, depth = 0)
                        val shouldBlockWindow = if (windowNsfw != null) {
                            if (isBypassActive) windowNsfw.category != "Custom Blocked Keyword" else true
                        } else false
                        if (shouldBlockWindow && windowNsfw != null) {
                            Log.w(TAG, "On-screen adult content detected in $packageName: '${windowNsfw.matchedTerm}'")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            val nsfwCount = _nsfwBlocks.incrementAndGet()
                            nsfwBlocksCount.value = nsfwCount
                            logEvent(packageName, "On-Screen Adult Content Blocked", "Matched: ${windowNsfw.matchedTerm}")
                            statsManager?.recordScreenBlock()
                            OverlayManager.triggerWarning(
                                this,
                                alertType = "Explicit Content Blocked",
                                message = "System safeguard intercepted explicit adult material on screen: '${windowNsfw.matchedTerm}'.",
                                detail = "App: $packageName"
                            )
                            safeRecycle(rootNode)
                            return
                        }
                    }
                }
            }

            safeRecycle(rootNode)
        } catch (t: Throwable) {
            Log.e(TAG, "Error in accessibility event processing", t)
            CrashGuardManager.recordHandledException("ShieldAccessibilityService.scanWindow", t, autoRecovered = true)
        }
    }

    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        if (node == null) return
        try {
            @Suppress("DEPRECATION")
            node.recycle()
        } catch (_: Throwable) {
        }
    }

    private fun isExemptPackage(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName == "com.android.systemui") return true
        if (packageName.contains("launcher") || packageName.contains("inputmethod")) return true
        return false
    }

    private fun inspectWindowForNsfw(node: AccessibilityNodeInfo?, depth: Int): com.zevbuild.zevblock.core.accessibility.NsfwDetectionResult? {
        if (node == null || depth > 12) return null
        if (node.isPassword) return null

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

        val count = node.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            val found = inspectWindowForNsfw(child, depth + 1)
            safeRecycle(child)
            if (found != null) return found
        }

        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "ShieldAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(scanRunnable)
        _isServiceActive.value = false
        Log.i(TAG, "ShieldAccessibilityService destroyed")
    }

    private fun showChatWarning(matchedTerm: String, packageName: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastChatWarningTime < CHAT_WARNING_THROTTLE_MS) {
            return
        }
        lastChatWarningTime = now

        // 1. Show immediate Toast alert on UI Thread
        mainHandler.post {
            try {
                Toast.makeText(
                    applicationContext,
                    "⚠️ ZevBlock: Vulgar keyword '$matchedTerm' detected. Keep chats clean!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display chat warning toast", e)
            }
        }

        // 2. Post heads-up warning notification
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHAT_WARNING_CHANNEL_ID,
                        "ZevBlock Chat & Messaging Safeguard",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Displays non-intrusive warnings when vulgar or explicit keywords are detected in chat apps"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val notification = NotificationCompat.Builder(this, CHAT_WARNING_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("⚠️ Vulgar Word Detected")
                    .setContentText("Keyword '$matchedTerm' detected in chat. Please avoid vulgarity.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setTimeoutAfter(8000L)
                    .build()

                notificationManager.notify(CHAT_WARNING_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post chat warning notification", e)
        }
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

        const val CHAT_WARNING_CHANNEL_ID = "zevblock_chat_warning_channel"
        private const val CHAT_WARNING_NOTIFICATION_ID = 3001
        private const val CHAT_WARNING_THROTTLE_MS = 4000L

        val CHAT_AND_MESSAGING_PACKAGES: Set<String> = setOf(
            // WhatsApp
            "com.whatsapp",
            "com.whatsapp.w4b",
            // Telegram
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            // Signal
            "org.thoughtcrime.securesms",
            // SMS & RCS
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            // Messenger & Instagram
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.instagram.android",
            // Discord & Slack
            "com.discord",
            "com.slack",
            // Snapchat
            "com.snapchat.android",
            // Viber, Line, WeChat, KakaoTalk
            "com.viber.voip",
            "jp.naver.line.android",
            "com.tencent.mm",
            "com.kakao.talk",
            // Google Meet / Chat / Hangouts
            "com.google.android.talk",
            // Skype
            "com.skype.raider"
        )

        fun isMessagingApp(packageName: String): Boolean {
            if (CHAT_AND_MESSAGING_PACKAGES.contains(packageName)) return true
            val lower = packageName.lowercase()
            return lower.startsWith("com.whatsapp") ||
                    lower.startsWith("org.telegram") ||
                    lower.startsWith("com.discord") ||
                    lower.contains(".messaging") ||
                    lower.contains(".im")
        }

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

        private val _chatWarnings = AtomicLong(0L)
        val chatWarningsCount = MutableStateFlow(0L)

        private val _settingsBlocks = AtomicLong(0L)
        val settingsBlocksCount = MutableStateFlow(0L)

        private val _recentEvents = MutableStateFlow<List<AccessibilityLogEntry>>(emptyList())
        val recentEvents: StateFlow<List<AccessibilityLogEntry>> = _recentEvents.asStateFlow()

        var statsManager: com.zevbuild.zevblock.core.stats.ProtectionStatsManager? = null
        var customRuleManager: com.zevbuild.zevblock.core.rules.CustomShieldRuleManager? = null

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
