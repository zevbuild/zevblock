package com.zevbuild.zevblock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService
import kotlin.math.PI
import kotlin.math.sin

/**
 * Simple, clear, and detailed real-time Network & DNS Telemetry Card:
 * 1. Displays live Download (↓) and Upload (↑) speeds + Today's Data Usage (synced with Notification Panel).
 * 2. Explains simply how the 3-Tier Local DNS Sinkhole works under the hood.
 * 3. Shows real-time Blocked, SafeSearch, and Allowed link decisions with filter pills and instant test trigger.
 */
@Composable
fun NetworkTelemetryWaveCard(
    isVpnRunning: Boolean,
    totalInspectedQueries: Long = 0L,
    dnsLogs: List<DnsLogEntry> = emptyList(),
    modifier: Modifier = Modifier
) {
    val dlSpeedBps by DnsBlockerVpnService.downloadSpeedBps.collectAsState()
    val ulSpeedBps by DnsBlockerVpnService.uploadSpeedBps.collectAsState()
    val todayDlBytes by DnsBlockerVpnService.todayDownloadBytes.collectAsState()
    val todayUlBytes by DnsBlockerVpnService.todayUploadBytes.collectAsState()
    val todayAppDlBytes by DnsBlockerVpnService.todayAppDownloadBytes.collectAsState()
    val todayAppUlBytes by DnsBlockerVpnService.todayAppUploadBytes.collectAsState()

    val totalQueries by DnsBlockerVpnService.totalQueriesCount.collectAsState()
    val blockedQueries by DnsBlockerVpnService.blockedQueriesCount.collectAsState()
    val safeSearchQueries by DnsBlockerVpnService.safeSearchQueriesCount.collectAsState()
    val allowedQueries by DnsBlockerVpnService.allowedQueriesCount.collectAsState()
    val latencyMs by DnsBlockerVpnService.lastUpstreamLatencyMs.collectAsState()
    val serviceLogs by DnsBlockerVpnService.recentLogs.collectAsState()

    val activeLogs = if (dnsLogs.isNotEmpty()) dnsLogs else serviceLogs

    var showHowDnsWorks by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Blocked, 2 = SafeSearch, 3 = Allowed

    val path1 = remember { Path() }
    val pathFill = remember { Path() }
    val path2 = remember { Path() }

    // Only run continuous wave animation when VPN is actively running to save battery & startup CPU
    val wavePhase = if (isVpnRunning) {
        val infiniteTransition = rememberInfiniteTransition(label = "telemetry_wave")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 3200,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
        phase
    } else {
        0f
    }
    val waveAmplitudeFactor = 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("network_telemetry_wave_card")
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ShieldBorderDark, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. HEADER: Title & Live Notification Sync Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadarPulseDot(
                        active = isVpnRunning,
                        activeColor = ShieldGreen,
                        inactiveColor = TextMutedDark,
                        size = 8.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LIVE TELEMETRY & DNS STREAM",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = if (isVpnRunning) ShieldGreen else TextSecondaryDark
                        )
                        Text(
                            text = if (isVpnRunning) "Showing in Notification Panel • Local DNS Active"
                            else "Start VPN to see live speed in Notification Panel",
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                // Latency Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isVpnRunning) ShieldGreen.copy(alpha = 0.15f) else ShieldNavyDark)
                        .border(
                            1.dp,
                            if (isVpnRunning) ShieldGreen.copy(alpha = 0.4f) else ShieldBorderDark,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "DNS Latency",
                            tint = if (isVpnRunning) ShieldGreen else TextMutedDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVpnRunning) "${latencyMs}ms" else "Idle",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVpnRunning) ShieldGreen else TextMutedDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. LIVE DOWNLOAD & UPLOAD SPEED BAR (Synced with Notification Panel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Download Speed & Today's Downloaded Data
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ShieldNavyDark)
                        .border(1.dp, ShieldGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Download Speed",
                                tint = ShieldGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DOWNLOAD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondaryDark
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DnsBlockerVpnService.formatSpeed(dlSpeedBps),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ShieldGreen
                        )
                        Text(
                            text = "Today: ${DnsBlockerVpnService.formatBytes(todayDlBytes)}",
                            fontSize = 10.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                // Upload Speed & Today's Uploaded Data
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ShieldNavyDark)
                        .border(1.dp, ShieldTeal.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Upload Speed",
                                tint = ShieldTeal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UPLOAD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondaryDark
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DnsBlockerVpnService.formatSpeed(ulSpeedBps),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ShieldTeal
                        )
                        Text(
                            text = "Today: ${DnsBlockerVpnService.formatBytes(todayUlBytes)}",
                            fontSize = 10.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // App DNS Data & Notification Indicator Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShieldNavyDark.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = ShieldTeal,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Notification Panel: Live ↓/↑ Speed Active",
                        fontSize = 10.sp,
                        color = TextSecondaryDark
                    )
                }
                Text(
                    text = "App DNS Today: ${DnsBlockerVpnService.formatBytes(todayAppDlBytes + todayAppUlBytes)}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ShieldTeal
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. WAVE CANVAS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ShieldNavyDark)
                    .border(1.dp, ShieldBorderDark.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val midY = height * 0.55f

                    val baseAmp = if (isVpnRunning) (height * 0.22f) * waveAmplitudeFactor else 3f

                    path1.reset()
                    pathFill.reset()
                    path2.reset()

                    val pointsCount = 40
                    val dx = width / (pointsCount - 1)

                    path1.moveTo(0f, midY)
                    pathFill.moveTo(0f, height)
                    pathFill.lineTo(0f, midY)

                    for (i in 0 until pointsCount) {
                        val x = i * dx
                        val normalizedX = x / width
                        val wave1 = sin((normalizedX * 4 * PI + wavePhase).toDouble()).toFloat()
                        val wave2 = sin((normalizedX * 8 * PI + wavePhase * 1.5).toDouble()).toFloat() * 0.35f
                        val y = midY + (wave1 + wave2) * baseAmp

                        path1.lineTo(x, y)
                        pathFill.lineTo(x, y)
                    }

                    pathFill.lineTo(width, height)
                    pathFill.close()

                    path2.moveTo(0f, midY)
                    for (i in 0 until pointsCount) {
                        val x = i * dx
                        val normalizedX = x / width
                        val waveSec = sin((normalizedX * 3.5 * PI - wavePhase * 0.8).toDouble()).toFloat()
                        val y = midY + waveSec * (baseAmp * 0.6f)
                        path2.lineTo(x, y)
                    }

                    if (isVpnRunning) {
                        drawPath(
                            path = pathFill,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ShieldTeal.copy(alpha = 0.25f),
                                    ShieldGreen.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                        drawPath(
                            path = path2,
                            color = ShieldGreen.copy(alpha = 0.35f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawPath(
                            path = path1,
                            brush = Brush.horizontalGradient(
                                colors = listOf(ShieldTeal, ShieldGreen, ShieldTeal)
                            ),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    } else {
                        drawPath(
                            path = path1,
                            color = ShieldBorderDark,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                // Clear, simple labels on top of wave canvas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DNS Tunnel: 10.254.1.1:53", fontSize = 9.sp, color = TextSecondaryDark, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "Upstream: Cloudflare Family (1.1.1.3)",
                        fontSize = 9.sp,
                        color = if (isVpnRunning) ShieldTeal else TextMutedDark,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. SIMPLE 4-COUNTER DNS DECISION SUMMARY ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SimpleDnsCounterBox(
                    modifier = Modifier.weight(1f),
                    label = "Checked",
                    count = totalQueries.coerceAtLeast(totalInspectedQueries),
                    color = TextPrimaryDark
                )
                SimpleDnsCounterBox(
                    modifier = Modifier.weight(1f),
                    label = "Blocked",
                    count = blockedQueries,
                    color = ShieldRed
                )
                SimpleDnsCounterBox(
                    modifier = Modifier.weight(1f),
                    label = "SafeSearch",
                    count = safeSearchQueries,
                    color = ShieldTeal
                )
                SimpleDnsCounterBox(
                    modifier = Modifier.weight(1f),
                    label = "Allowed",
                    count = allowedQueries,
                    color = ShieldGreen
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. SIMPLE EXPLANATION: HOW DNS & LINK FILTERING WORKS
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showHowDnsWorks = !showHowDnsWorks },
                color = ShieldNavyDark,
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = ShieldTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How DNS Blocks & Allows Links (Simple Guide)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                        }
                        Icon(
                            imageVector = if (showHowDnsWorks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Guide",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = showHowDnsWorks,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DnsStepExplainRow(
                                stepBadge = "STEP 1",
                                badgeColor = ShieldAmber,
                                title = "Link Lookup Caught On-Device (10.254.1.1)",
                                description = "When you open any link, ZevBlock checks only the website name (Port 53 DNS). Your normal videos, chats, and downloads never slow down."
                            )
                            DnsStepExplainRow(
                                stepBadge = "TIER 1",
                                badgeColor = ShieldTeal,
                                title = "Search Engines -> Forced SafeSearch VIP",
                                description = "Google, YouTube, Bing & DuckDuckGo are redirected to strict SafeSearch IPs (e.g. 216.239.38.120) so explicit results are hidden."
                            )
                            DnsStepExplainRow(
                                stepBadge = "TIER 2",
                                badgeColor = ShieldRed,
                                title = "Adult & Custom Sites -> BLOCKED (0.0.0.0)",
                                description = "150+ adult sites, video CDNs, and your Custom Rules are sent to 0.0.0.0 (dead end) in under 1 millisecond."
                            )
                            DnsStepExplainRow(
                                stepBadge = "TIER 3",
                                badgeColor = ShieldGreen,
                                title = "Safe Websites -> ALLOWED (1.1.1.3 Family DNS)",
                                description = "Clean links are resolved by Cloudflare Family (1.1.1.3) & CleanBrowsing so normal browsing works smoothly."
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. LIVE STREAM OF BLOCKED & ALLOWED LINKS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE LINK DECISIONS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryDark
                )

                // Quick 1-Tap Demo Test Button so user can see Blocked/SafeSearch/Allowed in action immediately
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            DnsBlockerVpnService.simulateDnsPipelineLookup("wikipedia.org")
                            DnsBlockerVpnService.simulateDnsPipelineLookup("www.google.com")
                            DnsBlockerVpnService.simulateDnsPipelineLookup("pornhub.com")
                        }
                        .testTag("test_live_dns_flow_button"),
                    color = ShieldTeal.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldTeal.copy(alpha = 0.5f)))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = ShieldTeal,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Test 3 Sample Links",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ShieldTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Pills: All | Blocked | SafeSearch | Allowed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf("All", "Blocked", "SafeSearch", "Allowed")
                filters.forEachIndexed { index, title ->
                    val isSelected = selectedFilter == index
                    val activeColor = when (index) {
                        1 -> ShieldRed
                        2 -> ShieldTeal
                        3 -> ShieldGreen
                        else -> ShieldGreen
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedFilter = index },
                        color = if (isSelected) activeColor.copy(alpha = 0.18f) else ShieldNavyDark,
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isSelected) activeColor else ShieldBorderDark
                            )
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeColor else TextSecondaryDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filteredLogs = remember(activeLogs, selectedFilter) {
                when (selectedFilter) {
                    1 -> activeLogs.filter { it.isBlocked }
                    2 -> activeLogs.filter { it.isSafeSearchRewritten }
                    3 -> activeLogs.filter { !it.isBlocked && !it.isSafeSearchRewritten }
                    else -> activeLogs
                }.take(6)
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ShieldNavyDark)
                        .border(1.dp, ShieldBorderDark, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No links in this view yet. Browse any website or tap 'Test 3 Sample Links' above to watch how DNS blocks and allows links!",
                        fontSize = 11.sp,
                        color = TextSecondaryDark
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filteredLogs.forEach { entry ->
                        LiveDnsDecisionRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleDnsCounterBox(
    modifier: Modifier,
    label: String,
    count: Long,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ShieldNavyDark)
            .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedRollingCounter(
                value = count,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextSecondaryDark,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DnsStepExplainRow(
    stepBadge: String,
    badgeColor: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ShieldSurfaceDark.copy(alpha = 0.7f))
            .padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = badgeColor.copy(alpha = 0.18f),
            shape = RoundedCornerShape(6.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(badgeColor.copy(alpha = 0.5f)))
        ) {
            Text(
                text = stepBadge,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 10.sp,
                color = TextSecondaryDark,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun LiveDnsDecisionRow(entry: DnsLogEntry) {
    val badgeText: String
    val badgeColor: Color
    val howItWorked: String

    when {
        entry.isBlocked -> {
            badgeText = "BLOCKED"
            badgeColor = ShieldRed
            howItWorked = "${entry.reason} (Sent to 0.0.0.0 sinkhole)"
        }
        entry.isSafeSearchRewritten -> {
            badgeText = "SAFESEARCH"
            badgeColor = ShieldTeal
            howItWorked = entry.reason
        }
        else -> {
            badgeText = "ALLOWED"
            badgeColor = ShieldGreen
            howItWorked = entry.reason
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ShieldNavyDark)
            .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.domain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimaryDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = howItWorked,
                fontSize = 10.sp,
                color = TextSecondaryDark
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = badgeColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(6.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(badgeColor))
        ) {
            Text(
                text = badgeText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

