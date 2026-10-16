package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.admin.DeviceOwnerStatus
import com.example.ui.theme.ShieldAmber
import com.example.ui.theme.ShieldBorderDark
import com.example.ui.theme.ShieldCardDark
import com.example.ui.theme.ShieldGreen
import com.example.ui.theme.ShieldGreenDark
import com.example.ui.theme.ShieldNavyDark
import com.example.ui.theme.ShieldRed
import com.example.ui.theme.ShieldSurfaceDark
import com.example.ui.theme.ShieldTeal
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

/**
 * Onboarding & Permissions Screen:
 * Guides the user through granting required system permissions,
 * including the visual step-by-step guide for bypassing Android 13+
 * "Restricted Settings" on sideloaded APKs.
 */
@Composable
fun OnboardingScreen(
    deviceOwnerStatus: DeviceOwnerStatus,
    isVpnRunning: Boolean,
    isAccessibilityActive: Boolean,
    onLaunchVpnPrepare: () -> Unit,
    onNavigateToPartnerLock: () -> Unit,
    onViewPurposeMission: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ShieldSurfaceDark)
                .border(1.dp, ShieldBorderDark, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ShieldGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = ShieldGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Permission & Anti-Bypass Setup",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Configure system privileges to secure your device",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onViewPurposeMission,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ShieldTeal)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Protection Mission: Shield from Adult Content",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // SECTION 1: REQUIRED SETUP
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ShieldGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "REQUIRED SETUP (2 SIMPLE STEPS)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = ShieldGreen
            )
        }

        // STEP 1: Accessibility Service Activation (with Android 13+ helper if inactive)
        if (!isAccessibilityActive) {
            RestrictedSettingsBypassCard(
                context = context,
                isAccessibilityActive = isAccessibilityActive
            )
        }

        AccessibilityPermissionCard(
            context = context,
            isActive = isAccessibilityActive
        )

        // STEP 2: Local DNS Sinkhole VPN
        VpnPermissionCard(
            isRunning = isVpnRunning,
            onLaunchVpn = onLaunchVpnPrepare
        )

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION 2: OPTIONAL ADVANCED HARDENING
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ShieldTeal)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "OPTIONAL HARDENING (NOT REQUIRED FOR BLOCKING)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = ShieldTeal
            )
        }

        Text(
            text = "The two steps above already block adult websites, keywords, and incognito across all apps! The features below are optional if you want extra anti-tamper locks.",
            fontSize = 11.sp,
            color = TextSecondaryDark,
            lineHeight = 16.sp
        )

        // OPTIONAL STEP 3: Accountability Partner Pairing Prompt
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("partner_onboarding_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ShieldTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Optional: Accountability Partner",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ShieldTeal.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "OPTIONAL",
                            color = ShieldTeal,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Let a trusted friend hold a 6-digit authenticator QR code so you cannot bypass protection or alter settings without their approval.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onNavigateToPartnerLock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("go_to_partner_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Setup Partner TOTP & QR Code", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        // OPTIONAL STEP 4: Device Owner ADB Provisioning
        DeviceOwnerSetupCard(
            status = deviceOwnerStatus,
            onCopyAdb = { cmd ->
                copyToClipboard(context, cmd, "ADB command copied to clipboard!")
            }
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Visual guide explaining how to bypass the Android 13+ "Restricted Settings" block
 * on sideloaded accessibility apps.
 */
@Composable
fun RestrictedSettingsBypassCard(context: Context, isAccessibilityActive: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("restricted_settings_guide_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isAccessibilityActive) ShieldBorderDark else ShieldAmber.copy(alpha = 0.7f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ShieldAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Android 13+ 'Restricted Settings' Guide",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ShieldAmber.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "OS RESTRICTION",
                        color = ShieldAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "On Android 13, 14, and 15, Android automatically greys out Accessibility for sideloaded APKs with 'Restricted setting: For your security, this setting is currently unavailable'.",
                fontSize = 12.sp,
                color = TextSecondaryDark,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step-by-step visual container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ShieldNavyDark)
                    .border(1.dp, ShieldBorderDark, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BypassStepRow(
                        stepNumber = "1",
                        title = "Open ZevBlock App Info",
                        description = "Tap the button below or go to Settings > Apps > ZevBlock."
                    )
                    BypassStepRow(
                        stepNumber = "2",
                        title = "Tap Top-Right Menu (⋮)",
                        description = "In the top right corner of App Info, tap the three vertical dots menu."
                    )
                    BypassStepRow(
                        stepNumber = "3",
                        title = "Select 'Allow restricted settings'",
                        description = "Verify your device PIN or fingerprint to lift the sideload restriction."
                    )
                    BypassStepRow(
                        stepNumber = "4",
                        title = "Enable Accessibility Service",
                        description = "Return to Accessibility settings where the toggle will now be unblocked."
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { openAppInfoSettings(context) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_app_info_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = ShieldNavyDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1. Open App Info", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ShieldNavyDark)
                }

                OutlinedButton(
                    onClick = { openAccessibilitySettings(context) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_accessibility_from_guide_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("2. Open Accessibility", fontSize = 12.sp, color = TextPrimaryDark)
                }
            }
        }
    }
}

@Composable
fun BypassStepRow(stepNumber: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(ShieldTeal.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = stepNumber, color = ShieldTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
            Text(text = description, fontSize = 11.sp, color = TextSecondaryDark, lineHeight = 15.sp)
        }
    }
}

@Composable
fun AccessibilityPermissionCard(context: Context, isActive: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("accessibility_permission_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = if (isActive) ShieldGreen else ShieldAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Anti-Tamper Accessibility Service",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                StatusChip(active = isActive)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Inspects browser address bars to detect NSFW queries and prevents force-stopping or uninstalling ZevBlock.",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { openAccessibilitySettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activate_accessibility_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) ShieldCardDark else ShieldGreenDark
                ),
                border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, ShieldBorderDark) else null,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isActive) "Service Already Active (Configure)" else "Turn On Accessibility Service",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun VpnPermissionCard(isRunning: Boolean, onLaunchVpn: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_permission_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = if (isRunning) ShieldGreen else ShieldTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local DNS Sinkhole VPN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                StatusChip(active = runningOrActive(isRunning))
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Filters ad trackers, explicit domains, and rewrites search engines to strict SafeSearch VIPs at the network layer.",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onLaunchVpn,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activate_vpn_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isRunning) "DNS Sinkhole Running" else "Grant VPN Permission & Start",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun DeviceOwnerSetupCard(status: DeviceOwnerStatus, onCopyAdb: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_owner_setup_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (status.isDeviceOwner) ShieldGreen else ShieldAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Owner Provisioning",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                StatusChip(active = status.isDeviceOwner)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Device Owner grants hardware-level anti-bypass: blocks uninstallation, blocks Safe Mode boot, and prevents Private DNS tampering.",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShieldNavyDark)
                    .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = status.adbCommand,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ShieldTeal,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCopyAdb(status.adbCommand) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy ADB", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(active: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) ShieldGreen.copy(alpha = 0.15f) else ShieldAmber.copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) ShieldGreen else ShieldAmber)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (active) "ACTIVE" else "INACTIVE",
                color = if (active) ShieldGreen else ShieldAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun runningOrActive(value: Boolean): Boolean = value

private fun openAppInfoSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

private fun copyToClipboard(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("ZevBlock", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
