package com.example.core.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * SettingsTamperGuard:
 * Inspects com.android.settings windows to prevent unauthorized tampering with
 * ShieldGuard OS (App Info, Force Stop, Uninstall), Accessibility Service,
 * Device Administrator privilege, Always-On VPN, and Private DNS settings.
 */
class SettingsTamperGuard {

    companion object {
        const val PKG_SETTINGS = "com.android.settings"
        const val PKG_GOOGLE_SETTINGS = "com.google.android.settings"
        const val PKG_MIUI_SECURITY = "com.miui.securitycenter"
        const val PKG_SAMSUNG_SETTINGS = "com.samsung.android.settings"

        val SETTINGS_PACKAGES = setOf(
            PKG_SETTINGS,
            PKG_GOOGLE_SETTINGS,
            PKG_MIUI_SECURITY,
            PKG_SAMSUNG_SETTINGS
        )
    }

    var isEnabled: Boolean = true
    var targetAppName: String = "ZevBlock"
    var targetPackageName: String = "com.example"

    /**
     * Inspects active settings window node hierarchy.
     */
    fun inspect(packageName: String, className: String?, rootNode: AccessibilityNodeInfo?): SettingsTamperEvent? {
        if (!isEnabled || !SETTINGS_PACKAGES.contains(packageName) || rootNode == null) {
            return null
        }

        // 1. Check if inspecting ShieldGuard OS App Info page
        if (isTargetAppInfoPage(rootNode)) {
            return SettingsTamperEvent(
                target = "ShieldGuard OS App Management",
                reason = "Prevented force-stop, uninstall, or data wipe attempt."
            )
        }

        // 2. Check if inspecting Accessibility Service configuration
        if (isAccessibilitySettingsPage(rootNode)) {
            return SettingsTamperEvent(
                target = "Accessibility Service Settings",
                reason = "Prevented disabling ShieldGuard Anti-Tamper Service."
            )
        }

        // 3. Check if inspecting Device Administrator deactivation
        if (isDeviceAdminPage(rootNode)) {
            return SettingsTamperEvent(
                target = "Device Administrator Settings",
                reason = "Prevented deactivating Device Admin privilege."
            )
        }

        // 4. Check if inspecting VPN configuration (turning off Always-on VPN)
        if (isVpnSettingsPage(rootNode)) {
            return SettingsTamperEvent(
                target = "VPN & Always-On Settings",
                reason = "Prevented modifying Always-On VPN configuration."
            )
        }

        // 5. Check if modifying Private DNS settings
        if (isPrivateDnsSettingsPage(rootNode)) {
            return SettingsTamperEvent(
                target = "Private DNS Settings",
                reason = "Prevented changing Private DNS (DoT) configuration."
            )
        }

        return null
    }

    private fun isTargetAppInfoPage(rootNode: AccessibilityNodeInfo): Boolean {
        // Look for ShieldGuard OS label or package combined with app management buttons
        val hasAppName = nodeContainsText(rootNode, listOf(targetAppName, targetPackageName))
        if (!hasAppName) return false

        // Check if management buttons exist (Uninstall, Force stop, Clear storage)
        val hasManagementAction = nodeContainsText(
            rootNode,
            listOf("uninstall", "force stop", "clear storage", "clear data", "disable")
        )
        return hasManagementAction
    }

    private fun isAccessibilitySettingsPage(rootNode: AccessibilityNodeInfo): Boolean {
        val hasServiceMention = nodeContainsText(
            rootNode,
            listOf(
                "zevblock anti-tamper", "zevblock anti-tamper service", "zevblock",
                "shieldguard anti-tamper", "shieldguard anti-tamper service"
            )
        )
        if (!hasServiceMention) return false

        return nodeContainsText(rootNode, listOf("stop", "turn off", "deactivate", "use service"))
    }

    private fun isDeviceAdminPage(rootNode: AccessibilityNodeInfo): Boolean {
        val hasAdminMention = nodeContainsText(
            rootNode,
            listOf(
                "zevblock device protection", "zevblock",
                "shieldguard os device protection", "device admin apps", "device administrators"
            )
        )
        if (!hasAdminMention) return false

        return nodeContainsText(rootNode, listOf("deactivate", "remove active admin", "turn off"))
    }

    private fun isVpnSettingsPage(rootNode: AccessibilityNodeInfo): Boolean {
        val hasVpnMention = nodeContainsText(
            rootNode,
            listOf("vpn", "zevblock dns sinkhole", "shieldguard dns sinkhole")
        )
        if (!hasVpnMention) return false

        return nodeContainsText(rootNode, listOf("always-on vpn", "forget vpn", "disconnect", "block connections without vpn"))
    }

    private fun isPrivateDnsSettingsPage(rootNode: AccessibilityNodeInfo): Boolean {
        return nodeContainsText(rootNode, listOf("private dns", "private dns provider hostname"))
    }

    private fun nodeContainsText(node: AccessibilityNodeInfo?, keywords: List<String>, depth: Int = 0): Boolean {
        if (node == null || depth > 8) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (kw in keywords) {
            val kwLower = kw.lowercase()
            if (text.contains(kwLower) || desc.contains(kwLower)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = nodeContainsText(child, keywords, depth + 1)
            child.recycle()
            if (found) return true
        }

        return false
    }
}

data class SettingsTamperEvent(
    val target: String,
    val reason: String
)
