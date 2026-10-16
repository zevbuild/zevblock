package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.ShieldAccessibilityService
import com.example.core.accessibility.NsfwContentDetector
import com.example.core.admin.DeviceAdminManager
import com.example.core.dns.DnsPacketBuilder
import com.example.core.dns.DnsPacketParser
import com.example.core.dns.DnsRecordType
import com.example.core.security.TamperLockManager
import com.example.ui.overlay.SecurityWarningOverlayActivity
import com.example.ui.theme.MyApplicationTheme
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
import com.example.vpn.DnsBlockerVpnService
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.StringReader

class MainActivity : ComponentActivity() {

    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var tamperLockManager: TamperLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        deviceAdminManager = DeviceAdminManager(this)
        tamperLockManager = TamperLockManager(this)

        setContent {
            MyApplicationTheme {
                ShieldGuardDashboard(
                    deviceAdminManager = deviceAdminManager,
                    tamperLockManager = tamperLockManager
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldGuardDashboard(
    deviceAdminManager: DeviceAdminManager,
    tamperLockManager: TamperLockManager
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var status by remember { mutableStateOf(deviceAdminManager.getStatus()) }
    var partnerSecret by remember { mutableStateOf(tamperLockManager.getPartnerSecret()) }
    var totpInput by remember { mutableStateOf("") }
    var totpResult by remember { mutableStateOf<Boolean?>(null) }

    var cooldownRemainingMs by remember { mutableLongStateOf(tamperLockManager.getRemainingCooldownMs()) }
    var isCooldownActive by remember { mutableStateOf(tamperLockManager.isCooldownActive()) }

    // Live VPN State observation
    val isVpnRunning by DnsBlockerVpnService.isRunning.collectAsState()
    var ruleCount by remember { mutableStateOf(DnsBlockerVpnService.blocklistMatcher.getRuleCount()) }
    var safeSearchEnabled by remember { mutableStateOf(DnsBlockerVpnService.safeSearchManager.isEnabled) }

    // Live Accessibility Service Observation
    val isAccessibilityActive by ShieldAccessibilityService.isServiceActive.collectAsState()
    var incognitoBlockEnabled by remember { mutableStateOf(ShieldAccessibilityService.isIncognitoBlockEnabled) }
    var nsfwFilterEnabled by remember { mutableStateOf(ShieldAccessibilityService.isNsfwFilterEnabled) }
    var settingsGuardEnabled by remember { mutableStateOf(ShieldAccessibilityService.isSettingsGuardEnabled) }

    // DNS Simulation State
    var testDomainInput by remember { mutableStateOf("www.google.com") }
    var testDomainResult by remember { mutableStateOf<String?>(null) }
    var testDomainActionType by remember { mutableStateOf<String?>(null) }

    // Browser Scraper Simulation State
    var testScraperInput by remember { mutableStateOf("https://www.google.com/search?q=free+xxx+video") }
    var testScraperResult by remember { mutableStateOf<String?>(null) }
    var testScraperAction by remember { mutableStateOf<String?>(null) }

    val nsfwDetector = remember { NsfwContentDetector() }

    // VPN Prepare Launcher
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DnsBlockerVpnService.start(context)
            Toast.makeText(context, "VPN Service Started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "VPN Permission was denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Live monotonic countdown ticker
    LaunchedEffect(isCooldownActive) {
        while (isCooldownActive) {
            val remaining = tamperLockManager.getRemainingCooldownMs()
            cooldownRemainingMs = remaining
            isCooldownActive = tamperLockManager.isCooldownActive()
            delay(1000L)
        }
    }

    fun refreshAll() {
        status = deviceAdminManager.getStatus()
        partnerSecret = tamperLockManager.getPartnerSecret()
        cooldownRemainingMs = tamperLockManager.getRemainingCooldownMs()
        isCooldownActive = tamperLockManager.isCooldownActive()
        ruleCount = DnsBlockerVpnService.blocklistMatcher.getRuleCount()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scaffold"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ShieldGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "ShieldGuard Icon",
                                tint = ShieldGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ShieldGuard OS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Deliverables 1, 2 & 3 Active",
                                fontSize = 12.sp,
                                color = ShieldTeal
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshAll() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextSecondaryDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ShieldSurfaceDark
                )
            )
        },
        containerColor = ShieldNavyDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Deliverable Status Banner
            DeliverableBanner(
                isVpnRunning = isVpnRunning,
                isAccessibilityActive = isAccessibilityActive,
                rulesCount = ruleCount
            )

            // Deliverable 3: Accessibility Service Browser Scraper & Settings Guard
            AccessibilityGuardCard(
                isAccessibilityActive = isAccessibilityActive,
                incognitoEnabled = incognitoBlockEnabled,
                nsfwEnabled = nsfwFilterEnabled,
                settingsGuardEnabled = settingsGuardEnabled,
                onOpenSettings = {
                    ShieldAccessibilityService.openAccessibilitySettings(context)
                },
                onToggleIncognito = {
                    incognitoBlockEnabled = it
                    ShieldAccessibilityService.isIncognitoBlockEnabled = it
                },
                onToggleNsfw = {
                    nsfwFilterEnabled = it
                    ShieldAccessibilityService.isNsfwFilterEnabled = it
                },
                onToggleSettingsGuard = {
                    settingsGuardEnabled = it
                    ShieldAccessibilityService.isSettingsGuardEnabled = it
                },
                onTestOverlay = {
                    SecurityWarningOverlayActivity.show(
                        context,
                        alertType = "Security Restriction Test",
                        message = "This simulated alert demonstrates the Jetpack Compose warning overlay triggered on Incognito or settings tampering.",
                        detail = "Triggered via ShieldGuard OS Dashboard"
                    )
                }
            )

            // Deliverable 3: Browser URL & Search Query Simulator
            BrowserScraperSimulatorCard(
                input = testScraperInput,
                onInputChange = { testScraperInput = it },
                resultText = testScraperResult,
                actionText = testScraperAction,
                onEvaluate = {
                    val candidate = testScraperInput.trim()
                    if (candidate.contains("incognito", ignoreCase = true) || candidate.contains("private", ignoreCase = true)) {
                        testScraperAction = "Incognito Mode Detected"
                        testScraperResult = "Trigger GLOBAL_ACTION_BACK + Warning Overlay: Private Browsing Prohibited"
                    } else if (candidate.contains("settings") || candidate.contains("force stop", ignoreCase = true) || candidate.contains("uninstall", ignoreCase = true)) {
                        testScraperAction = "Settings Tamper Detected"
                        testScraperResult = "Trigger GLOBAL_ACTION_BACK + Warning Overlay: Prevented modification of ShieldGuard OS"
                    } else {
                        val nsfw = nsfwDetector.analyze(candidate)
                        if (nsfw.isNsfw) {
                            testScraperAction = "NSFW Content: ${nsfw.category}"
                            testScraperResult = "Trigger GLOBAL_ACTION_BACK + Warning Overlay: Matched keyword '${nsfw.matchedTerm}'"
                        } else {
                            testScraperAction = "Clean Content"
                            testScraperResult = "URL/Search query allowed: No adult keywords or tampering detected"
                        }
                    }
                }
            )

            // Deliverable 2: Local DNS Sinkhole & SafeSearch Card
            DnsSinkholeCard(
                isVpnRunning = isVpnRunning,
                safeSearchEnabled = safeSearchEnabled,
                ruleCount = ruleCount,
                onToggleVpn = {
                    if (isVpnRunning) {
                        DnsBlockerVpnService.stop(context)
                    } else {
                        val prepareIntent = VpnService.prepare(context)
                        if (prepareIntent != null) {
                            vpnPrepareLauncher.launch(prepareIntent)
                        } else {
                            DnsBlockerVpnService.start(context)
                        }
                    }
                },
                onToggleSafeSearch = { enabled ->
                    safeSearchEnabled = enabled
                    DnsBlockerVpnService.safeSearchManager.isEnabled = enabled
                },
                onLoadSampleHosts = {
                    val sampleHosts = """
                        # StevenBlack Hosts Extra Feed
                        0.0.0.0 telemetry.samsung.com
                        0.0.0.0 ads.tiktok.com
                        0.0.0.0 tracking.snapchat.com
                        0.0.0.0 analytics.yahoo.com
                        0.0.0.0 popcash.net
                        0.0.0.0 adsterra.com
                    """.trimIndent()
                    val added = DnsBlockerVpnService.blocklistMatcher.loadFromReader(BufferedReader(StringReader(sampleHosts)))
                    ruleCount = DnsBlockerVpnService.blocklistMatcher.getRuleCount()
                    Toast.makeText(context, "Loaded $added rules from StevenBlack feed", Toast.LENGTH_SHORT).show()
                }
            )

            // DNS Simulation Card
            DnsSimulatorCard(
                domainInput = testDomainInput,
                onDomainInputChange = { testDomainInput = it },
                resultText = testDomainResult,
                actionType = testDomainActionType,
                onTestDomain = {
                    val clean = testDomainInput.trim()
                    if (DnsBlockerVpnService.safeSearchManager.isEnabled &&
                        (clean == "www.google.com" || clean == "google.com" || clean.startsWith("www.google.") || clean.startsWith("google."))) {
                        testDomainActionType = "SafeSearch VIP"
                        testDomainResult = "Intercepted by SafeSearch VIP -> 216.239.38.120 (Strict Google VIP)"
                    } else if (clean.contains("youtube.com")) {
                        testDomainActionType = "SafeSearch VIP"
                        testDomainResult = "Intercepted by YouTube VIP -> 216.239.38.119 (Restricted Mode VIP)"
                    } else {
                        val blockResult = DnsBlockerVpnService.blocklistMatcher.isBlocked(clean)
                        if (blockResult.isBlocked) {
                            testDomainActionType = "Blocked"
                            testDomainResult = "Blocked by ${blockResult.reason} (Sinkholed to 0.0.0.0)"
                        } else {
                            testDomainActionType = "Allowed"
                            testDomainResult = "Domain is clean -> Forwarded to Upstream DNS (1.1.1.1:53)"
                        }
                    }
                }
            )

            // Deliverable 1: Device Owner & Policy Card
            DeviceOwnerCard(
                status = status,
                onCopyCommand = { cmd -> copyToClipboard(context, cmd, "Command copied!") },
                onApplyLockdown = {
                    val ok = deviceAdminManager.applyAntiBypassLockdown()
                    Toast.makeText(
                        context,
                        if (ok) "Anti-bypass lockdown enforced!" else "Requires Device Owner via ADB",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshAll()
                },
                onReleaseLockdown = {
                    val ok = deviceAdminManager.releaseAntiBypassLockdown()
                    Toast.makeText(
                        context,
                        if (ok) "Lockdown released." else "Action not permitted.",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshAll()
                }
            )

            // Deliverable 1: RFC 6238 Accountability Partner Lock Card
            AccountabilityPartnerCard(
                partnerSecret = partnerSecret,
                totpInput = totpInput,
                totpResult = totpResult,
                onTotpInputChange = { totpInput = it },
                onGenerateSecret = {
                    val secret = tamperLockManager.setupNewPartnerSecret()
                    partnerSecret = secret
                    totpResult = null
                    Toast.makeText(context, "New Partner Secret Generated", Toast.LENGTH_SHORT).show()
                },
                onVerifyTotp = {
                    val valid = tamperLockManager.verifyPartnerTotp(totpInput)
                    totpResult = valid
                },
                onCopySecret = { sec -> copyToClipboard(context, sec, "Secret copied!") },
                onCopyUri = {
                    tamperLockManager.getPartnerTotpUri()?.let { uri ->
                        copyToClipboard(context, uri, "TOTP URI copied!")
                    }
                }
            )

            // Deliverable 1: Monotonic Anti-Tamper Cooldown Card
            CooldownTimerCard(
                isCooldownActive = isCooldownActive,
                cooldownRemainingMs = cooldownRemainingMs,
                onStartCooldown = { ms ->
                    tamperLockManager.startCooldown(ms)
                    isCooldownActive = true
                    cooldownRemainingMs = ms
                },
                onCancelCooldown = {
                    tamperLockManager.cancelCooldown()
                    isCooldownActive = false
                    cooldownRemainingMs = 0L
                    Toast.makeText(context, "Cooldown cancelled", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DeliverableBanner(isVpnRunning: Boolean, isAccessibilityActive: Boolean, rulesCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deliverable_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isVpnRunning && isAccessibilityActive) ShieldGreen else ShieldTeal)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Deliverables 1, 2 & 3 Active: Anti-Bypass, Sinkhole & Guard",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Browser Scraper • Settings Guard • SafeSearch VIP • $rulesCount Rules",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
            }
        }
    }
}

@Composable
fun AccessibilityGuardCard(
    isAccessibilityActive: Boolean,
    incognitoEnabled: Boolean,
    nsfwEnabled: Boolean,
    settingsGuardEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    onToggleNsfw: (Boolean) -> Unit,
    onToggleSettingsGuard: (Boolean) -> Unit,
    onTestOverlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("accessibility_guard_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Accessibility Guard",
                        tint = if (isAccessibilityActive) ShieldGreen else ShieldAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Browser Scraper & Settings Guard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAccessibilityActive) ShieldGreen.copy(alpha = 0.15f) else ShieldAmber.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isAccessibilityActive) "SERVICE ACTIVE" else "NOT ENABLED",
                        color = if (isAccessibilityActive) ShieldGreen else ShieldAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Scrapes address bar URLs in Chrome, Firefox & Brave. Detects Incognito mode, explicit search queries, and intercepts com.android.settings tampering.",
                fontSize = 12.sp,
                color = TextSecondaryDark,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Toggles
            GuardToggleRow(
                title = "Block Incognito & Private Windows",
                subtitle = "Executes BACK when Incognito tab is opened",
                checked = incognitoEnabled,
                onCheckedChange = onToggleIncognito,
                tag = "incognito_toggle"
            )

            GuardToggleRow(
                title = "Filter NSFW URLs & Search Queries",
                subtitle = "Scrapes address bar and search parameters",
                checked = nsfwEnabled,
                onCheckedChange = onToggleNsfw,
                tag = "nsfw_toggle"
            )

            GuardToggleRow(
                title = "Settings Anti-Tamper Guard",
                subtitle = "Blocks force-stop, uninstall, and VPN changes",
                checked = settingsGuardEnabled,
                onCheckedChange = onToggleSettingsGuard,
                tag = "settings_guard_toggle"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_accessibility_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Accessibility Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = onTestOverlay,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_overlay_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = ShieldAmber)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview Overlay", fontSize = 11.sp, color = TextPrimaryDark)
                }
            }
        }
    }
}

