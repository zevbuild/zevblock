package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * ShieldAccessibilityService: Anti-tamper window inspection and enforcement service.
 * Foundation stub for Deliverable 1; full window inspection and settings lock
 * implemented in future deliverable.
 */
class ShieldAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "ShieldAccessibilityService connected and active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        // Inspect window transitions for Android settings / force-stop attempts
        Log.d(TAG, "Accessibility event from: $packageName, type: ${event.eventType}")
    }

    override fun onInterrupt() {
        Log.w(TAG, "ShieldAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ShieldAccessibilityService destroyed")
    }

    companion object {
        const val TAG = "ShieldAccessibility"
    }
}
