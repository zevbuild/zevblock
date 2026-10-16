package com.zevbuild.zevblock.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevblock.core.security.TamperLockManager
import com.zevbuild.zevblock.ui.qr.QrCodeView
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

/**
 * Accountability Partner Setup & QR Code Screen:
 * Displays the RFC 6238 visual QR Code for scanning with Google Authenticator,
 * Base32 manual keys, and verification tester.
 */
@Composable
fun PartnerSetupScreen(
    tamperLockManager: TamperLockManager,
    isCooldownActive: Boolean,
    cooldownRemainingMs: Long,
    isBypassActive: Boolean = false,
    bypassRemainingMs: Long = 0L,
    onStartCooldown: (Long) -> Unit,
    onCancelCooldown: () -> Unit,
    onStartBypass: (Long) -> Unit = {},
    onCancelBypass: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var partnerSecret by remember { mutableStateOf(tamperLockManager.getPartnerSecret()) }
    var totpUri by remember { mutableStateOf(tamperLockManager.getPartnerTotpUri()) }
    var verificationInput by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<Boolean?>(null) }

    fun refreshSecret() {
        partnerSecret = tamperLockManager.getPartnerSecret()
        totpUri = tamperLockManager.getPartnerTotpUri()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ShieldSurfaceDark)
                .border(1.dp, ShieldBorderDark, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ShieldTeal.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = ShieldTeal,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Accountability Partner Lock",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Offline RFC 6238 TOTP Two-Person Rule",
                            fontSize = 12.sp,
                            color = ShieldTeal
                        )
                    }
                }
            }
        }

        // QR Code Pairing Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_pairing_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (partnerSecret != null && totpUri != null) {
                    Text(
                        text = "Scan with Friend's Authenticator",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Have your trusted friend open Google Authenticator, Aegis, Bitwarden, or 2FAS and scan this code.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // QR Code View Canvas
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .testTag("partner_qr_code_view"),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeView(
                            data = totpUri!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Secret text box
                    Text(
                        text = "Secret Key (Base32):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldNavyDark)
                            .border(1.dp, ShieldBorderDark, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = partnerSecret!!,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ShieldGreen,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { copyToClipboard(context, partnerSecret!!, "Secret copied!") },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Secret", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { copyToClipboard(context, totpUri!!, "otpauth:// URI copied!") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Copy URI", fontSize = 11.sp, color = ShieldTeal)
                        }

                        OutlinedButton(
                            onClick = {
                                val newSec = tamperLockManager.setupNewPartnerSecret()
                                refreshSecret()
                                verificationResult = null
                                Toast.makeText(context, "Regenerated New Partner Key", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate", fontSize = 11.sp, color = TextPrimaryDark)
                        }
                    }
                } else {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = ShieldTeal, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Accountability Partner Paired",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generate a cryptographic 160-bit Base32 secret for your friend to hold. You will not be able to bypass protection without their code.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            tamperLockManager.setupNewPartnerSecret()
                            refreshSecret()
                            Toast.makeText(context, "Generated Partner Secret", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Generate Secret & Show QR Code", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Verification Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("totp_verification_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Verify Friend's 6-Digit Code",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ask your friend for the current rolling code showing in their authenticator app to confirm pairing.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = verificationInput,
                        onValueChange = { if (it.length <= 6) verificationInput = it },
                        placeholder = { Text("000000", color = TextMutedDark) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("partner_verification_input"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ShieldTeal,
                            unfocusedBorderColor = ShieldBorderDark,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )

                    Button(
                        onClick = {
                            val valid = tamperLockManager.verifyPartnerTotp(verificationInput)
                            verificationResult = valid
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("partner_verify_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Verify", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (verificationResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val isSuccess = verificationResult == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSuccess) ShieldGreen.copy(alpha = 0.15f)
                                else ShieldRed.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (isSuccess) ShieldGreen.copy(alpha = 0.4f) else ShieldRed.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isSuccess) ShieldGreen else ShieldRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSuccess) "Pairing confirmed! Rolling TOTP verified successfully."
                                else "Code mismatch. Ensure phone clocks are synchronized.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSuccess) ShieldGreen else ShieldRed
                            )
                        }
                    }
                }
            }
        }

        // Temporary Bypass Card (TOTP Authorized)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("partner_temporary_bypass_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (isBypassActive) ShieldAmber else ShieldTeal
                )
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isBypassActive) ShieldAmber else ShieldTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Temporary Bypass Window",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isBypassActive) ShieldAmber.copy(alpha = 0.15f) else ShieldTeal.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isBypassActive) "ACTIVE" else "REQUIRES PARTNER",
                            color = if (isBypassActive) ShieldAmber else ShieldTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Temporarily suspend all keyword, adult domain, and incognito restrictions with your partner's 6-digit TOTP approval. Protections automatically re-engage when time expires.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isBypassActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldNavyDark)
                            .border(1.dp, ShieldAmber, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "TEMPORARY BYPASS RUNNING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ShieldAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatMs(bypassRemainingMs),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = ShieldAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All filters will automatically re-lock at 00:00",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onCancelBypass,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("End Bypass & Re-Lock Protections Now", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    var selectedDurationMs by remember { mutableLongStateOf(TamperLockManager.BYPASS_15_MIN_MS) }
                    var bypassTotpInput by remember { mutableStateOf("") }
                    var bypassError by remember { mutableStateOf<String?>(null) }

                    Text(
                        text = "Select Bypass Duration:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            TamperLockManager.BYPASS_15_MIN_MS to "15 Min",
                            TamperLockManager.BYPASS_30_MIN_MS to "30 Min",
                            TamperLockManager.BYPASS_1_HOUR_MS to "1 Hour"
                        ).forEach { (ms, label) ->
                            val isSelected = selectedDurationMs == ms
                            OutlinedButton(
                                onClick = { selectedDurationMs = ms },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) ShieldTeal.copy(alpha = 0.2f) else Color.Transparent
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) ShieldTeal else ShieldBorderDark
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ShieldTeal else TextSecondaryDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = bypassTotpInput,
                            onValueChange = {
                                if (it.length <= 6) {
                                    bypassTotpInput = it
                                    bypassError = null
                                }
                            },
                            placeholder = { Text("Partner 6-digit TOTP", color = TextMutedDark, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ShieldTeal,
                                unfocusedBorderColor = ShieldBorderDark,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )

                        Button(
                            onClick = {
                                val success = tamperLockManager.activateBypassWithTotp(bypassTotpInput, selectedDurationMs)
                                if (success) {
                                    onStartBypass(selectedDurationMs)
                                    bypassTotpInput = ""
                                    bypassError = null
                                    Toast.makeText(context, "Temporary Bypass Activated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    bypassError = "Invalid partner code! Check friend's authenticator app."
                                }
                            },
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ShieldAmber),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Bypass", fontWeight = FontWeight.Bold, color = ShieldNavyDark)
                        }
                    }

                    if (bypassError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = bypassError!!,
                            color = ShieldRed,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Cooldown Timer Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("partner_cooldown_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.HourglassBottom, contentDescription = null, tint = ShieldAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tamper Delay (Cooldown)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCooldownActive) ShieldAmber.copy(alpha = 0.15f) else ShieldGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isCooldownActive) "COUNTING DOWN" else "READY",
                            color = if (isCooldownActive) ShieldAmber else ShieldGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enforces a mandatory delay before protections can be lifted, verified against the hardware monotonic clock (SystemClock.elapsedRealtime()).",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isCooldownActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldNavyDark)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatMs(cooldownRemainingMs),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ShieldAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onCancelCooldown,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel Cooldown", color = ShieldRed, fontSize = 12.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onStartCooldown(60_000L) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ShieldSurfaceDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("1 Min Test", fontSize = 12.sp, color = TextPrimaryDark)
                        }

                        Button(
                            onClick = { onStartCooldown(300_000L) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ShieldGreenDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("5 Min Lock", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

private fun formatMs(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    val s = sec % 60
    return String.format("%02d:%02d", min, s)
}

private fun copyToClipboard(context: Context, text: String, msg: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("ZevBlock", text))
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
