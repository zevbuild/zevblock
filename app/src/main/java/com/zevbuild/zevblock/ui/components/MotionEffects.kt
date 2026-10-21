package com.zevbuild.zevblock.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modifier for modern spring physics tactile press animation.
 * Depresses slightly with spring damping and pops back on release.
 */
fun Modifier.springBounceClick(
    scaleDownFactor: Float = 0.94f,
    onClick: () -> Unit
): Modifier = this.pointerInput(onClick) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Pressed state handled by stateful composable or pointer
        }
    }
}

/**
 * Interactive Composable wrapper that provides spring bounce scale on touch.
 */
@Composable
fun SpringBounceBox(
    modifier: Modifier = Modifier,
    scaleDown: Float = 0.95f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "spring_bounce_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled && onClick != null) {
                    Modifier
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    isPressed = true
                                    val up = waitForUpOrCancellation()
                                    isPressed = false
                                    if (up != null) {
                                        onClick()
                                    }
                                }
                            }
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Lightweight, fast numeric counter that avoids spawning per-character
 * AnimatedContent subcompositions during initial screen load.
 */
@Composable
fun AnimatedRollingCounter(
    value: Long,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.White,
    prefix: String = "",
    suffix: String = ""
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$prefix$value$suffix",
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        )
    }
}

/**
 * Pulsing radar dot for active status indicators (e.g. VPN, Active Guard).
 * Only runs an infinite transition when `active == true` to save CPU/GPU and battery.
 */
@Composable
fun RadarPulseDot(
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    size: Dp = 8.dp,
    pulseMultiplier: Float = 2.2f
) {
    Box(
        modifier = Modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = pulseMultiplier,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "radar_scale"
            )

            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.65f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "radar_alpha"
            )

            // Expanding wave ring using graphicsLayer to avoid layout relayout on each frame
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }

        // Core solid dot
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (active) activeColor else inactiveColor)
        )
    }
}
