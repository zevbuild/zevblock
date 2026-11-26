package com.zevbuild.zevblock

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zevbuild.zevblock.accessibility.ShieldAccessibilityService
import com.zevbuild.zevblock.core.accessibility.NsfwContentDetector
import com.zevbuild.zevblock.core.admin.DeviceAdminManager
import com.zevbuild.zevblock.core.diagnostics.CrashGuardManager
import com.zevbuild.zevblock.core.prefs.ZevBlockPreferences
import com.zevbuild.zevblock.core.rules.CustomShieldRuleManager
import com.zevbuild.zevblock.core.security.TamperLockManager
import com.zevbuild.zevblock.core.stats.ProtectionStatsManager
import com.zevbuild.zevblock.ui.components.LiveDnsTelemetryModal
import com.zevbuild.zevblock.ui.components.PartnerUnlockGateDialog
import com.zevbuild.zevblock.ui.components.ZevStatusBadge
import com.zevbuild.zevblock.ui.overlay.SecurityWarningOverlayActivity
import com.zevbuild.zevblock.ui.screens.CustomRulesScreen
import com.zevbuild.zevblock.ui.screens.DashboardScreen
import com.zevbuild.zevblock.ui.screens.GuidedSetupSheet
import com.zevbuild.zevblock.ui.screens.PartnerSetupScreen
import com.zevbuild.zevblock.ui.screens.SettingsScreen
import com.zevbuild.zevblock.ui.screens.WelcomePurposeDialog
import com.zevbuild.zevblock.ui.screens.checkBatteryOptimizationExempt
import com.zevbuild.zevblock.ui.screens.checkNotificationPermissionGranted
import com.zevbuild.zevblock.ui.screens.requestStandardDeviceAdmin
import com.zevbuild.zevblock.ui.theme.ZevBlockTheme
import com.zevbuild.zevblock.ui.theme.ZevTheme
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var tamperLockManager: TamperLockManager
    private lateinit var prefs: ZevBlockPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        val launchStartRealtime = SystemClock.elapsedRealtime()
        CrashGuardManager.install(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        deviceAdminManager = DeviceAdminManager(this)
        tamperLockManager = TamperLockManager(this)
        prefs = ZevBlockPreferences(this)

        setContent {
            val themeMode by prefs.themeMode.collectAsState()
            ZevBlockTheme(themeMode = themeMode) {
                ShieldGuardApp(
                    deviceAdminManager = deviceAdminManager,
                    tamperLockManager = tamperLockManager,
                    prefs = prefs,
                    launchStartRealtime = launchStartRealtime
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldGuardApp(
    deviceAdminManager: DeviceAdminManager,
    tamperLockManager: TamperLockManager,
    prefs: ZevBlockPreferences = ZevBlockPreferences(LocalContext.current),
    launchStartRealtime: Long = SystemClock.elapsedRealtime()
) {
    val context = LocalContext.current
    val colors = ZevTheme.colors
    val lifecycleOwner = LocalLifecycleOwner.current
    val appPrefs = remember { context.getSharedPreferences("zevblock_app_prefs", Context.MODE_PRIVATE) }

    var showPurposeDisclaimer by remember {
        mutableStateOf(!appPrefs.getBoolean("has_seen_purpose_disclaimer", false))
    }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Shield, 1: Rules, 2: Partner, 3: Settings
    var showLiveDnsSheet by remember { mutableStateOf(false) }
    var showGuidedSetup by remember { mutableStateOf(false) }

    // Partner gate request: (title, description, cooldownPurpose, onAuthorized)
    var partnerGateRequest by remember {
        mutableStateOf<Triple<String, String, () -> Unit>?>(null)
    }
    var partnerGatePurpose by remember { mutableStateOf<String?>(null) }

    var status by remember { mutableStateOf(deviceAdminManager.getStatus()) }
    var isBatteryExempt by remember { mutableStateOf(checkBatteryOptimizationExempt(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermissionGranted(context)) }
    var hasPartnerConfigured by remember { mutableStateOf(tamperLockManager.getPartnerSecret() != null) }

    var cooldownRemainingMs by remember { mutableLongStateOf(tamperLockManager.getRemainingCooldownMs()) }
    var isCooldownActive by remember { mutableStateOf(tamperLockManager.isCooldownActive()) }
    var isBypassActive by remember { mutableStateOf(tamperLockManager.isBypassActive()) }
    var bypassRemainingMs by remember { mutableLongStateOf(tamperLockManager.getRemainingBypassMs()) }

    val isVpnRunning by DnsBlockerVpnService.isRunning.collectAsState()
    val isAccessibilityActive by ShieldAccessibilityService.isServiceActive.collectAsState()
    val safeSearchEnabled by prefs.safeSearchEnabled.collectAsState()
    val recentDnsLogs by DnsBlockerVpnService.recentLogs.collectAsState()
    val recentAccessibilityLogs by ShieldAccessibilityService.recentEvents.collectAsState()
    var ruleCount by remember { mutableIntStateOf(DnsBlockerVpnService.blocklistMatcher.getRuleCount()) }

    val customRuleManager = remember {
        CustomShieldRuleManager(
            context = context,
            tamperLockManager = tamperLockManager,
            blocklistMatcher = DnsBlockerVpnService.blocklistMatcher,
            nsfwContentDetector = NsfwContentDetector.shared
        )
    }

    val statsManager = remember {
        ProtectionStatsManager(
            context = context,
            tamperLockManager = tamperLockManager
        )
    }

    fun refreshAll() {
        try {
            status = deviceAdminManager.getStatus()
            isBatteryExempt = checkBatteryOptimizationExempt(context)
            hasNotificationPermission = checkNotificationPermissionGranted(context)
            hasPartnerConfigured = tamperLockManager.getPartnerSecret() != null
            cooldownRemainingMs = tamperLockManager.getRemainingCooldownMs()
            isCooldownActive = tamperLockManager.isCooldownActive()
            isBypassActive = tamperLockManager.isBypassActive()
            bypassRemainingMs = tamperLockManager.getRemainingBypassMs()
            ruleCount = DnsBlockerVpnService.blocklistMatcher.getRuleCount()
            if (DnsBlockerVpnService.isRunning.value) {
                prefs.setVpnExpectedActive(true)
            }
            if (ShieldAccessibilityService.isServiceActive.value) {
                prefs.setAccessibilityExpectedActive(true)
            }
            statsManager.refreshStats()
            customRuleManager.refreshPendingRemovals()
        } catch (t: Throwable) {
            CrashGuardManager.recordHandledException("MainActivity.refreshAll", t, autoRecovered = true)
        }
    }

    // Track expected service states so unexpected OS kills trigger recovery alerts
    LaunchedEffect(isVpnRunning, isAccessibilityActive) {
        if (isVpnRunning) prefs.setVpnExpectedActive(true)
        if (isAccessibilityActive) prefs.setAccessibilityExpectedActive(true)
    }

    // Automatically re-sync permissions & OS state when returning from Android System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        DnsBlockerVpnService.statsManager = statsManager
        ShieldAccessibilityService.statsManager = statsManager
        ShieldAccessibilityService.customRuleManager = customRuleManager
        prefs.applyToServices()
        CrashGuardManager.recordStartupComplete(launchStartRealtime)

        withContext(Dispatchers.Default) {
            runCatching {
                NsfwContentDetector.shared.isBuiltInKeyword("porn")
                statsManager.refreshStats()
                customRuleManager.refreshPendingRemovals()
            }.onFailure { t ->
                CrashGuardManager.recordHandledException("MainActivity.warmup", t, autoRecovered = true)
            }
        }
        ruleCount = DnsBlockerVpnService.blocklistMatcher.getRuleCount()
    }

    // Notification permission launcher (Android 13+)
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || checkNotificationPermissionGranted(context)
    }

    // VPN prepare launcher
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            prefs.setVpnExpectedActive(true)
            DnsBlockerVpnService.start(context)
            Toast.makeText(context, "ZevBlock DNS Shield started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVpnInternal() {
        try {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPrepareLauncher.launch(prepareIntent)
            } else {
                prefs.setVpnExpectedActive(true)
                DnsBlockerVpnService.start(context)
            }
        } catch (t: Throwable) {
            CrashGuardManager.recordHandledException("MainActivity.startVpn", t, autoRecovered = true)
            Toast.makeText(context, "Could not start VPN: ${t.localizedMessage ?: "error"}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopVpnAuthorized() {
        prefs.setVpnExpectedActive(false)
        DnsBlockerVpnService.stop(context)
        Toast.makeText(context, "DNS Sinkhole paused", Toast.LENGTH_SHORT).show()
    }

    fun toggleVpn() {
        if (isVpnRunning) {
            if (hasPartnerConfigured) {
                partnerGatePurpose = TamperLockManager.COOLDOWN_PURPOSE_PAUSE_VPN
                partnerGateRequest = Triple(
                    "Pause DNS Sinkhole",
                    "Partner verification is required before pausing network DNS filtering.",
                    { stopVpnAuthorized() }
                )
            } else {
                stopVpnAuthorized()
            }
        } else {
            startVpnInternal()
        }
    }

    LaunchedEffect(isCooldownActive) {
        while (isCooldownActive) {
            cooldownRemainingMs = tamperLockManager.getRemainingCooldownMs()
            isCooldownActive = tamperLockManager.isCooldownActive()
            delay(1000L)
        }
    }

    LaunchedEffect(isBypassActive) {
        while (isBypassActive) {
            bypassRemainingMs = tamperLockManager.getRemainingBypassMs()
            isBypassActive = tamperLockManager.isBypassActive()
            delay(1000L)
        }
    }

    if (showPurposeDisclaimer) {
        WelcomePurposeDialog(
            onDismiss = {
                appPrefs.edit().putBoolean("has_seen_purpose_disclaimer", true).apply()
                showPurposeDisclaimer = false
                if ((!isVpnRunning || !isAccessibilityActive) && !prefs.hasCompletedOnboarding.value) {
                    showGuidedSetup = true
                }
            }
        )
    }

    if (showLiveDnsSheet) {
        LiveDnsTelemetryModal(
            isVpnRunning = isVpnRunning,
            dnsLogs = recentDnsLogs,
            onDismiss = { showLiveDnsSheet = false }
        )
    }

    if (showGuidedSetup) {
        GuidedSetupSheet(
            deviceOwnerStatus = status,
            isVpnRunning = isVpnRunning,
            isAccessibilityActive = isAccessibilityActive,
            isBatteryExempt = isBatteryExempt,
            hasNotificationPermission = hasNotificationPermission,
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onLaunchVpnPrepare = { startVpnInternal() },
            onActivateDeviceAdmin = { requestStandardDeviceAdmin(context, deviceAdminManager) },
            onNavigateToPartnerLock = { selectedTab = 2 },
            onViewPurposeMission = { showPurposeDisclaimer = true },
            onFinish = {
                prefs.setOnboardingCompleted(true)
                showGuidedSetup = false
                refreshAll()
            },
            onDismiss = {
                showGuidedSetup = false
                refreshAll()
            }
        )
    }

    partnerGateRequest?.let { (title, description, onAuthorized) ->
        PartnerUnlockGateDialog(
            title = title,
            description = description,
            tamperLockManager = tamperLockManager,
            cooldownPurpose = partnerGatePurpose,
            cooldownDurationMs = TamperLockManager.COOLDOWN_1_HOUR_MS,
            onDismiss = {
                partnerGateRequest = null
                partnerGatePurpose = null
            },
            onAuthorized = {
                onAuthorized()
                partnerGateRequest = null
                partnerGatePurpose = null
                refreshAll()
            },
            onCooldownStarted = {
                isCooldownActive = tamperLockManager.isCooldownActive()
                cooldownRemainingMs = tamperLockManager.getRemainingCooldownMs()
                Toast.makeText(context, "Timed unlock delay started.", Toast.LENGTH_LONG).show()
            }
        )
    }

    val isRecoveryNeeded = (prefs.isVpnExpectedActive() && !isVpnRunning) ||
        (prefs.isAccessibilityExpectedActive() && !isAccessibilityActive)

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
                                color = colors.textPrimary
                            )
                            Text(
                                text = when (selectedTab) {
                                    0 -> "Shield Overview & Streak"
                                    1 -> "Rules & Category Lookup"
                                    2 -> "Accountability Partner Vault"
                                    else -> "Health, Guards & Privacy"
                                },
                                fontSize = 11.sp,
                                color = colors.teal
                            )
                        }
                    }
                },
                actions = {
                    androidx.compose.material3.Surface(
                        onClick = { showLiveDnsSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        color = (if (isVpnRunning) colors.green else colors.textMuted).copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            (if (isVpnRunning) colors.green else colors.border).copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .testTag("topbar_live_dns_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.zevbuild.zevblock.ui.components.RadarPulseDot(
                                active = isVpnRunning,
                                activeColor = colors.green,
                                inactiveColor = colors.textMuted,
                                size = 6.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live DNS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVpnRunning) colors.green else colors.textSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = { refreshAll() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("app_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = colors.surface,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Shield") },
                    label = { Text("Shield", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_tab_shield"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.greenBtnFg,
                        indicatorColor = colors.greenBtn,
                        selectedTextColor = colors.green,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Rules") },
                    label = { Text("Rules", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_tab_rules"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.tealBtnFg,
                        indicatorColor = colors.teal,
                        selectedTextColor = colors.teal,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.QrCode2, contentDescription = "Partner") },
                    label = { Text("Partner", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_tab_partner"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.tealBtnFg,
                        indicatorColor = colors.teal,
                        selectedTextColor = colors.teal,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_tab_settings"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.tealBtnFg,
                        indicatorColor = colors.teal,
                        selectedTextColor = colors.teal,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    )
                )
            }
        },
        containerColor = colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialOffsetX = { fullWidth -> direction * (fullWidth / 3) }
                    ) + fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )).togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            targetOffsetX = { fullWidth -> -direction * (fullWidth / 3) }
                        ) + fadeOut(
                            animationSpec = tween(120)
                        )
                    )
                },
                label = "tab_screen_transition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    0 -> DashboardScreen(
                        deviceOwnerStatus = status,
                        isVpnRunning = isVpnRunning,
                        isAccessibilityActive = isAccessibilityActive,
                        safeSearchEnabled = safeSearchEnabled,
                        hasPartnerConfigured = hasPartnerConfigured,
                        isCooldownActive = isCooldownActive,
                        isBypassActive = isBypassActive,
                        bypassRemainingMs = bypassRemainingMs,
                        isRecoveryNeeded = isRecoveryNeeded,
                        ruleCount = ruleCount,
                        dnsLogs = recentDnsLogs,
                        accessibilityLogs = recentAccessibilityLogs,
                        statsManager = statsManager,
                        onToggleVpn = { toggleVpn() },
                        onApplyLockdown = {
                            val ok = deviceAdminManager.applyAntiBypassLockdown()
                            Toast.makeText(context, if (ok) "Hardware lockdown enforced" else "Requires Device Owner", Toast.LENGTH_SHORT).show()
                            refreshAll()
                        },
                        onReleaseLockdown = {
                            if (hasPartnerConfigured) {
                                partnerGatePurpose = TamperLockManager.COOLDOWN_PURPOSE_RELEASE_LOCKDOWN
                                partnerGateRequest = Triple(
                                    "Release Hardware Lockdown",
                                    "Partner verification is required to lift Device Owner restrictions.",
                                    {
                                        val ok = deviceAdminManager.releaseAntiBypassLockdown()
                                        Toast.makeText(context, if (ok) "Lockdown released" else "Not permitted", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                val ok = deviceAdminManager.releaseAntiBypassLockdown()
                                Toast.makeText(context, if (ok) "Lockdown released" else "Not permitted", Toast.LENGTH_SHORT).show()
                                refreshAll()
                            }
                        },
                        onNavigateToSetup = { showGuidedSetup = true },
                        onNavigateToPartner = { selectedTab = 2 },
                        onNavigateToSettings = { selectedTab = 3 },
                        onOpenLiveDnsSheet = { showLiveDnsSheet = true },
                        onCancelBypass = {
                            tamperLockManager.cancelTemporaryBypass()
                            isBypassActive = false
                            bypassRemainingMs = 0L
                        }
                    )

                    1 -> CustomRulesScreen(
                        ruleManager = customRuleManager,
                        tamperLockManager = tamperLockManager,
                        hasPartnerConfigured = hasPartnerConfigured,
                        onNavigateToPartnerSetup = { selectedTab = 2 }
                    )

                    2 -> PartnerSetupScreen(
                        tamperLockManager = tamperLockManager,
                        isCooldownActive = isCooldownActive,
                        cooldownRemainingMs = cooldownRemainingMs,
                        isBypassActive = isBypassActive,
                        bypassRemainingMs = bypassRemainingMs,
                        onStartCooldown = { ms, purpose ->
                            tamperLockManager.setCooldownPurpose(purpose)
                            tamperLockManager.startCooldown(ms)
                            isCooldownActive = true
                            cooldownRemainingMs = ms
                        },
                        onCancelCooldown = {
                            tamperLockManager.cancelCooldown()
                            isCooldownActive = false
                            cooldownRemainingMs = 0L
                        },
                        onCompleteCooldownUnpair = {
                            refreshAll()
                        },
                        onStartBypass = { ms ->
                            isBypassActive = true
                            bypassRemainingMs = ms
                        },
                        onCancelBypass = {
                            tamperLockManager.cancelTemporaryBypass()
                            isBypassActive = false
                            bypassRemainingMs = 0L
                        },
                        onPartnerStateChanged = {
                            refreshAll()
                        }
                    )

                    else -> SettingsScreen(
                        prefs = prefs,
                        tamperLockManager = tamperLockManager,
                        statsManager = statsManager,
                        deviceOwnerStatus = status,
                        isVpnRunning = isVpnRunning,
                        isAccessibilityActive = isAccessibilityActive,
                        isBatteryExempt = isBatteryExempt,
                        hasNotificationPermission = hasNotificationPermission,
                        hasPartnerConfigured = hasPartnerConfigured,
                        onOpenGuidedSetup = { showGuidedSetup = true },
                        onTestOverlay = {
                            SecurityWarningOverlayActivity.show(
                                context,
                                alertType = "Pause & Stay Focused",
                                message = "This preview demonstrates the supportive intervention overlay triggered when explicit content or settings tampering is intercepted.",
                                detail = "Triggered from Developer & QA Lab",
                                category = "DEVELOPER PREVIEW",
                                streakDays = statsManager.streakDays.value
                            )
                        },
                        onViewPurposeMission = { showPurposeDisclaimer = true },
                        onStartTestCooldown = {
                            tamperLockManager.setCooldownPurpose(TamperLockManager.COOLDOWN_PURPOSE_UNLOCK)
                            tamperLockManager.startCooldown(60_000L)
                            isCooldownActive = true
                            cooldownRemainingMs = 60_000L
                            Toast.makeText(context, "1-minute test cooldown started in Partner tab", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