@Composable
fun GuardToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimaryDark)
            Text(subtitle, fontSize = 11.sp, color = TextSecondaryDark)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ShieldGreen,
                checkedTrackColor = ShieldGreenDark.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
fun BrowserScraperSimulatorCard(
    input: String,
    onInputChange: (String) -> Unit,
    resultText: String?,
    actionText: String?,
    onEvaluate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scraper_simulator_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Test Scraper",
                    tint = ShieldAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Browser Scraper & Guard Tester",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Enter a URL, search query parameter, or settings target to test immediate detection and overlay response.",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text("e.g. https://www.google.com/search?q=free+xxx", color = TextMutedDark, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scraper_test_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ShieldAmber,
                        unfocusedBorderColor = ShieldBorderDark,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                Button(
                    onClick = onEvaluate,
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("evaluate_scraper_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Inspect", fontWeight = FontWeight.Bold, color = ShieldNavyDark)
                }
            }

            if (resultText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isBlocked = actionText?.contains("Clean") != true
                val badgeColor = if (isBlocked) ShieldRed else ShieldGreen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = actionText ?: "Evaluation",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                            Text(
                                text = resultText,
                                fontSize = 12.sp,
                                color = TextPrimaryDark,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DnsSinkholeCard(
    isVpnRunning: Boolean,
    safeSearchEnabled: Boolean,
    ruleCount: Int,
    onToggleVpn: () -> Unit,
    onToggleSafeSearch: (Boolean) -> Unit,
    onLoadSampleHosts: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dns_sinkhole_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "DNS Sinkhole",
                        tint = if (isVpnRunning) ShieldGreen else ShieldTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local DNS Sinkhole (VpnService)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isVpnRunning) ShieldGreen.copy(alpha = 0.15f) else ShieldBorderDark.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = if (isVpnRunning) "VPN RUNNING" else "STOPPED",
                        color = if (isVpnRunning) ShieldGreen else TextSecondaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Captures port 53 DNS queries locally on TUN 10.254.1.1. Resolves clean traffic via Cloudflare 1.1.1.1 and Quad9 without routing TCP data through VPN.",
                fontSize = 12.sp,
                color = TextSecondaryDark,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Stat pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShieldNavyDark)
                        .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("Active Rules", fontSize = 11.sp, color = TextSecondaryDark)
                        Text("$ruleCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ShieldTeal)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShieldNavyDark)
                        .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("Upstream DNS", fontSize = 11.sp, color = TextSecondaryDark)
                        Text("1.1.1.1 / 9.9.9.9", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SafeSearch Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SafeSearch VIP Rewriting", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimaryDark)
                    Text("Forces www.google.com -> 216.239.38.120", fontSize = 11.sp, color = TextSecondaryDark)
                }
                Switch(
                    checked = safeSearchEnabled,
                    onCheckedChange = onToggleSafeSearch,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ShieldGreen,
                        checkedTrackColor = ShieldGreenDark.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("safesearch_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleVpn,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_vpn_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isVpnRunning) ShieldRed else ShieldGreenDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (isVpnRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isVpnRunning) "Stop Sinkhole" else "Start Sinkhole",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onLoadSampleHosts,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("load_hosts_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load StevenBlack", fontSize = 11.sp, color = TextPrimaryDark)
                }
            }
        }
    }
}

