package com.example.ui.screens

import android.content.Context
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
import com.example.R
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.accessibility.AccessibilityLogEntry
import com.example.core.admin.DeviceAdminManager
import com.example.core.admin.DeviceOwnerStatus
import com.example.core.dns.DnsLogEntry
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
    ruleCount: Int,
    dnsLogs: List<DnsLogEntry>,
    accessibilityLogs: List<AccessibilityLogEntry>,
    onToggleVpn: () -> Unit,
    onApplyLockdown: () -> Unit,
    onReleaseLockdown: () -> Unit,
    onNavigateToSetup: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isFullDefenseActive = isVpnRunning && isAccessibilityActive && deviceOwnerStatus.isDeviceOwner

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
