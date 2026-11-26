package com.zevbuild.zevblock.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.zevbuild.zevblock.core.accessibility.NsfwContentDetector
import com.zevbuild.zevblock.core.admin.DeviceOwnerStatus
import com.zevbuild.zevblock.core.diagnostics.CrashGuardManager
import com.zevbuild.zevblock.core.prefs.ZevBlockPreferences
import com.zevbuild.zevblock.core.security.TamperLockManager
import com.zevbuild.zevblock.core.stats.ProtectionStatsManager
import com.zevbuild.zevblock.core.updater.AppUpdateManager
import com.zevbuild.zevblock.core.updater.UpdateStatus
import com.zevbuild.zevblock.ui.components.PartnerUnlockGateDialog
import com.zevbuild.zevblock.ui.components.SegmentedOtpInput
import com.zevbuild.zevblock.ui.components.ZevStatusBadge
import com.zevbuild.zevblock.ui.components.formatDurationClock
import com.zevbuild.zevblock.ui.theme.ZevTheme
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings Screen ("Settings" Tab):
 * Unifies System Health & Permissions, Guard Controls (Partner-gated when paired),
 * Theme Selection, Privacy & Technical Limits Disclosures, Updates, and Developer & QA Lab.
 */
@Composable
fun SettingsScreen(
    prefs: ZevBlockPreferences,
    tamperLockManager: TamperLockManager,
    statsManager: ProtectionStatsManager,
    deviceOwnerStatus: DeviceOwnerStatus,
    isVpnRunning: Boolean,
    isAccessibilityActive: Boolean,
    isBatteryExempt: Boolean,
    hasNotificationPermission: Boolean,
    hasPartnerConfigured: Boolean,
    onOpenGuidedSetup: () -> Unit,
    onTestOverlay: () -> Unit,
    onViewPurposeMission: () -> Unit,
    onStartTestCooldown: () -> Unit
) {
    val context = LocalContext.current
    val colors = ZevTheme.colors
    val scope = rememberCoroutineScope()

    val themeMode by prefs.themeMode.collectAsState()
    val notifMode by prefs.notificationMode.collectAsState()
    val devLabEnabled by prefs.devLabEnabled.collectAsState()
    val incognitoOn by prefs.incognitoBlockEnabled.collectAsState()
    val nsfwOn by prefs.nsfwFilterEnabled.collectAsState()
    val chatWarnOn by prefs.chatWarningEnabled.collectAsState()
    val settingsGuardOn by prefs.settingsGuardEnabled.collectAsState()
    val safeSearchOn by prefs.safeSearchEnabled.collectAsState()
    val activeUpstream by DnsBlockerVpnService.activeUpstreamLabel.collectAsState()
    val updateStatus by AppUpdateManager.updateStatus.collectAsState()

    // Partner gate for weakening guards or resetting stats
    var pendingProtectedAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var resetOtpInput by remember { mutableStateOf("") }
    var resetError by remember { mutableStateOf<String?>(null) }
    var resetLockoutMs by remember { mutableLongStateOf(tamperLockManager.getLockoutRemainingMs()) }

    LaunchedEffect(showResetStatsDialog, resetLockoutMs) {
        if (showResetStatsDialog) {
            while (resetLockoutMs > 0L) {
                delay(1000L)
                resetLockoutMs = tamperLockManager.getLockoutRemainingMs()
            }
        }
    }

    fun runGuardedChange(description: String, requiresPartnerWhenReducing: Boolean, action: () -> Unit) {
        if (requiresPartnerWhenReducing && hasPartnerConfigured) {
            pendingProtectedAction = Pair(description, action)
        } else {
            action()
        }
    }

    pendingProtectedAction?.let { (desc, onGrant) ->
        PartnerUnlockGateDialog(
            title = "Partner Confirmation",
            description = desc,
            tamperLockManager = tamperLockManager,
            onDismiss = { pendingProtectedAction = null },
            onAuthorized = {
                onGrant()
                pendingProtectedAction = null
            }
        )
    }

    if (showResetStatsDialog) {
        AlertDialog(
            onDismissRequest = {
                showResetStatsDialog = false
                resetOtpInput = ""
                resetError = null
            },
            containerColor = colors.surface,
            title = {
                Text("Reset Statistics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your accountability partner's 6-digit TOTP code to permanently clear streak and block counters.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    if (resetLockoutMs > 0L) {
                        Text(
                            text = "Locked for ${formatDurationClock(resetLockoutMs)} due to repeated failed attempts.",
                            fontSize = 12.sp,
                            color = colors.red,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    SegmentedOtpInput(
                        value = resetOtpInput,
                        onValueChange = {
                            resetOtpInput = it
                            resetError = null
                        },
                        inputTestTag = "reset_stats_otp_input"
                    )
                    if (resetError != null) {
                        Text(resetError!!, fontSize = 12.sp, color = colors.red)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val resetOk = statsManager.resetStatsWithPartner(resetOtpInput)
                        resetLockoutMs = tamperLockManager.getLockoutRemainingMs()
                        if (resetOk) {
                            Toast.makeText(context, "Protection statistics reset", Toast.LENGTH_SHORT).show()
                            showResetStatsDialog = false
                            resetOtpInput = ""
                            resetError = null
                        } else {
                            resetError = if (resetLockoutMs > 0L) {
                                "Throttled for ${formatDurationClock(resetLockoutMs)}."
                            } else {
                                "Invalid or already consumed partner TOTP code."
                            }
                        }
                    },
                    enabled = resetOtpInput.length == 6 && resetLockoutMs <= 0L,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.redBtn),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reset Stats", fontWeight = FontWeight.Bold, color = colors.redBtnFg)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResetStatsDialog = false
                    resetOtpInput = ""
                    resetError = null
                }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. System Health & Provisioning Card
        item(key = "settings_health") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("System Health & Permissions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        ZevStatusBadge(
                            label = if (deviceOwnerStatus.isDeviceOwner) "OWNER" else "STANDARD",
                            color = if (deviceOwnerStatus.isDeviceOwner) colors.green else colors.teal
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HealthChip("DNS VPN", isVpnRunning, Modifier.weight(1f))
                        HealthChip("Screen Guard", isAccessibilityActive, Modifier.weight(1f))
                        HealthChip("Battery", isBatteryExempt, Modifier.weight(1f))
                    }

                    if (!hasNotificationPermission) {
                        Text(
                            text = "Notification permission missing: Android 13+ requires it for chat alerts & foreground service status.",
                            fontSize = 12.sp,
                            color = colors.amberSoftText
                        )
                    }

                    Button(
                        onClick = onOpenGuidedSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("open_guided_setup_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open Setup & Device Owner Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.tealBtnFg)
                    }
                }
            }
        }

        // 2. Guard Behavior Controls
        item(key = "settings_guards") {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guard Controls", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                        if (hasPartnerConfigured) {
                            ZevStatusBadge(label = "PARTNER LOCKED", color = colors.teal)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    GuardSwitchRow(
                        title = "Block Incognito Windows",
                        subtitle = "Immediately close private tabs in browsers",
                        checked = incognitoOn,
                        onCheckedChange = { enable ->
                            runGuardedChange(
                                "Disabling Incognito blocking requires Partner authorization.",
                                !enable
                            ) { prefs.setIncognitoBlockEnabled(enable) }
                        }
                    )
                    HorizontalDivider(color = colors.border)
                    GuardSwitchRow(
                        title = "Screen & Browser NSFW Guard",
                        subtitle = "Inspect address bar URLs and screen text in RAM",
                        checked = nsfwOn,
                        onCheckedChange = { enable ->
                            runGuardedChange(
                                "Disabling on-screen NSFW screening requires Partner authorization.",
                                !enable
                            ) { prefs.setNsfwFilterEnabled(enable) }
                        }
                    )
                    HorizontalDivider(color = colors.border)
                    GuardSwitchRow(
                        title = "Chat Vulgarity Reminders",
                        subtitle = "Show non-intrusive toast & notification in messaging apps",
                        checked = chatWarnOn,
                        onCheckedChange = { enable ->
                            runGuardedChange(
                                "Disabling chat accountability reminders requires Partner authorization.",
                                !enable
                            ) { prefs.setChatWarningEnabled(enable) }
                        }
                    )
                    HorizontalDivider(color = colors.border)
                    GuardSwitchRow(
                        title = "Settings Tamper Guard",
                        subtitle = "Intercept force-stop, uninstall & Accessibility tampering",
                        checked = settingsGuardOn,
                        onCheckedChange = { enable ->
                            runGuardedChange(
                                "Disabling Settings anti-tamper guard requires Partner authorization.",
                                !enable
                            ) { prefs.setSettingsGuardEnabled(enable) }
                        }
                    )
                    HorizontalDivider(color = colors.border)
                    GuardSwitchRow(
                        title = "Force Strict SafeSearch VIPs",
                        subtitle = "Rewrite search engines to forced SafeSearch endpoints",
                        checked = safeSearchOn,
                        onCheckedChange = { enable ->
                            runGuardedChange(
                                "Disabling SafeSearch VIP enforcement requires Partner authorization.",
                                !enable
                            ) { prefs.setSafeSearchEnabled(enable) }
                        }
                    )
                    HorizontalDivider(color = colors.border)

                    // Notification display style selector
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notification Shade Display", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ZevBlockPreferences.NOTIF_CLEAN to "Clean Status",
                                ZevBlockPreferences.NOTIF_SPEED to "Live Speed (↓ / ↑)"
                            ).forEach { (mode, label) ->
                                val selected = notifMode == mode
                                OutlinedButton(
                                    onClick = { prefs.setNotificationMode(mode) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) colors.teal.copy(alpha = 0.16f) else colors.background
                                    ),
                                    border = BorderStroke(1.dp, if (selected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) colors.teal else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Appearance Selector
        item(key = "settings_appearance") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ZevBlockPreferences.THEME_DARK to "Slate Dark",
                            ZevBlockPreferences.THEME_LIGHT to "Calm Light",
                            ZevBlockPreferences.THEME_SYSTEM to "System"
                        ).forEach { (mode, label) ->
                            val selected = themeMode == mode
                            OutlinedButton(
                                onClick = { prefs.setThemeMode(mode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) colors.teal.copy(alpha = 0.16f) else colors.background
                                ),
                                border = BorderStroke(1.dp, if (selected) colors.teal else colors.border)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) colors.teal else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Privacy, Upstream DNS & Honest Limitations
        item(key = "settings_privacy") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy, Upstream DNS & Limits", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                    Text("• Active Upstream DNS: $activeUpstream (failover to CleanBrowsing & AdGuard Family over port 53).", fontSize = 12.sp, color = colors.textSecondary)
                    Text("• Local Screen Inspection: URL & keyword inspection runs solely in RAM; zero analytics or disk URL history.", fontSize = 12.sp, color = colors.textSecondary)
                    Text("• Technical Limits: Embedded DNS-over-HTTPS (DoH), cached DNS, or direct IP connections can bypass DNS sinks; Screen Guard covers those layers.", fontSize = 12.sp, color = colors.textSecondary)
                    Text("• Update Checks: Contact api.github.com only when checking GitHub Releases.", fontSize = 12.sp, color = colors.textSecondary)

                    OutlinedButton(
                        onClick = onViewPurposeMission,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Review Purpose & Privacy Summary", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                    }
                }
            }
        }

        // 5. App Updates & Protected Statistics Reset
        item(key = "settings_updates_stats") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("App Version & Updates", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text("v${AppUpdateManager.getInstalledVersionName(context)} • GitHub Releases", fontSize = 12.sp, color = colors.teal)
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
                            colors = ButtonDefaults.buttonColors(containerColor = colors.card),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (updateStatus is UpdateStatus.Checking) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.teal, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.teal)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                            }
                        }
                    }

                    HorizontalDivider(color = colors.border)

                    OutlinedButton(
                        onClick = {
                            if (!hasPartnerConfigured) {
                                Toast.makeText(context, "Pair an accountability partner first to authorize stats reset.", Toast.LENGTH_LONG).show()
                            } else {
                                resetLockoutMs = tamperLockManager.getLockoutRemainingMs()
                                showResetStatsDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.red.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.red)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Statistics (Partner Required)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.red)
                    }
                }
            }
        }

        // 6. Developer & QA Lab (Replaces former 5th bottom tab without cluttering main UI)
        item(key = "settings_dev_lab") {
            DeveloperLabCard(
                expanded = devLabEnabled,
                onToggleExpanded = { prefs.setDevLabEnabled(!devLabEnabled) },
                onTestOverlay = onTestOverlay,
                onStartTestCooldown = onStartTestCooldown
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HealthChip(title: String, active: Boolean, modifier: Modifier = Modifier) {
    val colors = ZevTheme.colors
    val c = if (active) colors.green else colors.amber
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = colors.background,
        border = BorderStroke(1.dp, c.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (active) "ACTIVE" else "OFF", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = c)
            Text(title, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun GuardSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = ZevTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.greenBtnFg,
                checkedTrackColor = colors.greenBtn,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.background
            )
        )
    }
}

