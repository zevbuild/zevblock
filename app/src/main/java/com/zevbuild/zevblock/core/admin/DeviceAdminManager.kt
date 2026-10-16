package com.zevbuild.zevblock.core.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.zevbuild.zevblock.receiver.ShieldDeviceAdminReceiver

/**
 * DeviceAdminManager interacts with Android's DevicePolicyManager to enforce
 * system-level anti-bypass restrictions when running as Device Owner or Device Administrator.
 */
class DeviceAdminManager(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    val adminComponent: ComponentName =
        ComponentName(context, ShieldDeviceAdminReceiver::class.java)

    /**
     * Checks if this application is provisioned as the Android Device Owner.
     * Full anti-bypass controls (such as blocking uninstall and safe boot) require Device Owner.
     */
    fun isDeviceOwner(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Device Owner status", e)
            false
        }
    }

    /**
     * Checks if this application is provisioned as a Profile Owner (e.g. Work Profile).
     */
    fun isProfileOwner(): Boolean {
        return try {
            dpm.isProfileOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Profile Owner status", e)
            false
        }
    }

    /**
     * Checks if standard Device Administrator privilege is active.
     */
    fun isAdminActive(): Boolean {
        return try {
            dpm.isAdminActive(adminComponent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Admin Active status", e)
            false
        }
    }

    /**
     * Prevents the user or malicious actors from uninstalling ShieldGuard OS.
     * Requires Device Owner or Profile Owner.
     */
    fun setUninstallBlocked(blocked: Boolean): Boolean {
        if (!isDeviceOwner() && !isProfileOwner()) {
            Log.w(TAG, "Cannot setUninstallBlocked: App is not Device Owner or Profile Owner.")
            return false
        }
        return try {
            dpm.setUninstallBlocked(adminComponent, context.packageName, blocked)
            Log.i(TAG, "Uninstall blocked state updated to: $blocked")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while setting uninstall blocked", e)
            false
        }
    }

    /**
     * Checks if uninstallation is currently blocked for this application.
     */
    fun isUninstallBlocked(): Boolean {
        return try {
            if (isDeviceOwner() || isProfileOwner()) {
                dpm.isUninstallBlocked(adminComponent, context.packageName)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query uninstall blocked state", e)
            false
        }
    }

    /**
     * Enforces Always-On VPN with lockdown mode. When lockdown is enabled,
     * network traffic is strictly blocked if the VPN is not connected.
     * Requires Device Owner or Profile Owner.
     */
    fun setAlwaysOnVpnPackage(vpnPackage: String? = context.packageName, lockdown: Boolean = true): Boolean {
        if (!isDeviceOwner() && !isProfileOwner()) {
            Log.w(TAG, "Cannot setAlwaysOnVpnPackage: Not Device Owner or Profile Owner.")
            return false
        }
        return try {
            dpm.setAlwaysOnVpnPackage(adminComponent, vpnPackage, lockdown)
            Log.i(TAG, "Always-on VPN configured for $vpnPackage with lockdown=$lockdown")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Always-On VPN package", e)
            false
        }
    }

    /**
     * Clears Always-On VPN requirement.
     */
    fun clearAlwaysOnVpnPackage(): Boolean {
        if (!isDeviceOwner() && !isProfileOwner()) return false
        return try {
            dpm.setAlwaysOnVpnPackage(adminComponent, null, false)
            Log.i(TAG, "Always-on VPN cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear Always-On VPN", e)
            false
        }
    }

    /**
     * Queries the currently configured Always-On VPN package name.
     */
    fun getAlwaysOnVpnPackage(): String? {
        return try {
            if (isDeviceOwner() || isProfileOwner()) {
                dpm.getAlwaysOnVpnPackage(adminComponent)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Prevents rebooting into Safe Mode (which disables 3rd party apps and VPNs).
     * Requires Device Owner.
     */
    fun setSafeModeBlocked(blocked: Boolean): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot configure Safe Mode restriction: Requires Device Owner.")
            return false
        }
        return try {
            if (blocked) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            }
            Log.i(TAG, "Safe boot blocked status set to: $blocked")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while configuring DISALLOW_SAFE_BOOT", e)
            false
        }
    }

    /**
     * Checks if Safe Mode boot is currently blocked.
     */
    fun isSafeModeBlocked(): Boolean {
        return try {
            userManager.hasUserRestriction(UserManager.DISALLOW_SAFE_BOOT)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Blocks tampering with Android 9+ Private DNS (DoT) settings.
     * If the user could change Private DNS, they could bypass local sinkhole DNS filtering.
     * Requires Device Owner.
     */
    fun setPrivateDnsTamperBlocked(blocked: Boolean): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot configure Private DNS restriction: Requires Device Owner.")
            return false
        }
        return try {
            if (blocked) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }
            Log.i(TAG, "Private DNS configuration blocked status set to: $blocked")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while configuring DISALLOW_CONFIG_PRIVATE_DNS", e)
            false
        }
    }

    /**
     * Checks if Private DNS modification is currently disallowed.
     */
    fun isPrivateDnsTamperBlocked(): Boolean {
        return try {
            userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Applies full anti-bypass lockdown:
     * 1. Blocks uninstallation
     * 2. Enforces Always-On VPN with lockdown
     * 3. Blocks Safe Mode boot
     * 4. Blocks Private DNS modification
     * 5. Blocks VPN settings modification (DISALLOW_CONFIG_VPN)
     * 6. Blocks factory reset and multi-user switching (DISALLOW_ADD_USER, DISALLOW_FACTORY_RESET)
     */
    fun applyAntiBypassLockdown(): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot apply full lockdown: Device Owner is not granted.")
            return false
        }

        return try {
            // 1. Anti-uninstall
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)

            // 2. Always-On VPN lockdown
            dpm.setAlwaysOnVpnPackage(adminComponent, context.packageName, true)

            // 3. System restrictions
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)

            Log.i(TAG, "Full anti-bypass lockdown successfully applied!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying anti-bypass lockdown", e)
            false
        }
    }

    /**
     * Safely releases anti-bypass lockdown after authentication / cooldown completion.
     */
    fun releaseAntiBypassLockdown(): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            dpm.setUninstallBlocked(adminComponent, context.packageName, false)
            dpm.setAlwaysOnVpnPackage(adminComponent, null, false)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            Log.i(TAG, "Anti-bypass lockdown released")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing anti-bypass lockdown", e)
            false
        }
    }

    /**
     * Fetches current snapshot of all Device Owner & Admin statuses.
     */
    fun getStatus(): DeviceOwnerStatus {
        val isDo = isDeviceOwner()
        val isPo = isProfileOwner()
        val isAdmin = isAdminActive()
        return DeviceOwnerStatus(
            isDeviceOwner = isDo,
            isProfileOwner = isPo,
            isAdminActive = isAdmin,
            isUninstallBlocked = isUninstallBlocked(),
            isSafeModeBlocked = isSafeModeBlocked(),
            isPrivateDnsBlocked = isPrivateDnsTamperBlocked(),
            alwaysOnVpnPackage = getAlwaysOnVpnPackage(),
            adbCommand = getAdbCommand(),
            shizukuCommand = getShizukuCommand()
        )
    }

    /**
     * ADB Shell command to activate Device Owner.
     * Note: Android requires that all other accounts (Google, etc.) are temporarily
     * removed or device is provisioned from factory setup.
     */
    fun getAdbCommand(): String {
        return "adb shell dpm set-device-owner ${context.packageName}/${ShieldDeviceAdminReceiver::class.java.name}"
    }

    /**
     * Shizuku / root shell command string to provision Device Owner.
     */
    fun getShizukuCommand(): String {
        return "dpm set-device-owner ${context.packageName}/${ShieldDeviceAdminReceiver::class.java.name}"
    }

    companion object {
        private const val TAG = "DeviceAdminManager"
    }
}

/**
 * Snapshot model of device policy enforcement state.
 */
data class DeviceOwnerStatus(
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val isAdminActive: Boolean,
    val isUninstallBlocked: Boolean,
    val isSafeModeBlocked: Boolean,
    val isPrivateDnsBlocked: Boolean,
    val alwaysOnVpnPackage: String?,
    val adbCommand: String,
    val shizukuCommand: String
)
