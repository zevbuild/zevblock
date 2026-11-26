package com.zevbuild.zevblock.ui.screens

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.zevbuild.zevblock.core.admin.DeviceAdminManager
import com.zevbuild.zevblock.core.admin.DeviceOwnerStatus
import com.zevbuild.zevblock.ui.components.ZevStatusBadge
import com.zevbuild.zevblock.ui.theme.ZevTheme

/**
 * Full-height Guided Setup Modal Sheet opened on first setup or from Settings / Recovery Banner.
 */
@Composable
fun GuidedSetupSheet(
    deviceOwnerStatus: DeviceOwnerStatus,
    isVpnRunning: Boolean,
    isAccessibilityActive: Boolean,
    isBatteryExempt: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onLaunchVpnPrepare: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onNavigateToPartnerLock: () -> Unit,
    onViewPurposeMission: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZevTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.background)
                .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Guided Protection Setup",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Syncs automatically when you return from Android Settings",
                            fontSize = 12.sp,
                            color = colors.textMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Setup", tint = colors.textPrimary)
                    }
                }

                OnboardingScreen(
                    deviceOwnerStatus = deviceOwnerStatus,
                    isVpnRunning = isVpnRunning,
                    isAccessibilityActive = isAccessibilityActive,
                    isBatteryExempt = isBatteryExempt,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onLaunchVpnPrepare = onLaunchVpnPrepare,
                    onActivateDeviceAdmin = onActivateDeviceAdmin,
                    onNavigateToPartnerLock = {
                        onDismiss()
                        onNavigateToPartnerLock()
                    },
                    onViewPurposeMission = onViewPurposeMission,
                    onFinishSetup = onFinish
                )
            }
        }
    }
}

/**
 * 3-Step Onboarding & Permissions Wizard:
 * 1. Purpose & Privacy Limits
 * 2. Core Activation (Accessibility + Android 13 Guide, VPN + Notifications, Battery Keep-Alive)
 * 3. Optional Hardening (Standard Device Admin, ADB/Shizuku Device Owner, Partner TOTP)
 */
