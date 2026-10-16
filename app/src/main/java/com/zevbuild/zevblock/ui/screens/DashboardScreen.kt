package com.zevbuild.zevblock.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import com.zevbuild.zevblock.R
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.zevbuild.zevblock.core.stats.DayStat
import com.zevbuild.zevblock.core.stats.ProtectionStatsManager
import com.zevbuild.zevblock.core.updater.AppUpdateManager
import com.zevbuild.zevblock.core.updater.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevblock.accessibility.AccessibilityLogEntry
import com.zevbuild.zevblock.core.admin.DeviceAdminManager
import com.zevbuild.zevblock.core.admin.DeviceOwnerStatus
import com.zevbuild.zevblock.core.dns.DnsLogEntry
import com.zevbuild.zevblock.ui.theme.ShieldAmber
import com.zevbuild.zevblock.ui.theme.ShieldBorderDark
import com.zevbuild.zevblock.ui.theme.ShieldCardDark
import com.zevbuild.zevblock.ui.theme.ShieldGreen
import com.zevbuild.zevblock.ui.theme.ShieldGreenDark
import com.zevbuild.zevblock.ui.theme.ShieldNavyDark
import com.zevbuild.zevblock.ui.theme.ShieldRed
import com.zevbuild.zevblock.ui.theme.ShieldSurfaceDark
import com.zevbuild.zevblock.ui.theme.ShieldTeal
import com.zevbuild.zevblock.ui.theme.TextMutedDark
import com.zevbuild.zevblock.ui.theme.TextPrimaryDark
import com.zevbuild.zevblock.ui.theme.TextSecondaryDark

/**
 * Main Dashboard Status Screen:
 * Presents the primary operational overview of ShieldGuard OS,
 * showing live defense posture, real-time counters, module statuses,
 * and recent security intercept activity.
 */
