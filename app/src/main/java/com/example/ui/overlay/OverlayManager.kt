package com.example.ui.overlay

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * OverlayManager handles rate-limited, debounced triggering of security warning overlays.
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
        detail: String = ""
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < DEBOUNCE_INTERVAL_MS) {
            Log.d(TAG, "Suppressed duplicate overlay trigger due to debounce window.")
            return
        }
        lastTriggerTime = now

        Log.w(TAG, "Triggering Security Warning Overlay: [$alertType] $message ($detail)")
        SecurityWarningOverlayActivity.show(context, alertType, message, detail)
    }
}
