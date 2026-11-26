package com.zevbuild.zevblock.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevblock.R
import com.zevbuild.zevblock.accessibility.AccessibilityLogEntry
import com.zevbuild.zevblock.core.admin.DeviceOwnerStatus
import com.zevbuild.zevblock.core.dns.DnsLogEntry
import com.zevbuild.zevblock.core.stats.DayStat
import com.zevbuild.zevblock.core.stats.ProtectionStatsManager
import com.zevbuild.zevblock.core.updater.AppUpdateManager
import com.zevbuild.zevblock.core.updater.UpdateStatus
import com.zevbuild.zevblock.ui.components.AnimatedRollingCounter
import com.zevbuild.zevblock.ui.components.RadarPulseDot
import com.zevbuild.zevblock.ui.components.SpringBounceBox
import com.zevbuild.zevblock.ui.components.ZevStatusBadge
import com.zevbuild.zevblock.ui.components.formatDurationClock
import com.zevbuild.zevblock.ui.theme.ZevTheme
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shield Overview Screen ("Home"):
 * Focused 4-block layout reporting true OS state for VPN, Accessibility, and Device Hardening,
 * interactive 7-day protection breakdown, and on-demand access to the Live DNS Telemetry Sheet.
 */
@Composable
fun DashboardScreen(
    deviceOwnerStatus: DeviceOwnerStatus,
    isVpnRunning: Boolean,
    isAccessibilityActive: Boolean,
    safeSearchEnabled: Boolean = true,
    hasPartnerConfigured: Boolean,
    isCooldownActive: Boolean,
    isBypassActive: Boolean = false,
    bypassRemainingMs: Long = 0L,
    isRecoveryNeeded: Boolean = false,
    ruleCount: Int,
    dnsLogs: List<DnsLogEntry>,
    accessibilityLogs: List<AccessibilityLogEntry>,
    statsManager: ProtectionStatsManager? = null,
    onToggleVpn: () -> Unit,
    onApplyLockdown: () -> Unit,
    onReleaseLockdown: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToPartner: () -> Unit = onNavigateToSetup,
    onNavigateToSettings: () -> Unit = onNavigateToSetup,
    onOpenLiveDnsSheet: () -> Unit = {},
    onCancelBypass: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = ZevTheme.colors
    val updateStatus by AppUpdateManager.updateStatus.collectAsState()

    val past7Days by (statsManager?.past7Days ?: remember { MutableStateFlow(emptyList<DayStat>()) }).collectAsState()
    val todayStat by (statsManager?.todayStat ?: remember { MutableStateFlow(DayStat("Today", "", 0, 0, 0, 0, 0, 0)) }).collectAsState()
    val streakDays by (statsManager?.streakDays ?: remember { MutableStateFlow(1) }).collectAsState()
    val dlSpeedBps by DnsBlockerVpnService.downloadSpeedBps.collectAsState()
    val ulSpeedBps by DnsBlockerVpnService.uploadSpeedBps.collectAsState()
    val latencyMs by DnsBlockerVpnService.lastUpstreamLatencyMs.collectAsState()
    val activeUpstream by DnsBlockerVpnService.activeUpstreamLabel.collectAsState()

    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdateOncePerSession(context)
    }

    val isCoreActive = isVpnRunning && isAccessibilityActive
    val isMaxFortress = isCoreActive && deviceOwnerStatus.isDeviceOwner && !isBypassActive

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1A. Bypass Active Banner
        if (isBypassActive) {
            item(key = "bypass_banner") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("temporary_bypass_active_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.amber.copy(alpha = 0.14f)),
                    border = BorderStroke(1.dp, colors.amber)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = colors.amberSoftText, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CUSTOM BYPASS ACTIVE", fontWeight = FontWeight.Bold, color = colors.amberSoftText, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Custom rules paused • ${formatDurationClock(bypassRemainingMs)} left • Adult Shield locked",
                                color = colors.textPrimary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onCancelBypass,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.redBtn),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Re-Lock", color = colors.redBtnFg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 1B. Interrupted Service / Battery Recovery Prompt
        if (isRecoveryNeeded && (!isVpnRunning || !isAccessibilityActive)) {
            item(key = "recovery_banner") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_recovery_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.amber)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Protection Interrupted",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.amberSoftText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (!isAccessibilityActive) "Browser Guard was stopped by Android. Check battery exemption and re-enable."
                                else "Local DNS VPN stopped unexpectedly. Tap to resume network filtering.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onNavigateToSetup,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.amber),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Fix Now", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.amberBtnFg)
                        }
                    }
                }
            }
        } else if (!isAccessibilityActive || !isVpnRunning) {
            // 1C. Quick Core Activation Card (only shown when core permissions not yet active)
            item(key = "quick_setup_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_setup_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = colors.amberSoftText, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Complete Core Shield Setup", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
                            }
                            Text(
                                text = "${listOf(isAccessibilityActive, isVpnRunning).count { it }}/2",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = colors.amberSoftText
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToSetup,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Guided Setup Wizard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                            }
                            if (!isVpnRunning) {
                                Button(
                                    onClick = onToggleVpn,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Start VPN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.greenBtnFg)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1D. OTA Update Card
        if (updateStatus is UpdateStatus.UpdateAvailable) {
            item(key = "update_available_card") {
                val release = (updateStatus as UpdateStatus.UpdateAvailable).release
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_update_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.teal)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Update Available", tint = colors.green, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Available: ${release.tagName}", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(release.releaseNotes.take(150), color = colors.textSecondary, fontSize = 12.sp, maxLines = 3)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { AppUpdateManager.downloadApk(context, release) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Download & Install", color = colors.greenBtnFg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { AppUpdateManager.dismissUpdate() }, shape = RoundedCornerShape(10.dp)) {
                                Text("Later", color = colors.textMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else if (updateStatus is UpdateStatus.Downloading) {
            item(key = "update_downloading_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.teal, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Downloading latest release from GitHub...", color = colors.textPrimary, fontSize = 13.sp)
                    }
                }
            }
        }

        // 2. Hero Posture Card (Truthful Independent Posture + Honest KPIs)
        item(key = "hero_status_card") {
            val postureBadge: String
            val postureHeadline: String
            val postureColor: Color

            when {
                isBypassActive -> {
                    postureBadge = "BYPASS ACTIVE"
                    postureHeadline = "Custom rules paused • Adult & Incognito locked"
                    postureColor = colors.amber
                }
                isMaxFortress -> {
                    postureBadge = "MAXIMUM FORTRESS"
                    postureHeadline = "All shields + Device Owner lock active"
                    postureColor = colors.green
                }
                isCoreActive -> {
                    postureBadge = "CORE PROTECTED"
                    postureHeadline = "DNS filter & screen guard running"
                    postureColor = colors.green
                }
                isVpnRunning || isAccessibilityActive -> {
                    postureBadge = "PARTIAL PROTECTION"
                    postureHeadline = if (!isAccessibilityActive) "Browser guard off — DNS active" else "DNS VPN stopped — Browser guard active"
                    postureColor = colors.amber
                }
                else -> {
                    postureBadge = "STANDBY"
                    postureHeadline = "Enable DNS & Screen Guard to start protection"
                    postureColor = colors.red
                }
            }

            val safeSearchText = if (isVpnRunning && safeSearchEnabled) "Enforced" else "Off"
            val safeSearchColor = if (isVpnRunning && safeSearchEnabled) colors.teal else colors.amber

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_status_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.2.dp, postureColor.copy(alpha = 0.75f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(postureColor.copy(alpha = 0.14f))
                                .border(2.dp, postureColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_zevblock_logo),
                                contentDescription = "ZevBlock Shield",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = postureBadge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = postureColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = postureHeadline,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // 3 Honest KPI Metrics (no hardcoded SafeSearch or synthetic floors)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            title = "Blocked Today",
                            value = "${todayStat.totalBlocks}",
                            numericValue = todayStat.totalBlocks,
                            color = colors.textPrimary
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            title = "Clean Streak",
                            value = "${streakDays}d",
                            color = colors.green
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            title = "SafeSearch",
                            value = safeSearchText,
                            color = safeSearchColor
                        )
                    }

                    // Primary Actions Row (with Partner-gated Pause button contrast fixed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenLiveDnsSheet,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("open_live_dns_sheet_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Inspect Live DNS",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealBtnFg
                            )
                        }

                        OutlinedButton(
                            onClick = onToggleVpn,
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("dashboard_toggle_vpn_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                text = if (isVpnRunning) {
                                    if (hasPartnerConfigured) "Pause Lock" else "Stop VPN"
                                } else "Start VPN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. Compact Live Telemetry Strip (opens detailed wave & RAM-only logs sheet)
        item(key = "live_telemetry_strip") {
            val latencyLabel = when {
                !isVpnRunning -> "VPN idle"
                latencyMs > 0L -> "$activeUpstream • ${latencyMs}ms"
                else -> "$activeUpstream • Ready"
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenLiveDnsSheet() }
                    .testTag("compact_telemetry_strip"),
                color = colors.card,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isVpnRunning) "↓ ${DnsBlockerVpnService.formatSpeed(dlSpeedBps)}  ↑ ${DnsBlockerVpnService.formatSpeed(ulSpeedBps)}"
                            else "$ruleCount DNS & Custom Rules Ready",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isVpnRunning) colors.green else colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = latencyLabel,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    ZevStatusBadge(
                        label = "STREAM",
                        color = colors.teal,
                        showDot = isVpnRunning,
                        pulse = isVpnRunning
                    )
                }
            }
        }

        // 4. 7-Day Activity Bar Chart with Interactive Day Selection
        item(key = "weekly_bar_chart") {
            WeeklyProtectionBarChart(
                past7Days = past7Days,
                streakDays = streakDays
            )
        }

        // 5. 3 Independent Defense Layers + Accountability Status
        item(key = "defense_modules_grid") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DEFENSE LAYERS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary,
                    letterSpacing = 0.8.sp
                )

                LayerRowCard(
                    title = "Network DNS Sinkhole",
                    subtitle = if (isVpnRunning) "Local port 53 filter • $ruleCount rules active" else "VPN stopped",
                    badgeText = if (isVpnRunning) "ACTIVE" else "OFF",
                    badgeColor = if (isVpnRunning) colors.green else colors.red,
                    pulse = isVpnRunning,
                    icon = Icons.Default.Dns,
                    onClick = onToggleVpn
                )

                LayerRowCard(
                    title = "Screen, Browser & Chat Guard",
                    subtitle = if (isAccessibilityActive) "Inspecting 30+ browsers, Incognito & Settings" else "Accessibility permission required",
                    badgeText = if (isAccessibilityActive) "ACTIVE" else "OFF",
                    badgeColor = if (isAccessibilityActive) colors.green else colors.red,
                    pulse = isAccessibilityActive,
                    icon = Icons.Default.Visibility,
                    onClick = onNavigateToSetup
                )

                val hardeningSubtitle = when {
                    deviceOwnerStatus.isDeviceOwner -> "Uninstall, Safe Boot & Private DNS locked"
                    deviceOwnerStatus.isAdminActive -> "Standard Device Admin active (Safe Boot needs ADB)"
                    else -> "Optional ADB Device Owner or Standard Admin"
                }
                val hardeningBadge = when {
                    deviceOwnerStatus.isDeviceOwner -> "OWNER"
                    deviceOwnerStatus.isAdminActive -> "BASIC"
                    else -> "OFF"
                }
                val hardeningColor = when {
                    deviceOwnerStatus.isDeviceOwner -> colors.green
                    deviceOwnerStatus.isAdminActive -> colors.amber
                    else -> colors.textMuted
                }

                LayerRowCard(
                    title = "Device Hardening",
                    subtitle = hardeningSubtitle,
                    badgeText = hardeningBadge,
                    badgeColor = hardeningColor,
                    pulse = deviceOwnerStatus.isDeviceOwner,
                    icon = Icons.Default.PhoneAndroid,
                    onClick = onNavigateToSettings
                )

                LayerRowCard(
                    title = "Accountability Partner Lock",
                    subtitle = when {
                        isCooldownActive -> "Timed delay unlock active"
                        hasPartnerConfigured -> "Paired with offline RFC 6238 TOTP"
                        else -> "Pair a trusted friend's authenticator"
                    },
                    badgeText = if (hasPartnerConfigured) "LOCKED" else "UNPAIRED",
                    badgeColor = if (hasPartnerConfigured) colors.teal else colors.textMuted,
                    pulse = false,
                    icon = Icons.Default.Lock,
                    onClick = onNavigateToPartner
                )
            }
        }

        // 6. Device Owner Policy Controls (only actionable when Device Owner is genuinely active)
        if (deviceOwnerStatus.isDeviceOwner) {
            item(key = "lockdown_actions_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lockdown_actions_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Hardware Anti-Bypass Policies", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lock uninstall, Safe Mode boot, Private DNS modification, and Always-On VPN via DevicePolicyManager.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onApplyLockdown,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_apply_policies"),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.greenBtnFg)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enforce", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.greenBtnFg)
                            }
                            OutlinedButton(
                                onClick = onReleaseLockdown,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_release_policies"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Release", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        // 7. Recent High-Signal Security Events (focuses on Screen/Tamper/Block alerts without duplicating full DNS log)
        item(key = "recent_activity_section") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RECENT INTERCEPT EVENTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary,
                    letterSpacing = 0.8.sp
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recent_activity_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val combinedLogs = ArrayList<ActivityFeedItem>()
                        accessibilityLogs.take(5).forEach {
                            combinedLogs.add(
                                ActivityFeedItem(
                                    title = it.actionTitle,
                                    subtitle = "${it.packageName} • ${it.detail}",
                                    badgeColor = colors.amberSoftText,
                                    timestamp = it.timestamp
                                )
                            )
                        }
                        dnsLogs.filter { it.isBlocked }.take(3).forEach {
                            combinedLogs.add(
                                ActivityFeedItem(
                                    title = it.domain,
                                    subtitle = "DNS Sinkholed: ${it.reason}",
                                    badgeColor = colors.red,
                                    timestamp = it.timestamp
                                )
                            )
                        }

                        if (combinedLogs.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Quiet session. No blocked sites or tamper attempts recorded yet.",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        } else {
                            combinedLogs.sortedByDescending { it.timestamp }.take(5).forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.background)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(entry.badgeColor)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                        Text(entry.subtitle, fontSize = 11.sp, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MetricBox(
    modifier: Modifier,
    title: String,
    value: String,
    color: Color,
    numericValue: Long? = null
) {
    val colors = ZevTheme.colors
    SpringBounceBox(modifier = modifier, scaleDown = 0.96f) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (numericValue != null) {
                    AnimatedRollingCounter(
                        value = numericValue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                } else {
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(title, fontSize = 11.sp, color = colors.textMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LayerRowCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    pulse: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val colors = ZevTheme.colors
    SpringBounceBox(
        modifier = Modifier.fillMaxWidth(),
        scaleDown = 0.98f,
        onClick = onClick
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text(subtitle, fontSize = 12.sp, color = colors.textSecondary, maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                ZevStatusBadge(
                    label = badgeText,
                    color = badgeColor,
                    showDot = true,
                    pulse = pulse
                )
            }
        }
    }
}

@Composable
fun WeeklyProtectionBarChart(
    past7Days: List<DayStat>,
    streakDays: Int
) {
    val colors = ZevTheme.colors
    var selectedIdx by remember(past7Days) {
        mutableIntStateOf((past7Days.size - 1).coerceAtLeast(0))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "7-DAY PROTECTION MOMENTUM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Daily Intercepts",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.green.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, colors.green.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "$streakDays Days Protected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = colors.green,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val maxBlocks = (past7Days.maxOfOrNull { it.totalBlocks } ?: 1L).coerceAtLeast(10L).toFloat()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                past7Days.forEachIndexed { index, day ->
                    val fraction = (day.totalBlocks.toFloat() / maxBlocks).coerceIn(0.1f, 1f)
                    val isSelected = index == selectedIdx
                    val isToday = day.dayLabel == "Today"

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedIdx = index }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "${day.totalBlocks}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.green else colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((72 * fraction).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    when {
                                        isSelected -> Brush.verticalGradient(listOf(colors.green, colors.teal))
                                        day.totalBlocks > 0 -> SolidColor(colors.teal.copy(alpha = 0.5f))
                                        else -> SolidColor(colors.border)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.dayLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.textPrimary else colors.textMuted
                        )
                    }
                }
            }

            val inspectedDay = past7Days.getOrNull(selectedIdx)
            if (inspectedDay != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${inspectedDay.dayLabel}: DNS ${inspectedDay.dnsBlocked} • Screen ${inspectedDay.screenBlocked} • Chat ${inspectedDay.chatWarnings} • Tamper ${inspectedDay.settingsBlocked}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

data class ActivityFeedItem(
    val title: String,
    val subtitle: String,
    val badgeColor: Color,
    val timestamp: Long
)
