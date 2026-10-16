package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import com.example.vpn.DnsBlockerVpnService

/**
 * BootCompletedReceiver listens for system boot and package upgrade broadcasts
 * to re-verify device restrictions and resume protection.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "ZevBlock BootCompletedReceiver received action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Re-verifying anti-bypass and system policies post-boot. Launching DNS Sinkhole...")
                try {
                    DnsBlockerVpnService.start(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start DnsBlockerVpnService on boot", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