@Composable
fun DnsSimulatorCard(
    domainInput: String,
    onDomainInputChange: (String) -> Unit,
    resultText: String?,
    actionType: String?,
    onTestDomain: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dns_simulator_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Test DNS",
                    tint = ShieldTeal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DNS Parser & Rule Tester",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Simulates raw packet evaluation through SafeSearch VIP rewriting and StevenBlack host matching.",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = onDomainInputChange,
                    placeholder = { Text("e.g. www.google.com or doubleclick.net", color = TextMutedDark, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("domain_test_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ShieldTeal,
                        unfocusedBorderColor = ShieldBorderDark,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                Button(
                    onClick = onTestDomain,
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("simulate_dns_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Resolve", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            if (resultText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val badgeColor = when (actionType) {
                    "SafeSearch VIP" -> ShieldTeal
                    "Blocked" -> ShieldRed
                    else -> ShieldGreen
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (actionType) {
                                "SafeSearch VIP" -> Icons.Default.Security
                                "Blocked" -> Icons.Default.Block
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = actionType ?: "Result",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                            Text(
                                text = resultText,
                                fontSize = 12.sp,
                                color = TextPrimaryDark,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceOwnerCard(
    status: com.example.core.admin.DeviceOwnerStatus,
    onCopyCommand: (String) -> Unit,
    onApplyLockdown: () -> Unit,
    onReleaseLockdown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_owner_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Device Admin",
                        tint = if (status.isDeviceOwner) ShieldGreen else ShieldAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Owner & Anti-Bypass",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (status.isDeviceOwner) ShieldGreen.copy(alpha = 0.15f) else ShieldAmber.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (status.isDeviceOwner) "DEVICE OWNER" else "NOT PROVISIONED",
                        color = if (status.isDeviceOwner) ShieldGreen else ShieldAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status checks
            PolicyStatusRow(label = "Uninstall Blocked", active = status.isUninstallBlocked)
            PolicyStatusRow(label = "Safe Mode Blocked", active = status.isSafeModeBlocked)
            PolicyStatusRow(label = "Private DNS Tamper Blocked", active = status.isPrivateDnsBlocked)
            PolicyStatusRow(label = "Standard Device Admin Active", active = status.isAdminActive)

            Spacer(modifier = Modifier.height(14.dp))

            // Shell command box
            Text(
                text = "ADB Activation Command:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShieldNavyDark)
                    .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
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
                        onClick = { onCopyCommand(status.adbCommand) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("copy_adb_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Command",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApplyLockdown,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("apply_lockdown_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Policies", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = onReleaseLockdown,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("release_lockdown_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Release", fontSize = 12.sp, color = TextPrimaryDark)
                }
            }
        }
    }
}

@Composable
fun PolicyStatusRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = TextSecondaryDark)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (active) ShieldGreen else ShieldRed.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (active) "Active" else "Inactive",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) ShieldGreen else TextMutedDark
            )
        }
    }
}

@Composable
fun AccountabilityPartnerCard(
    partnerSecret: String?,
    totpInput: String,
    totpResult: Boolean?,
    onTotpInputChange: (String) -> Unit,
    onGenerateSecret: () -> Unit,
    onVerifyTotp: () -> Unit,
    onCopySecret: (String) -> Unit,
    onCopyUri: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("partner_lock_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "TOTP Lock",
                        tint = ShieldTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Accountability Partner Lock",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ShieldTeal.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "RFC 6238 TOTP",
                        color = ShieldTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Pairs with Google Authenticator, Aegis, or Bitwarden on your friend's phone. To unlock protection, you must obtain their 6-digit rolling code.",
                fontSize = 12.sp,
                color = TextSecondaryDark,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (partnerSecret != null) {
                Text(
                    text = "Friend's Shared Secret (Base32):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShieldNavyDark)
                        .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = partnerSecret,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ShieldGreen,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onCopySecret(partnerSecret) },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("copy_secret_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Secret",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyUri,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_uri_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Copy otpauth:// URI", fontSize = 11.sp, color = ShieldTeal)
                    }
                    OutlinedButton(
                        onClick = onGenerateSecret,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("regenerate_secret_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Regenerate Secret", fontSize = 11.sp, color = TextPrimaryDark)
                    }
                }
            } else {
                Button(
                    onClick = onGenerateSecret,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_partner_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Generate Partner Secret Key", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Verification Field
            Text(
                text = "Test 6-Digit TOTP Code:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = totpInput,
                    onValueChange = { if (it.length <= 6) onTotpInputChange(it) },
                    placeholder = { Text("000000", color = TextMutedDark) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("totp_input_field"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ShieldTeal,
                        unfocusedBorderColor = ShieldBorderDark,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                Button(
                    onClick = onVerifyTotp,
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("verify_totp_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Verify Code", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            if (totpResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (totpResult) ShieldGreen.copy(alpha = 0.15f)
                            else ShieldRed.copy(alpha = 0.15f)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (totpResult) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (totpResult) ShieldGreen else ShieldRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (totpResult) "Code verified! Protection authorization granted."
                        else "Invalid code. Please check your friend's authenticator app.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (totpResult) ShieldGreen else ShieldRed
                    )
                }
            }
        }
    }
}

@Composable
fun CooldownTimerCard(
    isCooldownActive: Boolean,
    cooldownRemainingMs: Long,
    onStartCooldown: (Long) -> Unit,
    onCancelCooldown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cooldown_timer_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassBottom,
                        contentDescription = "Cooldown Timer",
                        tint = if (isCooldownActive) ShieldAmber else ShieldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Anti-Tamper Cooldown Lock",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isCooldownActive) ShieldAmber.copy(alpha = 0.15f) else ShieldGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isCooldownActive) "COUNTING DOWN" else "STANDBY",
                        color = if (isCooldownActive) ShieldAmber else ShieldGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Protected by hardware monotonic clock (SystemClock.elapsedRealtime()). Forwarding the phone's clock or changing timezones will not accelerate or bypass this countdown.",
                fontSize = 12.sp,
                color = TextSecondaryDark,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (isCooldownActive) {
                // Large countdown display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ShieldNavyDark)
                        .border(1.dp, ShieldAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatTimeRemaining(cooldownRemainingMs),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = ShieldAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remaining until protection can be altered",
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onCancelCooldown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_cooldown_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel Cooldown (Admin Reset)", color = ShieldRed, fontSize = 12.sp)
                }
            } else {
                Text(
                    text = "Start Tamper Delay Test:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryDark
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onStartCooldown(60_000L) }, // 1 min test
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cooldown_1m_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldCardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ShieldBorderDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("1 Min Test", fontSize = 12.sp, color = TextPrimaryDark)
                    }

                    Button(
                        onClick = { onStartCooldown(300_000L) }, // 5 min
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cooldown_5m_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldCardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ShieldBorderDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("5 Minutes", fontSize = 12.sp, color = TextPrimaryDark)
                    }

                    Button(
                        onClick = { onStartCooldown(3_600_000L) }, // 1 hr
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cooldown_1h_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("1 Hour", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatTimeRemaining(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun copyToClipboard(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("ShieldGuard", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
