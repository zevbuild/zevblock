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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Tune
import com.example.accessibility.ShieldAccessibilityService
import com.example.core.accessibility.NsfwContentDetector
import com.example.core.admin.DeviceAdminManager
import com.example.core.rules.CustomShieldRuleManager
import com.example.core.security.TamperLockManager
import com.example.core.stats.ProtectionStatsManager
import com.example.ui.overlay.SecurityWarningOverlayActivity
import com.example.ui.screens.CustomRulesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PartnerSetupScreen
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
                ShieldGuardApp(
                    deviceAdminManager = deviceAdminManager,
                    tamperLockManager = tamperLockManager
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldGuardApp(
    deviceAdminManager: DeviceAdminManager,
    tamperLockManager: TamperLockManager
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var status by remember { mutableStateOf(deviceAdminManager.getStatus()) }
    var cooldownRemainingMs by remember { mutableLongStateOf(tamperLockManager.getRemainingCooldownMs()) }
    var isCooldownActive by remember { mutableStateOf(tamperLockManager.isCooldownActive()) }

    var isBypassActive by remember { mutableStateOf(tamperLockManager.isBypassActive()) }
    var bypassRemainingMs by remember { mutableLongStateOf(tamperLockManager.getRemainingBypassMs()) }

    val isVpnRunning by DnsBlockerVpnService.isRunning.collectAsState()
    val isAccessibilityActive by ShieldAccessibilityService.isServiceActive.collectAsState()
    val recentDnsLogs by DnsBlockerVpnService.recentLogs.collectAsState()
    val recentAccessibilityLogs by ShieldAccessibilityService.recentEvents.collectAsState()
    var ruleCount by remember { mutableStateOf(DnsBlockerVpnService.blocklistMatcher.getRuleCount()) }

    val customRuleManager = remember {
        CustomShieldRuleManager(
            context = context,
            tamperLockManager = tamperLockManager,
            blocklistMatcher = DnsBlockerVpnService.blocklistMatcher,
            nsfwContentDetector = NsfwContentDetector()
        )
    }

    val statsManager = remember {
        ProtectionStatsManager(
            context = context,
            tamperLockManager = tamperLockManager
        )
    }

    LaunchedEffect(Unit) {
        DnsBlockerVpnService.statsManager = statsManager
        ShieldAccessibilityService.statsManager = statsManager
        ShieldAccessibilityService.customRuleManager = customRuleManager
    }

    // VPN launcher
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

    fun launchVpn() {
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
    }

    // Cooldown clock loop
    LaunchedEffect(isCooldownActive) {
        while (isCooldownActive) {
            cooldownRemainingMs = tamperLockManager.getRemainingCooldownMs()
            isCooldownActive = tamperLockManager.isCooldownActive()
            delay(1000L)
        }
    }

    // Bypass clock loop
    LaunchedEffect(isBypassActive) {
        while (isBypassActive) {
            bypassRemainingMs = tamperLockManager.getRemainingBypassMs()
            isBypassActive = tamperLockManager.isBypassActive()
            delay(1000L)
        }
    }

    fun refreshAll() {
        status = deviceAdminManager.getStatus()
        cooldownRemainingMs = tamperLockManager.getRemainingCooldownMs()
        isCooldownActive = tamperLockManager.isCooldownActive()
        isBypassActive = tamperLockManager.isBypassActive()
        bypassRemainingMs = tamperLockManager.getRemainingBypassMs()
        ruleCount = DnsBlockerVpnService.blocklistMatcher.getRuleCount()
        statsManager.refreshStats()
        customRuleManager.refreshPendingRemovals()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_zevblock_logo),
                            contentDescription = "ZevBlock Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ZevBlock",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = when (selectedTab) {
                                    0 -> "System Dashboard & Streak"
                                    1 -> "Custom Rules & Keywords"
                                    2 -> "Accountability Partner Lock"
                                    3 -> "Permissions & Setup Guide"
                                    else -> "Interactive Rule Testers"
                                },
                                fontSize = 11.sp,
                                color = ShieldTeal
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshAll() },
                        modifier = Modifier.testTag("app_refresh_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondaryDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShieldSurfaceDark)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ShieldSurfaceDark,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Shield, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ShieldNavyDark,
                        indicatorColor = ShieldGreen,
                        selectedTextColor = ShieldGreen,
                        unselectedIconColor = TextSecondaryDark,
                        unselectedTextColor = TextSecondaryDark
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.Tune, contentDescription = "Rules") },
                    label = { Text("Rules", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ShieldNavyDark,
                        indicatorColor = ShieldTeal,
                        selectedTextColor = ShieldTeal,
                        unselectedIconColor = TextSecondaryDark,
                        unselectedTextColor = TextSecondaryDark
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.QrCode2, contentDescription = "Partner") },
                    label = { Text("Partner", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ShieldNavyDark,
                        indicatorColor = ShieldTeal,
                        selectedTextColor = ShieldTeal,
                        unselectedIconColor = TextSecondaryDark,
                        unselectedTextColor = TextSecondaryDark
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(imageVector = Icons.Default.Visibility, contentDescription = "Setup") },
                    label = { Text("Setup", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ShieldNavyDark,
                        indicatorColor = ShieldTeal,
                        selectedTextColor = ShieldTeal,
                        unselectedIconColor = TextSecondaryDark,
                        unselectedTextColor = TextSecondaryDark
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(imageVector = Icons.Default.Build, contentDescription = "Tools") },
                    label = { Text("Tools", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ShieldNavyDark,
                        indicatorColor = ShieldAmber,
                        selectedTextColor = ShieldAmber,
                        unselectedIconColor = TextSecondaryDark,
                        unselectedTextColor = TextSecondaryDark
                    )
                )
            }
        },
        containerColor = ShieldNavyDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    deviceOwnerStatus = status,
                    isVpnRunning = isVpnRunning,
                    isAccessibilityActive = isAccessibilityActive,
                    hasPartnerConfigured = tamperLockManager.getPartnerSecret() != null,
                    isCooldownActive = isCooldownActive,
                    isBypassActive = isBypassActive,
                    bypassRemainingMs = bypassRemainingMs,
                    ruleCount = ruleCount,
                    dnsLogs = recentDnsLogs,
                    accessibilityLogs = recentAccessibilityLogs,
                    statsManager = statsManager,
                    onToggleVpn = { launchVpn() },
                    onApplyLockdown = {
                        val ok = deviceAdminManager.applyAntiBypassLockdown()
                        Toast.makeText(context, if (ok) "Lockdown Enforced" else "Requires Device Owner", Toast.LENGTH_SHORT).show()
                        refreshAll()
                    },
                    onReleaseLockdown = {
                        val ok = deviceAdminManager.releaseAntiBypassLockdown()
                        Toast.makeText(context, if (ok) "Lockdown Released" else "Not permitted", Toast.LENGTH_SHORT).show()
                        refreshAll()
                    },
                    onNavigateToSetup = { selectedTab = 3 },
                    onCancelBypass = {
                        tamperLockManager.cancelTemporaryBypass()
                        isBypassActive = false
                        bypassRemainingMs = 0L
                    }
                )

                1 -> CustomRulesScreen(
                    ruleManager = customRuleManager,
                    hasPartnerConfigured = tamperLockManager.getPartnerSecret() != null,
                    onNavigateToPartnerSetup = { selectedTab = 2 }
                )

                2 -> PartnerSetupScreen(
                    tamperLockManager = tamperLockManager,
                    isCooldownActive = isCooldownActive,
                    cooldownRemainingMs = cooldownRemainingMs,
                    isBypassActive = isBypassActive,
                    bypassRemainingMs = bypassRemainingMs,
                    onStartCooldown = { ms ->
                        tamperLockManager.startCooldown(ms)
                        isCooldownActive = true
                        cooldownRemainingMs = ms
                    },
                    onCancelCooldown = {
                        tamperLockManager.cancelCooldown()
                        isCooldownActive = false
                        cooldownRemainingMs = 0L
                    },
                    onStartBypass = { ms ->
                        isBypassActive = true
                        bypassRemainingMs = ms
                    },
                    onCancelBypass = {
                        tamperLockManager.cancelTemporaryBypass()
                        isBypassActive = false
                        bypassRemainingMs = 0L
                    }
                )

                3 -> OnboardingScreen(
                    deviceOwnerStatus = status,
                    isVpnRunning = isVpnRunning,
                    isAccessibilityActive = isAccessibilityActive,
                    onLaunchVpnPrepare = { launchVpn() },
                    onNavigateToPartnerLock = { selectedTab = 2 }
                )

                4 -> ToolsSimulatorScreen(
                    onTestOverlay = {
                        SecurityWarningOverlayActivity.show(
                            context,
                            alertType = "Security Test Alert",
                            message = "This simulated alert demonstrates the Jetpack Compose warning overlay triggered when an anti-bypass violation occurs.",
                            detail = "Triggered from Developer Tools"
                        )
                    }
                )
            }
        }
    }
}

