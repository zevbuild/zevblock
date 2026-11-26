package com.zevbuild.zevblock.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zevbuild.zevblock.core.dns.DnsLogEntry
import com.zevbuild.zevblock.ui.theme.ZevTheme
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService
import kotlin.math.PI
import kotlin.math.sin

/**
 * Interactive full-detail Live DNS Telemetry bottom sheet container.
 */
@Composable
fun LiveDnsTelemetryModal(
    isVpnRunning: Boolean,
    dnsLogs: List<DnsLogEntry>,
    onDismiss: () -> Unit
) {
    val colors = ZevTheme.colors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.background)
                .border(
                    BorderStroke(1.dp, colors.border),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live DNS Telemetry",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Ephemeral session log in RAM • Never written to disk",
                            fontSize = 12.sp,
                            color = colors.textMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_live_dns_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Live DNS Telemetry",
                            tint = colors.textPrimary
                        )
                    }
                }

                NetworkTelemetryWaveCard(
                    isVpnRunning = isVpnRunning,
                    dnsLogs = dnsLogs
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Clean, truthful real-time Network & DNS Telemetry Card.
 * Reflects actual measured upstream latency and real query totals without synthetic floors.
 */
@Composable
fun NetworkTelemetryWaveCard(
    isVpnRunning: Boolean,
    totalInspectedQueries: Long = 0L,
    dnsLogs: List<DnsLogEntry> = emptyList(),
    modifier: Modifier = Modifier
) {
    val colors = ZevTheme.colors
    val dlSpeedBps by DnsBlockerVpnService.downloadSpeedBps.collectAsState()
    val ulSpeedBps by DnsBlockerVpnService.uploadSpeedBps.collectAsState()
    val todayDlBytes by DnsBlockerVpnService.todayDownloadBytes.collectAsState()
    val todayUlBytes by DnsBlockerVpnService.todayUploadBytes.collectAsState()

    val totalQueries by DnsBlockerVpnService.totalQueriesCount.collectAsState()
    val blockedQueries by DnsBlockerVpnService.blockedQueriesCount.collectAsState()
    val safeSearchQueries by DnsBlockerVpnService.safeSearchQueriesCount.collectAsState()
    val allowedQueries by DnsBlockerVpnService.allowedQueriesCount.collectAsState()
    val latencyMs by DnsBlockerVpnService.lastUpstreamLatencyMs.collectAsState()
    val activeUpstream by DnsBlockerVpnService.activeUpstreamLabel.collectAsState()
    val isLogPaused by DnsBlockerVpnService.isSessionLogPaused.collectAsState()
    val serviceLogs by DnsBlockerVpnService.recentLogs.collectAsState()

    val activeLogs = if (dnsLogs.isNotEmpty()) dnsLogs else serviceLogs

    var showHowDnsWorks by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Blocked, 2 = SafeSearch, 3 = Allowed

    val path1 = remember { Path() }
    val pathFill = remember { Path() }
    val path2 = remember { Path() }

    val wavePhase = if (isVpnRunning) {
        val infiniteTransition = rememberInfiniteTransition(label = "telemetry_wave")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
        phase
    } else {
        0f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("network_telemetry_wave_card")
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. Header & Real Upstream Latency Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadarPulseDot(
                        active = isVpnRunning,
                        activeColor = colors.green,
                        inactiveColor = colors.textMuted,
                        size = 8.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LOCAL DNS TELEMETRY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.8.sp,
                            color = if (isVpnRunning) colors.green else colors.textSecondary
                        )
                        Text(
                            text = if (isVpnRunning) activeUpstream else "Start VPN to capture port 53 queries",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                val latencyLabel = when {
                    !isVpnRunning -> "Idle"
                    latencyMs > 0L -> "${latencyMs}ms"
                    else -> "Ready"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isVpnRunning) colors.green.copy(alpha = 0.14f) else colors.background,
                    border = BorderStroke(
                        1.dp,
                        if (isVpnRunning) colors.green.copy(alpha = 0.4f) else colors.border
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Upstream DNS latency",
                            tint = if (isVpnRunning) colors.green else colors.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = latencyLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVpnRunning) colors.green else colors.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Live Speed Boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.green.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Download Speed",
                                tint = colors.green,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DOWNLOAD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DnsBlockerVpnService.formatSpeed(dlSpeedBps),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.green
                        )
                        Text(
                            text = "Today: ${DnsBlockerVpnService.formatBytes(todayDlBytes)}",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.teal.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Upload Speed",
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UPLOAD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DnsBlockerVpnService.formatSpeed(ulSpeedBps),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.teal
                        )
                        Text(
                            text = "Today: ${DnsBlockerVpnService.formatBytes(todayUlBytes)}",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Wave Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.background)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val midY = height * 0.58f
                    val baseAmp = if (isVpnRunning) height * 0.2f else 2.5f

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
                        val nx = x / width
                        val w1 = sin((nx * 4 * PI + wavePhase).toDouble()).toFloat()
                        val w2 = sin((nx * 8 * PI + wavePhase * 1.5).toDouble()).toFloat() * 0.35f
                        val y = midY + (w1 + w2) * baseAmp
                        path1.lineTo(x, y)
                        pathFill.lineTo(x, y)
                    }

                    pathFill.lineTo(width, height)
                    pathFill.close()

                    path2.moveTo(0f, midY)
                    for (i in 0 until pointsCount) {
                        val x = i * dx
                        val nx = x / width
                        val ws = sin((nx * 3.5 * PI - wavePhase * 0.8).toDouble()).toFloat()
                        val y = midY + ws * (baseAmp * 0.6f)
                        path2.lineTo(x, y)
                    }

                    if (isVpnRunning) {
                        drawPath(
                            path = pathFill,
                            brush = Brush.verticalGradient(
                                listOf(colors.teal.copy(alpha = 0.22f), colors.green.copy(alpha = 0.04f), Color.Transparent)
                            )
                        )
                        drawPath(path = path2, color = colors.green.copy(alpha = 0.35f), style = Stroke(width = 1.5.dp.toPx()))
                        drawPath(
                            path = path1,
                            brush = Brush.horizontalGradient(listOf(colors.teal, colors.green, colors.teal)),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    } else {
                        drawPath(path = path1, color = colors.border, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TUN: 10.254.1.1:53",
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isVpnRunning) "Split DNS Active" else "Inactive",
                        fontSize = 11.sp,
                        color = if (isVpnRunning) colors.teal else colors.textMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Honest 4-Counter Summary Row (no synthetic floors)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SimpleDnsCounterBox(
                    modifier = Modifier.weight(1f),
                    label = "Checked",
                    count = totalQueries.coerceAtLeast(totalInspectedQueries),
                    color = colors.textPrimary
                )
                SimpleDnsCounterBox(
                    modifier = Modifier.weight(1f),
                    label = "Blocked",
                    count = blockedQueries,
                    color = colors.red
                )
                SimpleDnsCounterBox(
                    modifier = Modifier.weight(1f),
                    label = "SafeSearch",
                    count = safeSearchQueries,
                    color = colors.teal
                )
                SimpleDnsCounterBox(
                    modifier = Modifier.weight(1f),
                    label = "Allowed",
                    count = allowedQueries,
                    color = colors.green
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Expandable 3-Tier Guide
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showHowDnsWorks = !showHowDnsWorks },
                color = colors.background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.border)
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
                                tint = colors.teal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How Local DNS Filtering Works",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                        Icon(
                            imageVector = if (showHowDnsWorks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Guide",
                            tint = colors.textSecondary,
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
                                stepBadge = "PORT 53",
                                badgeColor = colors.amberSoftText,
                                title = "Split Routing On-Device (10.254.1.1)",
                                description = "Only port 53 DNS lookups enter the TUN interface. App media, video, and downloads bypass TUN directly."
                            )
                            DnsStepExplainRow(
                                stepBadge = "TIER 1",
                                badgeColor = colors.teal,
                                title = "Search Engines -> SafeSearch VIP",
                                description = "Google, YouTube, Bing, DuckDuckGo, Yahoo, Brave, and Qwant rewrite directly to strict SafeSearch endpoints."
                            )
                            DnsStepExplainRow(
                                stepBadge = "TIER 2",
                                badgeColor = colors.red,
                                title = "Adult & Custom Sites -> 0.0.0.0 Sinkhole",
                                description = "Adult websites, video CDNs, and custom blocked domains resolve locally to 0.0.0.0 in under a millisecond."
                            )
                            DnsStepExplainRow(
                                stepBadge = "TIER 3",
                                badgeColor = colors.green,
                                title = "Clean Links -> Filtered Family DNS",
                                description = "Allowed lookups forward via Cloudflare Family (1.1.1.3), CleanBrowsing, or AdGuard Family."
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Session Log Controls (RAM-only privacy controls + quick sample test)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SESSION LOG (RAM ONLY)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { DnsBlockerVpnService.setSessionLogPaused(!isLogPaused) },
                        color = if (isLogPaused) colors.amber.copy(alpha = 0.16f) else colors.card,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isLogPaused) colors.amber else colors.border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLogPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isLogPaused) "Resume Log" else "Pause Log",
                                tint = if (isLogPaused) colors.amberSoftText else colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isLogPaused) "Resume" else "Pause",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLogPaused) colors.amberSoftText else colors.textSecondary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { DnsBlockerVpnService.clearSessionLogs() },
                        color = colors.card,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Session Log",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Sample Trigger
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        DnsBlockerVpnService.simulateDnsPipelineLookup("wikipedia.org")
                        DnsBlockerVpnService.simulateDnsPipelineLookup("www.google.com")
                        DnsBlockerVpnService.simulateDnsPipelineLookup("pornhub.com")
                    }
                    .testTag("test_live_dns_flow_button"),
                color = colors.teal.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Simulate 3 Sample Link Lookups",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf("All", "Blocked", "SafeSearch", "Allowed")
                filters.forEachIndexed { index, title ->
                    val isSelected = selectedFilter == index
                    val activeColor = when (index) {
                        1 -> colors.red
                        2 -> colors.teal
                        else -> colors.green
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedFilter = index },
                        color = if (isSelected) activeColor.copy(alpha = 0.16f) else colors.background,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSelected) activeColor else colors.border)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeColor else colors.textSecondary
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
                }.take(10)
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLogPaused) "Session log is paused. Tap Resume above to inspect new DNS lookups."
                        else "No links captured in this view yet. Browse any app or simulate sample lookups above.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
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
    val colors = ZevTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
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
                fontSize = 11.sp,
                color = colors.textSecondary,
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
    val colors = ZevTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card)
            .padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = badgeColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
        ) {
            Text(
                text = stepBadge,
                fontSize = 11.sp,
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun LiveDnsDecisionRow(entry: DnsLogEntry) {
    val colors = ZevTheme.colors
    val badgeText: String
    val badgeColor: Color
    val howItWorked: String

    when {
        entry.isBlocked -> {
            badgeText = "BLOCKED"
            badgeColor = colors.red
            howItWorked = entry.reason
        }
        entry.isSafeSearchRewritten -> {
            badgeText = "SAFESEARCH"
            badgeColor = colors.teal
            howItWorked = entry.reason
        }
        else -> {
            badgeText = "ALLOWED"
            badgeColor = colors.green
            howItWorked = entry.reason
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.background)
            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
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
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = howItWorked,
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        ZevStatusBadge(label = badgeText, color = badgeColor)
    }
}
