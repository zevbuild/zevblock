package com.zevbuild.zevblock.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevblock.core.prefs.ZevBlockPreferences
import com.zevbuild.zevblock.ui.theme.ZevBlockTheme
import com.zevbuild.zevblock.ui.theme.ZevTheme
import kotlinx.coroutines.delay

/**
 * Calm, supportive Jetpack Compose intervention overlay activity.
 * Triggered automatically when an Incognito window, NSFW content, or Android settings
 * tamper attempt is intercepted, surfacing clear rule attribution and streak encouragement.
 */
class SecurityWarningOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "Pause & Stay Focused"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "This action is restricted under your active ZevBlock safeguards."
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "SYSTEM SHIELD"
        val streakDays = intent.getIntExtra(EXTRA_STREAK_DAYS, 0)
        val themeMode = ZevBlockPreferences(this).themeMode.value

        setContent {
            ZevBlockTheme(themeMode = themeMode) {
                SecurityWarningOverlayScreen(
                    alertType = alertType,
                    message = message,
                    detail = detail,
                    category = category,
                    streakDays = streakDays,
                    onDismiss = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ALERT_TYPE = "extra_alert_type"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_DETAIL = "extra_detail"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_STREAK_DAYS = "extra_streak_days"

        fun show(
            context: Context,
            alertType: String,
            message: String,
            detail: String = "",
            category: String = "SYSTEM SHIELD",
            streakDays: Int = 0
        ) {
            try {
                val intent = Intent(context, SecurityWarningOverlayActivity::class.java).apply {
                    putExtra(EXTRA_ALERT_TYPE, alertType)
                    putExtra(EXTRA_MESSAGE, message)
                    putExtra(EXTRA_DETAIL, detail)
                    putExtra(EXTRA_CATEGORY, category)
                    putExtra(EXTRA_STREAK_DAYS, streakDays)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            } catch (t: Throwable) {
                com.zevbuild.zevblock.core.diagnostics.CrashGuardManager.recordHandledException(
                    "SecurityWarningOverlayActivity.show",
                    t,
                    autoRecovered = true
                )
            }
        }
    }
}

@Composable
fun SecurityWarningOverlayScreen(
    alertType: String,
    message: String,
    detail: String,
    category: String = "SYSTEM SHIELD",
    streakDays: Int = 0,
    onDismiss: () -> Unit
) {
    val colors = ZevTheme.colors
    var countdownSeconds by remember { mutableIntStateOf(6) }
    var autoDismissPaused by remember { mutableStateOf(false) }

    LaunchedEffect(autoDismissPaused) {
        if (!autoDismissPaused) {
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds--
            }
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.84f))
            .padding(24.dp)
            .testTag("security_overlay_root"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { autoDismissPaused = true }
                .testTag("security_warning_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(colors.red.copy(alpha = 0.85f))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Protective Shield Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.red.copy(alpha = 0.16f))
                        .border(2.dp, colors.red, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield Protection Active",
                        tint = colors.red,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rule Category Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.red.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = category.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = colors.red,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = alertType,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                if (detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.background)
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = detail,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = colors.amberSoftText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (streakDays > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.green.copy(alpha = 0.12f))
                            .border(1.dp, colors.green.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Protecting your $streakDays-day clean streak",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.green,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_overlay_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (autoDismissPaused) "Return to Safety" else "Return to Safety (${countdownSeconds}s)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.greenBtnFg
                    )
                }

                if (!autoDismissPaused) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { autoDismissPaused = true }) {
                        Text(
                            text = "Tap here to keep reading",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
    }
}