@Composable
fun DashboardScreen(
    deviceOwnerStatus: DeviceOwnerStatus,
    isVpnRunning: Boolean,
    isAccessibilityActive: Boolean,
    hasPartnerConfigured: Boolean,
    isCooldownActive: Boolean,
    isBypassActive: Boolean = false,
    bypassRemainingMs: Long = 0L,
    ruleCount: Int,
    dnsLogs: List<DnsLogEntry>,
    accessibilityLogs: List<AccessibilityLogEntry>,
    statsManager: ProtectionStatsManager? = null,
    onToggleVpn: () -> Unit,
    onApplyLockdown: () -> Unit,
    onReleaseLockdown: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onCancelBypass: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val updateStatus by AppUpdateManager.updateStatus.collectAsState()

    val past7Days by (statsManager?.past7Days ?: remember { MutableStateFlow(emptyList<DayStat>()) }).collectAsState()
    val todayStat by (statsManager?.todayStat ?: remember { MutableStateFlow(DayStat("Today", "", 0, 0, 0, 0, 0, 0)) }).collectAsState()
    val allTimeBlocks by (statsManager?.allTimeTotalBlocks ?: remember { MutableStateFlow(0L) }).collectAsState()
    val streakDays by (statsManager?.streakDays ?: remember { MutableStateFlow(1) }).collectAsState()

    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdate(context)
    }

    val isFullDefenseActive = isVpnRunning && isAccessibilityActive && deviceOwnerStatus.isDeviceOwner && !isBypassActive

    // Pulsing animation for shield
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isFullDefenseActive) 1.05f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Urgent Temporary Bypass Active Banner
        if (isBypassActive) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("temporary_bypass_active_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ShieldAmber.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ShieldAmber)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ShieldAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CUSTOM RULES BYPASS ACTIVE", fontWeight = FontWeight.Bold, color = ShieldAmber, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val sec = bypassRemainingMs / 1000
                        val min = sec / 60
                        val s = sec % 60
                        Text(
                            text = "Custom rules unlocked • Adult Shield strictly ACTIVE • %02d:%02d left".format(min, s),
                            color = TextPrimaryDark,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = onCancelBypass,
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Re-Lock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Quick 2-Step Setup Card (Shown when core setup is incomplete)
        if (!isAccessibilityActive || !isVpnRunning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_setup_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ShieldAmber)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ShieldAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Easy Setup: 2 Quick Steps to Protect Phone",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Step 1: Accessibility
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldNavyDark)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAccessibilityActive) "✅ 1. Screen & Keyword Guard" else "⚠️ 1. Screen & Keyword Guard",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAccessibilityActive) ShieldGreen else TextPrimaryDark
                            )
                            Text(
                                text = if (isAccessibilityActive) "Active • Inspecting URLs & Incognito" else "Required • Tap below to enable",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }

                        if (!isAccessibilityActive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { openAppInfo(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ShieldAmber),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("App Info", fontSize = 10.sp, color = ShieldNavyDark, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { openAccessibility(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Enable", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Step 2: VPN Filter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldNavyDark)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isVpnRunning) "✅ 2. Network SafeSearch Blocker" else "⚠️ 2. Network SafeSearch Blocker",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVpnRunning) ShieldGreen else TextPrimaryDark
                            )
                            Text(
                                text = if (isVpnRunning) "Active • Sinkholing adult DNS" else "Required • 1 tap to start VPN",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }

                        if (!isVpnRunning) {
                            Button(
                                onClick = onToggleVpn,
                                colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Start", fontSize = 10.sp, color = ShieldGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        // New Update Prompt Card
        if (updateStatus is UpdateStatus.UpdateAvailable) {
            val release = (updateStatus as UpdateStatus.UpdateAvailable).release
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_update_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ShieldNavyDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ShieldTeal)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update Available",
                            tint = ShieldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Update: ${release.tagName}",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = release.releaseNotes.take(160),
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { AppUpdateManager.downloadApk(context, release) },
                            colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Download & Install", color = ShieldGreen, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { AppUpdateManager.dismissUpdate() }
                        ) {
                            Text("Later", color = TextMutedDark)
                        }
                    }
                }
            }
        } else if (updateStatus is UpdateStatus.Downloading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = ShieldTeal,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Downloading latest release from GitHub...",
                        color = TextPrimaryDark,
                        fontSize = 13.sp
                    )
                }
            }
        }
        // Hero Shield Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hero_status_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (isFullDefenseActive) ShieldGreen else if (isVpnRunning || isAccessibilityActive) ShieldTeal else ShieldBorderDark
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Shield Emblem
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isFullDefenseActive) ShieldGreen.copy(alpha = 0.18f)
                            else if (isVpnRunning || isAccessibilityActive) ShieldTeal.copy(alpha = 0.18f)
                            else ShieldAmber.copy(alpha = 0.15f)
                        )
                        .border(
                            2.dp,
                            if (isFullDefenseActive) ShieldGreen
                            else if (isVpnRunning || isAccessibilityActive) ShieldTeal
                            else ShieldAmber,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zevblock_logo),
                        contentDescription = "ZevBlock Shield",
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isFullDefenseActive) "PROTECTION MAXIMUM"
                    else if (isVpnRunning || isAccessibilityActive) "PROTECTION ACTIVE"
                    else "STANDBY • SETUP INCOMPLETE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                    color = if (isFullDefenseActive) ShieldGreen
                    else if (isVpnRunning || isAccessibilityActive) ShieldTeal
                    else ShieldAmber
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isFullDefenseActive) "All Anti-Bypass Safeguards Enforced"
                    else "Complete permissions setup for full anti-tamper security",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryDark
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Real-time counter metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "DNS Rules",
                        value = "$ruleCount",
                        color = ShieldTeal
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "SafeSearch",
                        value = "VIP Active",
                        color = ShieldGreen
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "Anti-Tamper",
                        value = if (isAccessibilityActive) "Guarded" else "Off",
                        color = if (isAccessibilityActive) ShieldGreen else ShieldAmber
                    )
                }
            }
        }

        // 7-Day Visual Activity Bar Chart & Protection Streak
        WeeklyProtectionBarChart(
            past7Days = past7Days,
            streakDays = streakDays,
            allTimeTotalBlocks = allTimeBlocks,
            todayTotalBlocks = todayStat.totalBlocks
        )

        // Module Defense Grid (2x2 Cards)
        Text(
            text = "DEFENSE MODULES",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TextSecondaryDark,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModuleStatusCard(
                modifier = Modifier.weight(1f),
                title = "DNS Sinkhole",
                subtitle = if (isVpnRunning) "Filtering traffic" else "Stopped",
                active = isVpnRunning,
                icon = Icons.Default.Dns,
                activeColor = ShieldGreen,
                onClick = onToggleVpn
            )

            ModuleStatusCard(
                modifier = Modifier.weight(1f),
                title = "Browser Guard",
                subtitle = if (isAccessibilityActive) "Scraper running" else "Disabled",
                active = isAccessibilityActive,
                icon = Icons.Default.Visibility,
                activeColor = ShieldGreen,
                onClick = onNavigateToSetup
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModuleStatusCard(
                modifier = Modifier.weight(1f),
                title = "Device Owner",
                subtitle = if (deviceOwnerStatus.isDeviceOwner) "Anti-uninstall locked" else "ADB required",
                active = deviceOwnerStatus.isDeviceOwner,
                icon = Icons.Default.PhoneAndroid,
                activeColor = ShieldGreen,
                onClick = onNavigateToSetup
            )

            ModuleStatusCard(
                modifier = Modifier.weight(1f),
                title = "Partner Lock",
                subtitle = if (hasPartnerConfigured) "Paired (TOTP)" else "Unpaired",
                active = hasPartnerConfigured,
                icon = Icons.Default.Lock,
                activeColor = ShieldTeal,
                onClick = onNavigateToSetup
            )
        }

        // Quick Lockdown Actions Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lockdown_actions_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Anti-Bypass Policies",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimaryDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lock uninstallation, Safe Mode, and Always-On VPN atomically via DevicePolicyManager.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onApplyLockdown,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_apply_policies"),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enforce Lockdown", fontSize = 12.sp, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onReleaseLockdown,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_release_policies"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Release", fontSize = 12.sp, color = TextPrimaryDark)
                    }
                }
            }
        }

        // Recent Activity Feed
        Text(
            text = "LIVE SECURITY ACTIVITY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TextSecondaryDark,
            letterSpacing = 1.sp
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recent_activity_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val combinedLogs = ArrayList<ActivityFeedItem>()

                dnsLogs.take(5).forEach {
                    combinedLogs.add(
                        ActivityFeedItem(
                            title = it.domain,
                            subtitle = if (it.isSafeSearchRewritten) "SafeSearch VIP Rewritten" else if (it.isBlocked) "Blocked: ${it.reason}" else "Allowed",
                            badgeColor = if (it.isSafeSearchRewritten) ShieldTeal else if (it.isBlocked) ShieldRed else ShieldGreen,
                            timestamp = it.timestamp
                        )
                    )
                }

                accessibilityLogs.take(5).forEach {
                    combinedLogs.add(
                        ActivityFeedItem(
                            title = it.actionTitle,
                            subtitle = "${it.packageName} • ${it.detail}",
                            badgeColor = ShieldAmber,
                            timestamp = it.timestamp
                        )
                    )
                }

                if (combinedLogs.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ShieldGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("No violations recorded. All network queries and browser activity secure.", fontSize = 12.sp, color = TextSecondaryDark)
                    }
                } else {
                    combinedLogs.sortedByDescending { it.timestamp }.take(6).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(item.badgeColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimaryDark)
                                Text(item.subtitle, fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }
                    }
                }
            }
        }

        // Release Channel & Updates Footer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ZevBlock Release Channel",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Version: v${AppUpdateManager.getInstalledVersionName(context)} • GitHub Stable",
                        fontSize = 11.sp,
                        color = ShieldTeal
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            val result = AppUpdateManager.checkForUpdate(context)
                            if (result is UpdateStatus.UpToDate) {
                                Toast.makeText(context, "ZevBlock is up to date!", Toast.LENGTH_SHORT).show()
                            } else if (result is UpdateStatus.Error) {
                                Toast.makeText(context, "Update check: ${result.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldNavyDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (updateStatus is UpdateStatus.Checking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ShieldTeal, strokeWidth = 2.dp)
                    } else {
                        Text("Check Updates", fontSize = 11.sp, color = ShieldTeal)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun MetricBox(modifier: Modifier, title: String, value: String, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ShieldNavyDark)
            .border(1.dp, ShieldBorderDark, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, fontSize = 10.sp, color = TextSecondaryDark)
        }
    }
}

