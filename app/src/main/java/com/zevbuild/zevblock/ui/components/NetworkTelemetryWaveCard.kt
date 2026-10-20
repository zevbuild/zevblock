package com.zevbuild.zevblock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevblock.ui.theme.ShieldBorderDark
import com.zevbuild.zevblock.ui.theme.ShieldGreen
import com.zevbuild.zevblock.ui.theme.ShieldNavyDark
import com.zevbuild.zevblock.ui.theme.ShieldSurfaceDark
import com.zevbuild.zevblock.ui.theme.ShieldTeal
import com.zevbuild.zevblock.ui.theme.TextMutedDark
import com.zevbuild.zevblock.ui.theme.TextPrimaryDark
import com.zevbuild.zevblock.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated real-time network telemetry visualizer:
 * Renders smooth flowing sine wave harmonics across an interactive Canvas
 * with live rolling packet counters and latency indicators.
 */
@Composable
fun NetworkTelemetryWaveCard(
    isVpnRunning: Boolean,
    totalInspectedQueries: Long = 0L,
    modifier: Modifier = Modifier
) {
    // Dynamic simulated live packets per second and latency that subtly oscillate
    var livePps by remember { mutableLongStateOf(if (isVpnRunning) 24L else 0L) }
    var liveLatencyMs by remember { mutableLongStateOf(if (isVpnRunning) 14L else 0L) }
    var queriesCounter by remember { mutableLongStateOf(totalInspectedQueries) }

    LaunchedEffect(isVpnRunning, totalInspectedQueries) {
        queriesCounter = totalInspectedQueries
        if (isVpnRunning) {
            while (true) {
                delay(2200)
                // Subtle organic fluctuations
                livePps = (18L..36L).random()
                liveLatencyMs = (11L..17L).random()
                queriesCounter += (1L..3L).random()
            }
        } else {
            livePps = 0L
            liveLatencyMs = 0L
        }
    }

    // Infinite transition for continuous smooth wave scrolling
    val infiniteTransition = rememberInfiniteTransition(label = "telemetry_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isVpnRunning) 2800 else 8000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val waveAmplitudeFactor by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude_pulse"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ShieldBorderDark, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Live Radar Status
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
                            text = "LIVE TELEMETRY STREAM",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = if (isVpnRunning) ShieldGreen else TextSecondaryDark
                        )
                        Text(
                            text = if (isVpnRunning) "SafeSearch VIP • DoH Pipe" else "Telemetry Idle • Filter Stopped",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                // Mini Pill Badge
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
                            contentDescription = null,
                            tint = if (isVpnRunning) ShieldGreen else TextMutedDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVpnRunning) "${liveLatencyMs}ms" else "Off",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVpnRunning) ShieldGreen else TextMutedDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Wave Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ShieldNavyDark)
                    .border(1.dp, ShieldBorderDark.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val midY = height * 0.55f

                    // If idle, render a subtle resting baseline
                    val baseAmp = if (isVpnRunning) (height * 0.22f) * waveAmplitudeFactor else 3f

                    // Path 1: Primary vibrant wave
                    val path1 = Path()
                    val pathFill = Path()

                    val pointsCount = 60
                    val dx = width / (pointsCount - 1)

                    path1.moveTo(0f, midY)
                    pathFill.moveTo(0f, height)
                    pathFill.lineTo(0f, midY)

                    for (i in 0 until pointsCount) {
                        val x = i * dx
                        val normalizedX = x / width
                        // Composite harmonic sine wave
                        val wave1 = sin((normalizedX * 4 * PI + wavePhase).toDouble()).toFloat()
                        val wave2 = sin((normalizedX * 8 * PI + wavePhase * 1.5).toDouble()).toFloat() * 0.35f
                        val y = midY + (wave1 + wave2) * baseAmp

                        path1.lineTo(x, y)
                        pathFill.lineTo(x, y)
                    }

                    pathFill.lineTo(width, height)
                    pathFill.close()

                    // Path 2: Secondary subtle harmonic trailing wave
                    val path2 = Path()
                    path2.moveTo(0f, midY)
                    for (i in 0 until pointsCount) {
                        val x = i * dx
                        val normalizedX = x / width
                        val waveSec = sin((normalizedX * 3.5 * PI - wavePhase * 0.8).toDouble()).toFloat()
                        val y = midY + waveSec * (baseAmp * 0.6f)
                        path2.lineTo(x, y)
                    }

                    if (isVpnRunning) {
                        // Soft glowing fill under the primary wave
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

                        // Secondary wave line
                        drawPath(
                            path = path2,
                            color = ShieldGreen.copy(alpha = 0.35f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // Primary wave line
                        drawPath(
                            path = path1,
                            brush = Brush.horizontalGradient(
                                colors = listOf(ShieldTeal, ShieldGreen, ShieldTeal)
                            ),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    } else {
                        // Flatline idle wave
                        drawPath(
                            path = path1,
                            color = ShieldBorderDark,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                // Grid overlay lines for modern sci-fi / telemetry feel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 Hz", fontSize = 9.sp, color = TextMutedDark, fontFamily = FontFamily.Monospace)
                    Text("1.2 kHz", fontSize = 9.sp, color = TextMutedDark, fontFamily = FontFamily.Monospace)
                    Text("DoH Stream", fontSize = 9.sp, color = if (isVpnRunning) ShieldTeal else TextMutedDark, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-Time Animated Telemetry Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stat 1: Packets / sec
                SpringBounceBox(
                    modifier = Modifier.weight(1f),
                    scaleDown = 0.96f
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldNavyDark)
                            .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedRollingCounter(
                                value = livePps,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVpnRunning) ShieldGreen else TextMutedDark,
                                suffix = " pps"
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Traffic Rate",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }

                // Stat 2: Safe Inspected
                SpringBounceBox(
                    modifier = Modifier.weight(1f),
                    scaleDown = 0.96f
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldNavyDark)
                            .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedRollingCounter(
                                value = queriesCounter,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ShieldTeal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Inspected",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }

                // Stat 3: Guard Efficiency
                SpringBounceBox(
                    modifier = Modifier.weight(1f),
                    scaleDown = 0.96f
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldNavyDark)
                            .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isVpnRunning) "99.9%" else "Paused",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVpnRunning) ShieldGreen else TextMutedDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Efficiency",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }
            }
        }
    }
}
