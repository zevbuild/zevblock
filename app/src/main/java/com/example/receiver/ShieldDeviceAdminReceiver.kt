package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * ShieldDeviceAdminReceiver handles Device Administrator and Device Owner lifecycle events.
 * It enforces anti-uninstall blocks, safe mode restrictions, and always-on VPN lockdown.
 */
class ShieldDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "ShieldGuard Device Administrator successfully enabled.")
        Toast.makeText(context, "ShieldGuard Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "ShieldGuard Device Administrator disable requested by user.")
        return "Warning: Disabling ShieldGuard Device Admin will remove system-level anti-bypass restrictions."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "ShieldGuard Device Administrator disabled.")
        Toast.makeText(context, "ShieldGuard Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.i(TAG, "Entered Lock Task mode with package: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.i(TAG, "Exited Lock Task mode")
    }

    companion object {
        private const val TAG = "ShieldDeviceAdmin"
    }
}