@Composable
fun ModuleStatusCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ShieldCardDark)
            .border(1.dp, ShieldBorderDark, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) activeColor else TextMutedDark,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (active) activeColor else ShieldRed.copy(alpha = 0.5f))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            Text(subtitle, fontSize = 11.sp, color = TextSecondaryDark, maxLines = 1)
        }
    }
}

data class ActivityFeedItem(
    val title: String,
    val subtitle: String,
    val badgeColor: Color,
    val timestamp: Long
)

private fun openAppInfo(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun openAccessibility(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

@Composable
fun WeeklyProtectionBarChart(
    past7Days: List<DayStat>,
    streakDays: Int,
    allTimeTotalBlocks: Long,
    todayTotalBlocks: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header with Streak Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PROTECTION HISTORY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "7-Day Activity & Streak",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }

                // Streak Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ShieldGreen.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldGreen))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔥 $streakDays Days Clean",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ShieldGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bar Chart Layout
            val maxBlocks = (past7Days.maxOfOrNull { it.totalBlocks } ?: 1L).coerceAtLeast(10L).toFloat()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                past7Days.forEach { day ->
                    val fraction = (day.totalBlocks.toFloat() / maxBlocks).coerceIn(0.08f, 1f)
                    val isToday = day.dayLabel == "Today"

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Value label above bar
                        Text(
                            text = if (day.totalBlocks > 0) "${day.totalBlocks}" else "0",
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) ShieldGreen else TextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bar with gradient
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((90 * fraction).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (isToday) {
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            listOf(ShieldGreen, ShieldTeal)
                                        )
                                    } else if (day.totalBlocks > 0) {
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            listOf(ShieldTeal.copy(alpha = 0.8f), ShieldTeal.copy(alpha = 0.3f))
                                        )
                                    } else {
                                        androidx.compose.ui.graphics.SolidColor(ShieldBorderDark)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Day label (e.g. Mon, Tue, Today)
                        Text(
                            text = day.dayLabel,
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (isToday) ShieldGreen else TextSecondaryDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Distribution Summary Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(
                    modifier = Modifier.weight(1f),
                    title = "DNS Sinks",
                    count = past7Days.sumOf { it.dnsBlocked },
                    color = ShieldGreen
                )
                StatPill(
                    modifier = Modifier.weight(1f),
                    title = "Screen Blocks",
                    count = past7Days.sumOf { it.screenBlocked },
                    color = ShieldTeal
                )
                StatPill(
                    modifier = Modifier.weight(1f),
                    title = "Chat Warnings",
                    count = past7Days.sumOf { it.chatWarnings },
                    color = ShieldAmber
                )
            }
        }
    }
}

@Composable
fun StatPill(
    modifier: Modifier = Modifier,
    title: String,
    count: Long,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.10f),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.3f)))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$count", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
            Text(text = title, fontSize = 10.sp, color = TextSecondaryDark, maxLines = 1)
        }
    }
}


