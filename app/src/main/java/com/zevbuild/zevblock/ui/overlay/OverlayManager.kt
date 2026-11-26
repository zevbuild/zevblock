package com.zevbuild.zevblock.ui.overlay

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * OverlayManager handles rate-limited, debounced triggering of supportive security intervention overlays.
 */
object OverlayManager {

    private const val TAG = "OverlayManager"
    private const val DEBOUNCE_INTERVAL_MS = 1500L // Prevent overlay spamming

    private var lastTriggerTime: Long = 0L

    @Synchronized
    fun triggerWarning(
        context: Context,
        alertType: String,
        message: String,
        detail: String = "",
        category: String = "SYSTEM SHIELD",
        streakDays: Int = 0
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < DEBOUNCE_INTERVAL_MS) {
            Log.d(TAG, "Suppressed duplicate overlay trigger due to debounce window.")
            return
        }
        lastTriggerTime = now

        Log.w(TAG, "Triggering Security Warning Overlay: [$category / $alertType] $message")
        SecurityWarningOverlayActivity.show(
            context = context,
            alertType = alertType,
            message = message,
            detail = detail,
            category = category,
            streakDays = streakDays
        )
    }
}