@Composable
fun OnboardingScreen(
    deviceOwnerStatus: DeviceOwnerStatus,
    isVpnRunning: Boolean,
    isAccessibilityActive: Boolean,
    isBatteryExempt: Boolean? = null,
    hasNotificationPermission: Boolean? = null,
    onRequestNotificationPermission: () -> Unit = {},
    onLaunchVpnPrepare: () -> Unit,
    onActivateDeviceAdmin: () -> Unit = {},
    onNavigateToPartnerLock: () -> Unit,
    onViewPurposeMission: () -> Unit = {},
    onFinishSetup: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedBatteryExempt = isBatteryExempt ?: checkBatteryOptimizationExempt(context)
    val resolvedNotificationGranted = hasNotificationPermission ?: checkNotificationPermissionGranted(context)
    val colors = ZevTheme.colors
    val scrollState = rememberScrollState()
    var step by remember { mutableIntStateOf(if (isVpnRunning && isAccessibilityActive) 2 else 1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Stepper Bar (3 clear steps)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1. Mission", "2. Core Shield", "3. Hardening").forEachIndexed { idx, label ->
                val selected = step == idx
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp)),
                    onClick = { step = idx },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) colors.teal else colors.surface,
                    border = BorderStroke(1.dp, if (selected) colors.teal else colors.border)
                ) {
                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) colors.tealBtnFg else colors.textSecondary
                        )
                    }
                }
            }
        }

        when (step) {
            0 -> {
                // Step 1: Purpose & Honest Limits
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = colors.teal, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mission & Honest Architecture", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                        Text(
                            text = "ZevBlock pairs a local port-53 DNS sinkhole with on-device Accessibility screening to keep your device free from adult content.",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 19.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.background)
                                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("What Stays Local vs Network:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Text("• Screen text & browser URL inspection runs in RAM only—never saved to disk.", fontSize = 12.sp, color = colors.textSecondary)
                                Text("• Allowed DNS lookups use family-filtered upstream resolvers (Cloudflare 1.1.1.3 / CleanBrowsing).", fontSize = 12.sp, color = colors.textSecondary)
                                Text("• Embedded browser DoH or direct IPs are caught by the Accessibility Screen Guard.", fontSize = 12.sp, color = colors.textSecondary)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onViewPurposeMission,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Full Disclaimer", fontSize = 12.sp, color = colors.teal, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { step = 1 },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Next: Activate", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.greenBtnFg)
                            }
                        }
                    }
                }
            }

            1 -> {
                // Step 2: Core Shield Activation (VPN + Accessibility + Notifications + Battery Keep-Alive)
                if (!isAccessibilityActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RestrictedSettingsBypassCard(context = context, isAccessibilityActive = isAccessibilityActive)
                }

                AccessibilityPermissionCard(context = context, isActive = isAccessibilityActive)

                VpnPermissionCard(
                    isRunning = isVpnRunning,
                    hasNotificationPermission = resolvedNotificationGranted,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onLaunchVpn = onLaunchVpnPrepare
                )

                BatteryOptimizationCard(context = context, isExempt = resolvedBatteryExempt)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { step = 2 },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Optional Hardening", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.tealBtnFg)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.tealBtnFg)
                    }
                    if (isVpnRunning && isAccessibilityActive && onFinishSetup != null) {
                        Button(
                            onClick = onFinishSetup,
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Finish Setup", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.greenBtnFg)
                        }
                    }
                }
            }

            else -> {
                // Step 3: Optional Hardening & Honest Capabilities Matrix
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("partner_onboarding_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = colors.teal, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Accountability Partner Lock", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            }
                            ZevStatusBadge(label = "OPTIONAL", color = colors.teal)
                        }
                        Text(
                            text = "Pair a trusted friend's authenticator app so disabling shields or changing custom rules requires their 6-digit code.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Button(
                            onClick = onNavigateToPartnerLock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("go_to_partner_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Configure Partner TOTP & QR", fontWeight = FontWeight.Bold, color = colors.tealBtnFg)
                        }
                    }
                }

                // Standard Device Admin Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Standard Device Administrator", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                            StatusChip(active = deviceOwnerStatus.isAdminActive)
                        }
                        Text(
                            text = "Adds uninstall friction without needing ADB/PC setup. Note: Safe Mode, Private DNS locks, and Always-On VPN lockdown still require Device Owner.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        OutlinedButton(
                            onClick = onActivateDeviceAdmin,
                            enabled = !deviceOwnerStatus.isAdminActive,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (deviceOwnerStatus.isAdminActive) "Device Admin Already Active" else "Activate Device Admin",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (deviceOwnerStatus.isAdminActive) colors.green else colors.teal
                            )
                        }
                    }
                }

                DeviceOwnerSetupCard(
                    status = deviceOwnerStatus,
                    onCopyCommand = { cmd, label -> copyToClipboard(context, cmd, label) }
                )

                if (onFinishSetup != null) {
                    Button(
                        onClick = onFinishSetup,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, color = colors.greenBtnFg)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun RestrictedSettingsBypassCard(context: Context, isAccessibilityActive: Boolean) {
    val colors = ZevTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("restricted_settings_guide_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (isAccessibilityActive) colors.border else colors.amber)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.amberSoftText, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Android 13+ Restricted Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                }
                ZevStatusBadge(label = "OS STEP", color = colors.amber)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sideloaded APKs show 'Restricted setting' on Android 13–15 until allowed in App Info:",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.background)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BypassStepRow("1", "Open ZevBlock App Info", "Tap 1. Open App Info below.")
                    BypassStepRow("2", "Tap Top-Right Menu (⋮)", "Select 'Allow restricted settings' & confirm PIN.")
                    BypassStepRow("3", "Enable Accessibility Service", "Tap 2. Open Accessibility & turn on ZevBlock.")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openAppInfoSettings(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("open_app_info_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.amber),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.amberBtnFg)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1. App Info", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.amberBtnFg)
                }

                OutlinedButton(
                    onClick = { openAccessibilitySettings(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("open_accessibility_from_guide_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text("2. Accessibility", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
fun BypassStepRow(stepNumber: String, title: String, description: String) {
    val colors = ZevTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.teal.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = stepNumber, color = colors.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text(text = description, fontSize = 12.sp, color = colors.textSecondary)
        }
    }
}

@Composable
fun AccessibilityPermissionCard(context: Context, isActive: Boolean) {
    val colors = ZevTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("accessibility_permission_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = if (isActive) colors.green else colors.amber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Screen & Anti-Tamper Guard", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                }
                StatusChip(active = isActive)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Inspects browser URLs, blocks Incognito tabs, alerts on vulgar chat text, and guards Android Settings.",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { openAccessibilitySettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("activate_accessibility_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) colors.card else colors.greenBtn
                ),
                border = if (isActive) BorderStroke(1.dp, colors.border) else null,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isActive) "Accessibility Active (Configure)" else "Enable Accessibility Guard",
                    color = if (isActive) colors.textPrimary else colors.greenBtnFg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun VpnPermissionCard(
    isRunning: Boolean,
    hasNotificationPermission: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onLaunchVpn: () -> Unit
) {
    val colors = ZevTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_permission_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = if (isRunning) colors.green else colors.teal, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Local DNS Sinkhole VPN", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                }
                StatusChip(active = isRunning)
            }

            Text(
                text = "Intercepts port 53 lookups locally, enforces SafeSearch VIPs across search engines, and blocks adult domains.",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.amber)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.amberSoftText)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Grant Notification Permission (Android 13+)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.amberSoftText)
                }
            }

            Button(
                onClick = onLaunchVpn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("activate_vpn_button"),
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isRunning) "DNS Sinkhole Running" else "Grant VPN & Start Shield",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = colors.tealBtnFg
                )
            }
        }
    }
}

