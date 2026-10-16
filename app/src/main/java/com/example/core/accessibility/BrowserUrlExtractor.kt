package com.example.core.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Extracts active URLs, address bar content, and detects Private/Incognito windows
 * across Chrome, Firefox, Brave, Edge, and Samsung Internet.
 */
class BrowserUrlExtractor {

    companion object {
        const val PKG_CHROME = "com.android.chrome"
        const val PKG_FIREFOX = "org.mozilla.firefox"
        const val PKG_FIREFOX_FENIX = "org.mozilla.fenix"
        const val PKG_FIREFOX_FOCUS = "org.mozilla.focus"
        const val PKG_BRAVE = "com.brave.browser"
        const val PKG_EDGE = "com.microsoft.emmx"
        const val PKG_SAMSUNG_BROWSER = "com.sec.android.app.sbrowser"
        const val PKG_OPERA = "com.opera.browser"
        const val PKG_DUCKDUCKGO = "com.duckduckgo.mobile.android"

        val SUPPORTED_BROWSERS = setOf(
            PKG_CHROME,
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.chromium.chrome",
            PKG_FIREFOX,
            PKG_FIREFOX_FENIX,
            PKG_FIREFOX_FOCUS,
            "org.mozilla.firefox_beta",
            PKG_BRAVE,
            PKG_EDGE,
            PKG_SAMSUNG_BROWSER,
            "com.sec.android.app.sbrowser.beta",
            PKG_OPERA,
            "com.opera.mini.native",
            "com.opera.touch",
            "com.opera.gx",
            PKG_DUCKDUCKGO,
            "com.kiwibrowser.browser",
            "mark.via.gp",
            "com.vivaldi.browser",
            "com.aloha.browser",
            "alohamobile.browser.lite",
            "com.cloudmosa.puffinFree",
            "org.torproject.torbrowser",
            "com.ucmobile.intl",
            "com.UCMobile",
            "com.mi.globalbrowser",
            "com.transsion.phoenix",
            "acr.browser.barebones",
            "com.pure.mini.browser"
        )

        // Known Address Bar View IDs
        private val URL_BAR_VIEW_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "com.brave.browser:id/url_bar",
            "com.microsoft.emmx:id/url_bar",
            "org.mozilla.firefox:id/toolbar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.fenix:id/toolbar_url",
            "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.opera.browser:id/url_field",
            "com.kiwibrowser.browser:id/url_bar",
            "mark.via.gp:id/search_bar",
            "mark.via.gp:id/at",
            "com.vivaldi.browser:id/url_bar",
            "com.aloha.browser:id/url_bar"
        )
    }

    /**
     * Checks if the package name belongs to a supported web browser.
     */
    fun isSupportedBrowser(packageName: String): Boolean {
        return SUPPORTED_BROWSERS.contains(packageName)
    }

    /**
     * Scans the active window tree of a browser to extract current URL/search text.
     */
    fun extractUrl(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null

        // 1. Fast ID lookup
        for (viewId in URL_BAR_VIEW_IDS) {
            val matchingNodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!matchingNodes.isNullOrEmpty()) {
                for (node in matchingNodes) {
                    val text = node.text?.toString() ?: node.contentDescription?.toString()
                    if (!text.isNullOrBlank()) {
                        return text.trim()
                    }
                }
            }
        }

        // 2. Fallback heuristic: Traverse node hierarchy looking for EditText in address bar position
        return findUrlByHeuristic(rootNode, depth = 0)
    }

    private fun findUrlByHeuristic(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 8) return null

        val className = node.className?.toString() ?: ""
        if (className.contains("EditText", ignoreCase = true)) {
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            if (!text.isNullOrBlank() && (text.contains(".") || text.contains("/") || text.length > 2)) {
                return text.trim()
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findUrlByHeuristic(child, depth + 1)
            child.recycle()
            if (found != null) return found
        }

        return null
    }

    /**
     * Detects if the current browser window is an Incognito or Private Browsing window.
     */
    fun isIncognitoOrPrivateWindow(packageName: String, rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        return when (packageName) {
            PKG_CHROME, PKG_BRAVE -> detectChromeBraveIncognito(rootNode)
            PKG_FIREFOX, PKG_FIREFOX_FENIX, PKG_FIREFOX_FOCUS -> detectFirefoxPrivate(rootNode)
            PKG_EDGE -> detectEdgeInPrivate(rootNode)
            PKG_SAMSUNG_BROWSER -> detectSamsungSecretMode(rootNode)
            else -> false
        }
    }

    private fun detectChromeBraveIncognito(rootNode: AccessibilityNodeInfo): Boolean {
        // Search for Incognito view ID or description
        val incognitoBadge = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/incognito_badge")
        if (!incognitoBadge.isNullOrEmpty()) return true

        val tabSwitcher = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/incognito_tabs")
        if (!tabSwitcher.isNullOrEmpty()) return true

        return checkNodeTextContains(
            rootNode,
            listOf("you've gone incognito", "incognito tab", "private tab", "private window", "open in incognito")
        )
    }

    private fun detectFirefoxPrivate(rootNode: AccessibilityNodeInfo): Boolean {
        if (checkNodeTextContains(rootNode, listOf("private browsing", "private tab", "trackers blocked"))) {
            return true
        }
        val privateIndicator = rootNode.findAccessibilityNodeInfosByViewId("org.mozilla.fenix:id/private_browsing_indicator")
        return !privateIndicator.isNullOrEmpty()
    }

    private fun detectEdgeInPrivate(rootNode: AccessibilityNodeInfo): Boolean {
        return checkNodeTextContains(rootNode, listOf("inprivate browsing", "inprivate tab", "inprivate"))
    }

    private fun detectSamsungSecretMode(rootNode: AccessibilityNodeInfo): Boolean {
        return checkNodeTextContains(rootNode, listOf("secret mode", "turn on secret mode", "turn off secret mode"))
    }

    private fun checkNodeTextContains(node: AccessibilityNodeInfo?, targets: List<String>, depth: Int = 0): Boolean {
        if (node == null || depth > 6) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (target in targets) {
            if (text.contains(target) || desc.contains(target)) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = checkNodeTextContains(child, targets, depth + 1)
            child.recycle()
            if (found) return true
        }

        return false
    }
}
