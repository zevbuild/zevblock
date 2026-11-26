package com.zevbuild.zevblock.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevblock.core.security.TamperLockManager
import com.zevbuild.zevblock.ui.theme.ZevTheme
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Accessible status chip using semantic ZevTheme palette tokens (>= 11sp).
 */
@Composable
fun ZevStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    pulse: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (showDot) {
                RadarPulseDot(
                    active = pulse,
                    activeColor = color,
                    inactiveColor = color,
                    size = 6.dp
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Clean 6-digit segmented numeric OTP box input backed by a single BasicTextField
 * so pasting, deleting, keyboard entry, and UI tests behave seamlessly.
 */
@Composable
fun SegmentedOtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    inputTestTag: String = "segmented_otp_input",
    accentColor: Color = ZevTheme.colors.teal
) {
    val colors = ZevTheme.colors
    val digitsOnly = value.filter { it.isDigit() }.take(6)

    BasicTextField(
        value = digitsOnly,
        onValueChange = { raw ->
            onValueChange(raw.filter { it.isDigit() }.take(6))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(accentColor),
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(inputTestTag),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                for (index in 0 until 6) {
                    val char = digitsOnly.getOrNull(index)
                    val isFocusedSlot = digitsOnly.length == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.background)
                            .border(
                                width = if (isFocusedSlot || char != null) 1.5.dp else 1.dp,
                                color = when {
                                    isFocusedSlot -> accentColor
                                    char != null -> colors.green.copy(alpha = 0.65f)
                                    else -> colors.border
                                },
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char?.toString() ?: "•",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (char != null) colors.textPrimary else colors.textMuted
                        )
                    }
                }
            }
        }
    )
}

/**
 * Unified Partner Authorization modal gating sensitive security reductions when paired.
 * Enforces TOTP validation, surfaces TamperLockManager lockout countdowns accurately,
 * and supports optional hardware cooldown requests.
 */
@Composable
fun PartnerUnlockGateDialog(
    title: String,
    description: String,
    tamperLockManager: TamperLockManager,
    cooldownPurpose: String? = null,
    cooldownDurationMs: Long = TamperLockManager.COOLDOWN_24_HOURS_MS,
    onDismiss: () -> Unit,
    onAuthorized: () -> Unit,
    onCooldownStarted: (() -> Unit)? = null
) {
    val colors = ZevTheme.colors
    var otpInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutRemainingMs by remember { mutableLongStateOf(tamperLockManager.getLockoutRemainingMs()) }
    var isCooldownFinishedForPurpose by remember {
        mutableStateOf(
            cooldownPurpose != null &&
                tamperLockManager.isCooldownFinished() &&
                tamperLockManager.getCooldownPurpose() == cooldownPurpose
        )
    }

    LaunchedEffect(lockoutRemainingMs) {
        while (lockoutRemainingMs > 0L) {
            delay(1000L)
            lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )

                if (isCooldownFinishedForPurpose) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.green.copy(alpha = 0.14f)),
                        border = BorderStroke(1.dp, colors.green.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Timed Delay Complete",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green
                            )
                            Text(
                                text = "Your mandatory waiting period expired. You may confirm this action once.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                            Button(
                                onClick = {
                                    if (tamperLockManager.consumeCompletedCooldown()) {
                                        onAuthorized()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Confirm Unlocked Action", fontWeight = FontWeight.Bold, color = colors.greenBtnFg)
                            }
                        }
                    }
                }

                if (lockoutRemainingMs > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.red.copy(alpha = 0.14f))
                            .border(1.dp, colors.red.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = colors.red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Too many invalid attempts. Locked for ${formatDurationClock(lockoutRemainingMs)}.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.red
                            )
                        }
                    }
                }

                SegmentedOtpInput(
                    value = otpInput,
                    onValueChange = {
                        otpInput = it
                        errorMessage = null
                    },
                    inputTestTag = "partner_gate_otp_input"
                )

                if (errorMessage != null && lockoutRemainingMs <= 0L) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = colors.red,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (cooldownPurpose != null && onCooldownStarted != null && !isCooldownFinishedForPurpose) {
                    OutlinedButton(
                        onClick = {
                            tamperLockManager.setCooldownPurpose(cooldownPurpose)
                            tamperLockManager.startCooldown(cooldownDurationMs)
                            onCooldownStarted()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = colors.amberSoftText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Or Start ${formatCooldownBadge(cooldownDurationMs)} Delay",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.amberSoftText
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ok = tamperLockManager.verifyPartnerTotp(otpInput)
                    if (ok) {
                        onAuthorized()
                    } else {
                        lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
                        errorMessage = if (lockoutRemainingMs > 0L) {
                            "Verification throttled for ${formatDurationClock(lockoutRemainingMs)}."
                        } else {
                            "Code rejected or already used. Ask partner for next rolling code."
                        }
                    }
                },
                enabled = otpInput.length == 6 && lockoutRemainingMs <= 0L,
                colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("partner_gate_confirm_button")
            ) {
                Text("Verify Code", fontWeight = FontWeight.Bold, color = colors.greenBtnFg)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textMuted)
            }
        }
    )
}

fun formatDurationClock(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val min = (totalSec % 3600L) / 60L
    val sec = totalSec % 60L
    return if (hrs > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, min, sec)
    } else {
        String.format(Locale.US, "%02d:%02d", min, sec)
    }
}

private fun formatCooldownBadge(ms: Long): String {
    val hours = ms / 3_600_000L
    return if (hours >= 1L) "${hours}h" else "${(ms / 60_000L).coerceAtLeast(1L)}m"
}