@Composable
fun BatteryOptimizationCard(context: Context, isExempt: Boolean) {
    val colors = ZevTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_optimization_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (isExempt) colors.border else colors.amber.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = if (isExempt) colors.green else colors.amber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Background Keep-Alive", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                }
                ZevStatusBadge(
                    label = if (isExempt) "UNRESTRICTED" else "OPTIMIZED",
                    color = if (isExempt) colors.green else colors.amber
                )
            }

            Text(
                text = "Prevents Xiaomi MIUI, Samsung OneUI, or Pixel adaptive battery daemons from stopping the VPN or Accessibility service.",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            OutlinedButton(
                onClick = { openBatterySettings(context) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Text(
                    text = if (isExempt) "Battery Unrestricted (Review Settings)" else "Set Battery to Unrestricted",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isExempt) colors.textPrimary else colors.amberSoftText
                )
            }
        }
    }
}

@Composable
fun DeviceOwnerSetupCard(
    status: DeviceOwnerStatus,
    onCopyCommand: (String, String) -> Unit
) {
    val colors = ZevTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_owner_setup_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = if (status.isDeviceOwner) colors.green else colors.amber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Device Owner Provisioning (ADB / Shizuku)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                }
                StatusChip(active = status.isDeviceOwner)
            }

            Text(
                text = "Enables OS-level anti-uninstall, blocks Safe Mode boot, locks Private DNS (DoT), and enforces Always-On VPN lockdown.",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            CommandCopyBox(
                label = "ADB Shell Command",
                command = status.adbCommand,
                onCopy = { onCopyCommand(status.adbCommand, "ADB command copied!") }
            )

            CommandCopyBox(
                label = "Shizuku / Local Shell Command",
                command = status.shizukuCommand,
                onCopy = { onCopyCommand(status.shizukuCommand, "Shizuku command copied!") }
            )
        }
    }
}

@Composable
private fun CommandCopyBox(
    label: String,
    command: String,
    onCopy: () -> Unit
) {
    val colors = ZevTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.background)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = colors.teal,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun StatusChip(active: Boolean) {
    val colors = ZevTheme.colors
    ZevStatusBadge(
        label = if (active) "ACTIVE" else "INACTIVE",
        color = if (active) colors.green else colors.amber,
        showDot = true,
        pulse = active
    )
}

fun checkBatteryOptimizationExempt(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
        true
    }
}

fun checkNotificationPermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun requestStandardDeviceAdmin(context: Context, deviceAdminManager: DeviceAdminManager) {
    try {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminManager.adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Adds OS-level uninstall friction to support ZevBlock anti-tamper safeguards."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppInfoSettings(context)
    }
}

private fun openBatterySettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        }
    } catch (_: Exception) { }
    openAppInfoSettings(context)
}

private fun openAppInfoSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun copyToClipboard(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ZevBlock", text))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
