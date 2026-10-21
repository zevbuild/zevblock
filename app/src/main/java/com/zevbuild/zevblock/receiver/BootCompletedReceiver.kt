package com.zevbuild.zevblock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.zevbuild.zevblock.core.diagnostics.CrashGuardManager
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService

/**
 * BootCompletedReceiver listens for system boot and package upgrade broadcasts
 * to re-verify device restrictions and resume protection when VPN permission is already granted.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "ZevBlock BootCompletedReceiver received action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                try {
                    // Only auto-start VPN service if the user has already granted VPN consent.
                    // Starting a VpnService when VpnService.prepare() != null causes establish() to return null
                    // and triggers ForegroundServiceStartNotAllowedException / crash loops on Android 12-15.
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent == null) {
                        Log.i(TAG, "VPN permission verified post-boot/update. Launching DNS Sinkhole...")
                        DnsBlockerVpnService.start(context)
                    } else {
                        Log.i(TAG, "Skipping auto-start on $action: VPN consent not yet granted by user.")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to auto-start DnsBlockerVpnService on $action", t)
                    CrashGuardManager.recordHandledException("BootCompletedReceiver", t, autoRecovered = true)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
