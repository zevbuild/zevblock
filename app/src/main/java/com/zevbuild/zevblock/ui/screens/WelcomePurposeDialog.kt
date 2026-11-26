package com.zevbuild.zevblock.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
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
import com.zevbuild.zevblock.ui.theme.ZevTheme

/**
 * WelcomePurposeDialog:
 * First-launch introduction clearly explaining ZevBlock's core mission,
 * on-device privacy guarantees, upstream DNS disclosure, and documented Android limits.
 */
@Composable
fun WelcomePurposeDialog(
    onDismiss: () -> Unit
) {
    val colors = ZevTheme.colors

    Dialog(
        onDismissRequest = { /* Require explicit acknowledgment */ },
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
                .border(1.5.dp, colors.teal.copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.background)
                        .border(2.dp, colors.teal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "ZevBlock Protection Shield",
                        tint = colors.teal,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Welcome to ZevBlock",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.teal.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, colors.teal.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Built to keep you protected from pornography and adult content with quiet reliability.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.teal,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PurposeFeatureRow(
                        icon = Icons.Default.Block,
                        iconTint = colors.red,
                        title = "Network & Browser Defense-in-Depth",
                        description = "Local port-53 DNS sinkhole + SafeSearch VIP combined with on-device URL, keyword, and Incognito detection."
                    )

                    PurposeFeatureRow(
                        icon = Icons.Default.Message,
                        iconTint = colors.teal,
                        title = "Non-Intrusive Chat Accountability",
                        description = "Detects explicit keywords in messaging apps with gentle alerts instead of kicking you out of conversations."
                    )

                    PurposeFeatureRow(
                        icon = Icons.Default.Lock,
                        iconTint = colors.green,
                        title = "Partner TOTP & Hardware Delays",
                        description = "Protects against impulsive changes with offline authenticator codes or monotonic 24-hour cooldown locks."
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Transparent privacy & limits notice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transparency: Screen text is inspected strictly in local RAM and never uploaded or written to disk. Clean DNS lookups use family-filtered upstream resolvers (e.g. 1.1.1.3). Browser DoH or direct IPs are covered by Screen Guard.",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.greenBtnFg,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I Understand — Continue",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.greenBtnFg
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
    val colors = ZevTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.border, CircleShape),
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