@Composable
private fun DeveloperLabCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onTestOverlay: () -> Unit,
    onStartTestCooldown: () -> Unit
) {
    val context = LocalContext.current
    val colors = ZevTheme.colors
    val diagnostics by CrashGuardManager.diagnosticLogs.collectAsState()
    val startupDurationMs by CrashGuardManager.lastStartupDurationMs.collectAsState()
    val autoRecoveredCount by CrashGuardManager.autoRecoveryCount.collectAsState()
    var memorySummary by remember { mutableStateOf(CrashGuardManager.getMemorySummaryMb()) }
    var scraperInput by remember { mutableStateOf("https://www.google.com/search?q=free+xxx+video") }
    var scraperResult by remember { mutableStateOf<String?>(null) }
    val nsfwDetector = remember { NsfwContentDetector.shared }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crash_guard_diagnostics_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = colors.amberSoftText, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Developer & QA Lab", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("CrashGuard telemetry, overlay & scraper testers", fontSize = 12.sp, color = colors.textMuted)
                    }
                }
                OutlinedButton(
                    onClick = onToggleExpanded,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.teal
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (expanded) "Hide" else "Open", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // CrashGuard Metrics
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.background)
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Startup: ${startupDurationMs}ms • Recoveries: $autoRecoveredCount • Heap: $memorySummary",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.green
                            )
                            if (diagnostics.isEmpty()) {
                                Text("Zero unhandled exceptions recorded.", fontSize = 12.sp, color = colors.textSecondary)
                            } else {
                                diagnostics.take(3).forEach { entry ->
                                    Text("[${entry.source}] ${entry.summary}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = colors.amberSoftText)
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                CrashGuardManager.recordServiceRecovery("DevLab", "Simulated recovery check passed")
                                memorySummary = CrashGuardManager.getMemorySummaryMb()
                            },
                            modifier = Modifier.weight(1f).testTag("crash_guard_simulate_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test Guard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.tealBtnFg)
                        }
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("ZevBlock Diagnostics", CrashGuardManager.buildDiagnosticReport()))
                                Toast.makeText(context, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("crash_guard_copy_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Copy Report", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.greenBtnFg)
                        }
                        if (diagnostics.isNotEmpty()) {
                            Button(
                                onClick = {
                                    CrashGuardManager.clearLogs()
                                    memorySummary = CrashGuardManager.getMemorySummaryMb()
                                },
                                modifier = Modifier.testTag("crash_guard_clear_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.redBtn),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.redBtnFg)
                            }
                        }
                    }

                    // Overlay & 1 Min Test Cooldown
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onTestOverlay,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.amber),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Preview Overlay", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.amberBtnFg)
                        }
                        OutlinedButton(
                            onClick = onStartTestCooldown,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("1m Test Delay", fontSize = 12.sp, color = colors.textPrimary)
                        }
                    }

                    // Browser Scraper Inspector
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = scraperInput,
                            onValueChange = { scraperInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                val res = nsfwDetector.analyze(scraperInput.trim())
                                scraperResult = if (res.isNsfw) "BLOCKED: '${res.matchedTerm}' (${res.category})" else "CLEAN: Allowed"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Inspect", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.greenBtnFg)
                        }
                    }
                    if (scraperResult != null) {
                        Text(scraperResult!!, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = colors.amberSoftText)
                    }
                }
            }
        }
    }
}
