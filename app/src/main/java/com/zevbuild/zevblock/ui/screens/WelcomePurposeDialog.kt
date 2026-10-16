package com.zevbuild.zevblock.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zevbuild.zevblock.ui.theme.ShieldBorderDark
import com.zevbuild.zevblock.ui.theme.ShieldCardDark
import com.zevbuild.zevblock.ui.theme.ShieldGreen
import com.zevbuild.zevblock.ui.theme.ShieldGreenDark
import com.zevbuild.zevblock.ui.theme.ShieldNavyDark
import com.zevbuild.zevblock.ui.theme.ShieldSurfaceDark
import com.zevbuild.zevblock.ui.theme.ShieldTeal
import com.zevbuild.zevblock.ui.theme.TextMutedDark
import com.zevbuild.zevblock.ui.theme.TextPrimaryDark
import com.zevbuild.zevblock.ui.theme.TextSecondaryDark

/**
 * WelcomePurposeDialog:
 * High-impact onboarding dialog shown upon first launch.
 * Clearly, concisely, and effectively explains that ZevBlock is designed to protect
 * the user from pornography and adult content everywhere on their device.
 */
@Composable
fun WelcomePurposeDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* Force explicit user acknowledgement */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, ShieldTeal.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Shield Icon with Glow Ring
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(ShieldNavyDark)
                        .border(2.dp, ShieldTeal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Protection Shield",
                        tint = ShieldTeal,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Welcome to ZevBlock",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimaryDark,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Core Purpose Message (Short, punchy & highly effective)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ShieldTeal.copy(alpha = 0.12f))
                        .border(1.dp, ShieldTeal.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This app is designed to keep you safe from pornography and adult content.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShieldTeal,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Feature Highlights Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ShieldNavyDark)
                        .border(1.dp, ShieldBorderDark, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PurposeFeatureRow(
                        icon = Icons.Default.Block,
                        iconTint = Color(0xFFFF5252),
                        title = "Zero-Bypass Adult Filter",
                        description = "Blocks explicit sites, search queries, and incognito sessions across all apps & browsers."
                    )

                    PurposeFeatureRow(
                        icon = Icons.Default.Message,
                        iconTint = ShieldTeal,
                        title = "Chat & Messaging Safeguard",
                        description = "Monitors for vulgar content in chat apps (WhatsApp, Telegram, etc.) with non-intrusive alerts."
                    )

                    PurposeFeatureRow(
                        icon = Icons.Default.Lock,
                        iconTint = ShieldGreen,
                        title = "Tamper-Proof Commitment",
                        description = "Prevents impulsive bypass with Partner TOTP authorization and 24-hour hardware cooldowns."
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reassuring privacy note
                Text(
                    text = "🔒 100% On-Device Privacy • No Data Collected • Zero Logging",
                    fontSize = 11.sp,
                    color = TextMutedDark,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Acknowledgement Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ShieldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I Understand — Keep Me Safe",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PurposeFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ShieldSurfaceDark)
                .border(1.dp, ShieldBorderDark, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = TextSecondaryDark,
                lineHeight = 15.sp
            )
        }
    }
}