/**
 * Developer & Simulation tools for validating DNS sinkhole and browser scraper evaluations.
 */
@Composable
fun ToolsSimulatorScreen(onTestOverlay: () -> Unit) {
    val scrollState = rememberScrollState()

    var domainInput by remember { mutableStateOf("www.google.com") }
    var domainResult by remember { mutableStateOf<String?>(null) }
    var scraperInput by remember { mutableStateOf("https://www.google.com/search?q=free+xxx+video") }
    var scraperResult by remember { mutableStateOf<String?>(null) }

    val nsfwDetector = remember { NsfwContentDetector() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overlay Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Compose Warning Overlay Preview", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tests the translucent fullscreen warning overlay triggered on Incognito or settings tampering.", fontSize = 12.sp, color = TextSecondaryDark)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onTestOverlay,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Trigger Compose Warning Overlay", fontWeight = FontWeight.Bold, color = ShieldNavyDark)
                }
            }
        }

        // DNS Resolver Tester
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("DNS Sinkhole & SafeSearch Tester", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Evaluates domain against SafeSearch VIP rewriter and StevenBlack blocklist.", fontSize = 12.sp, color = TextSecondaryDark)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            val clean = domainInput.trim()
                            if (clean == "www.google.com" || clean == "google.com") {
                                domainResult = "Intercepted by SafeSearch VIP -> 216.239.38.120"
                            } else {
                                val res = DnsBlockerVpnService.blocklistMatcher.isBlocked(clean)
                                domainResult = if (res.isBlocked) "Blocked: ${res.reason} -> 0.0.0.0" else "Allowed -> Upstream DNS"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Test", fontWeight = FontWeight.Bold)
                    }
                }

                if (domainResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(domainResult!!, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ShieldTeal)
                }
            }
        }

        // Browser Scraper Tester
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Browser Address Bar Scraper Tester", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tests URL parameters, adult terms, and leetspeak bypass detection.", fontSize = 12.sp, color = TextSecondaryDark)
                Spacer(modifier = Modifier.height(12.dp))

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
                            val candidate = scraperInput.trim()
                            val nsfw = nsfwDetector.analyze(candidate)
                            scraperResult = if (nsfw.isNsfw) "BLOCKED: Matched '${nsfw.matchedTerm}' (${nsfw.category})" else "CLEAN: Allowed"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Inspect", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (scraperResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(scraperResult!!, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ShieldAmber)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
