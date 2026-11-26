package com.zevbuild.zevblock.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevblock.core.security.TamperLockManager
import com.zevbuild.zevblock.ui.components.PartnerUnlockGateDialog
import com.zevbuild.zevblock.ui.components.SegmentedOtpInput
import com.zevbuild.zevblock.ui.components.ZevStatusBadge
import com.zevbuild.zevblock.ui.components.formatDurationClock
import com.zevbuild.zevblock.ui.qr.QrCodeView
import com.zevbuild.zevblock.ui.theme.ZevTheme
import kotlinx.coroutines.delay

/**
 * Accountability Partner Vault Screen ("Partner"):
 * - Enforces Vault hiding once pairing is verified so the QR code & Base32 secret cannot be
 *   re-scanned or overwritten without Partner TOTP or a 24-hour Cooldown.
 * - Surfaces live TamperLockManager brute-force lockout timers clearly.
 * - Uses 6-digit segmented numeric OTP controls for verification and temporary bypass windows.
 */
@Composable
fun PartnerSetupScreen(
    tamperLockManager: TamperLockManager,
    isCooldownActive: Boolean,
    cooldownRemainingMs: Long,
    isBypassActive: Boolean = false,
    bypassRemainingMs: Long = 0L,
    onStartCooldown: (Long, String) -> Unit,
    onCancelCooldown: () -> Unit,
    onCompleteCooldownUnpair: () -> Unit = {},
    onStartBypass: (Long) -> Unit = {},
    onCancelBypass: () -> Unit = {},
    onPartnerStateChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = ZevTheme.colors
    val scrollState = rememberScrollState()

    var partnerSecret by remember { mutableStateOf(tamperLockManager.getPartnerSecret()) }
    var totpUri by remember { mutableStateOf(tamperLockManager.getPartnerTotpUri()) }
    var isPairingConfirmed by remember { mutableStateOf(tamperLockManager.isPairingConfirmed()) }
    var isVaultTemporarilyRevealed by remember { mutableStateOf(false) }

    var verificationInput by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<Boolean?>(null) }
    var lockoutRemainingMs by remember { mutableLongStateOf(tamperLockManager.getLockoutRemainingMs()) }
    var showRevealVaultGate by remember { mutableStateOf(false) }

    LaunchedEffect(lockoutRemainingMs) {
        while (lockoutRemainingMs > 0L) {
            delay(1000L)
            lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
        }
    }

    fun refreshSecretState() {
        partnerSecret = tamperLockManager.getPartnerSecret()
        totpUri = tamperLockManager.getPartnerTotpUri()
        isPairingConfirmed = tamperLockManager.isPairingConfirmed()
        lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
        onPartnerStateChanged()
    }

    if (showRevealVaultGate) {
        PartnerUnlockGateDialog(
            title = "Partner Vault Authorization",
            description = "Verify your partner's 6-digit TOTP code to reveal, regenerate, or unpair your accountability key.",
            tamperLockManager = tamperLockManager,
            cooldownPurpose = TamperLockManager.COOLDOWN_PURPOSE_UNPAIR,
            cooldownDurationMs = TamperLockManager.COOLDOWN_24_HOURS_MS,
            onDismiss = { showRevealVaultGate = false },
            onAuthorized = {
                isVaultTemporarilyRevealed = true
                showRevealVaultGate = false
                refreshSecretState()
            },
            onCooldownStarted = {
                onStartCooldown(TamperLockManager.COOLDOWN_24_HOURS_MS, TamperLockManager.COOLDOWN_PURPOSE_UNPAIR)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Lockout Alert Banner (shown whenever throttled by TamperLockManager)
        if (lockoutRemainingMs > 0L) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("partner_lockout_banner"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.red.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, colors.red)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.red, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Too many invalid TOTP attempts. Verification locked for ${formatDurationClock(lockoutRemainingMs)}.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.red
                    )
                }
            }
        }

        // 1. Vault / Pairing Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_pairing_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(
                1.dp,
                if (partnerSecret != null && isPairingConfirmed) colors.green.copy(alpha = 0.65f) else colors.border
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (partnerSecret != null && isPairingConfirmed && !isVaultTemporarilyRevealed) {
                    // SECURED VAULT STATE: Key & QR are concealed to prevent self-rescan/bypass
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.green.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = colors.green, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Partner Vault Protected",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Keystore-encrypted • Key hidden from device",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                        ZevStatusBadge(label = "LOCKED", color = colors.green)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "For anti-bypass integrity, your QR code and Base32 secret are concealed once paired. Ask your partner for a 6-digit code to view or reset pairing.",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { showRevealVaultGate = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.teal)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reveal Key or Unpair (Requires Partner)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                    }
                } else if (partnerSecret != null && totpUri != null) {
                    // PAIRING OR TEMPORARILY REVEALED STATE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isVaultTemporarilyRevealed) "Partner Key Unlocked" else "Step 1: Share Key With Partner",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        if (isVaultTemporarilyRevealed) {
                            OutlinedButton(
                                onClick = { isVaultTemporarilyRevealed = false },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Hide Key", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.green)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Have your friend scan this QR in Google Authenticator, Aegis, Bitwarden, or 2FAS—or tap Open Authenticator.",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .testTag("partner_qr_code_view"),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeView(
                            data = totpUri!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Accessible Copy Base32 Box (48dp touch target)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.background)
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
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
                                fontSize = 12.sp,
                                color = colors.green,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { copyToClipboard(context, partnerSecret!!, "Secret copied!") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Secret", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { openUriInAuthenticator(context, totpUri!!) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.tealBtnFg)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Authenticator", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.tealBtnFg)
                        }

                        OutlinedButton(
                            onClick = { copyToClipboard(context, totpUri!!, "otpauth:// URI copied!") },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text("Copy URI", fontSize = 12.sp, color = colors.textPrimary)
                        }
                    }

                    if (isVaultTemporarilyRevealed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    tamperLockManager.setupNewPartnerSecret()
                                    isVaultTemporarilyRevealed = false
                                    verificationResult = null
                                    refreshSecretState()
                                    Toast.makeText(context, "Generated New Partner Key — Verify First Code", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rotate Key", fontSize = 12.sp, color = colors.textPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    tamperLockManager.clearPartnerSecret()
                                    isVaultTemporarilyRevealed = false
                                    verificationResult = null
                                    refreshSecretState()
                                    Toast.makeText(context, "Partner pairing removed", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.red.copy(alpha = 0.6f))
                            ) {
                                Text("Unpair", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.red)
                            }
                        }
                    }
                } else {
                    // UNPAIRED EMPTY STATE
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = colors.teal, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Pair Accountability Partner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generate an offline 160-bit TOTP key for a trusted friend to hold. Works with zero accounts or internet connection.",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            tamperLockManager.setupNewPartnerSecret()
                            refreshSecretState()
                            Toast.makeText(context, "Generated Secret — Confirm Partner Code Below", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Generate Secret & QR Code", fontWeight = FontWeight.Bold, color = colors.tealBtnFg)
                    }
                }
            }
        }

        // 2. Verification Card (locks secret automatically upon first confirmation!)
        if (partnerSecret != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("totp_verification_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (!isPairingConfirmed) "Step 2: Verify Partner's First Code to Lock" else "Test Partner TOTP Code",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (!isPairingConfirmed) "Enter the 6-digit code from your partner's authenticator to confirm pairing and hide the key."
                        else "Verify a rolling 6-digit code with replay protection and +/-30s clock tolerance.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )

                    SegmentedOtpInput(
                        value = verificationInput,
                        onValueChange = {
                            verificationInput = it
                            verificationResult = null
                        },
                        inputTestTag = "partner_verification_input"
                    )

                    Button(
                        onClick = {
                            val valid = tamperLockManager.verifyPartnerTotp(verificationInput)
                            verificationResult = valid
                            lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
                            if (valid) {
                                isVaultTemporarilyRevealed = false
                                verificationInput = ""
                                refreshSecretState()
                            }
                        },
                        enabled = verificationInput.length == 6 && lockoutRemainingMs <= 0L,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("partner_verify_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (!isPairingConfirmed) "Confirm Pairing & Lock Vault" else "Verify Rolling Code",
                            fontWeight = FontWeight.Bold,
                            color = colors.greenBtnFg
                        )
                    }

                    if (verificationResult != null) {
                        val isSuccess = verificationResult == true
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background((if (isSuccess) colors.green else colors.red).copy(alpha = 0.14f))
                                .border(1.dp, (if (isSuccess) colors.green else colors.red).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isSuccess) colors.green else colors.red,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isSuccess -> "Pairing confirmed! Key hidden in protected vault."
                                        lockoutRemainingMs > 0L -> "Too many invalid attempts. Wait ${formatDurationClock(lockoutRemainingMs)}."
                                        else -> "Code mismatch or already used. Check device clock & ask for next code."
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSuccess) colors.green else colors.red
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Temporary Bypass Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("partner_temporary_bypass_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, if (isBypassActive) colors.amber else colors.border)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = if (isBypassActive) colors.amberSoftText else colors.teal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Temporary Bypass Window", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                    ZevStatusBadge(
                        label = if (isBypassActive) "ACTIVE" else "PARTNER AUTH",
                        color = if (isBypassActive) colors.amber else colors.teal
                    )
                }

                Text(
                    text = "Temporarily suspends custom-added rules & Settings guard with Partner approval. Adult domains, explicit keywords, SafeSearch VIP, and Incognito blocks stay strictly locked.",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 17.sp
                )

                if (isBypassActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.background)
                            .border(1.dp, colors.amber, RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BYPASS REMAINING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.amberSoftText)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatDurationClock(bypassRemainingMs),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = colors.amberSoftText
                            )
                            Text("Auto re-locks at 00:00", fontSize = 11.sp, color = colors.textMuted)
                        }
                    }

                    Button(
                        onClick = onCancelBypass,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.redBtn),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("End Bypass & Re-Lock Now", fontWeight = FontWeight.Bold, color = colors.redBtnFg)
                    }
                } else {
                    var selectedDurationMs by remember { mutableLongStateOf(TamperLockManager.BYPASS_15_MIN_MS) }
                    var bypassTotpInput by remember { mutableStateOf("") }
                    var bypassError by remember { mutableStateOf<String?>(null) }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            TamperLockManager.BYPASS_15_MIN_MS to "15 Min",
                            TamperLockManager.BYPASS_30_MIN_MS to "30 Min",
                            TamperLockManager.BYPASS_1_HOUR_MS to "1 Hour"
                        ).forEach { (ms, label) ->
                            val isSelected = selectedDurationMs == ms
                            OutlinedButton(
                                onClick = { selectedDurationMs = ms },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) colors.teal.copy(alpha = 0.16f) else colors.background
                                ),
                                border = BorderStroke(1.dp, if (isSelected) colors.teal else colors.border)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colors.teal else colors.textSecondary
                                )
                            }
                        }
                    }

                    SegmentedOtpInput(
                        value = bypassTotpInput,
                        onValueChange = {
                            bypassTotpInput = it
                            bypassError = null
                        },
                        inputTestTag = "bypass_totp_input"
                    )

                    Button(
                        onClick = {
                            val ok = tamperLockManager.activateBypassWithTotp(bypassTotpInput, selectedDurationMs)
                            lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
                            if (ok) {
                                onStartBypass(selectedDurationMs)
                                bypassTotpInput = ""
                                Toast.makeText(context, "Temporary Bypass Activated", Toast.LENGTH_SHORT).show()
                            } else {
                                bypassError = if (lockoutRemainingMs > 0L) {
                                    "Locked out for ${formatDurationClock(lockoutRemainingMs)}."
                                } else {
                                    "Invalid or replayed code. Ask partner for next code."
                                }
                            }
                        },
                        enabled = partnerSecret != null && bypassTotpInput.length == 6 && lockoutRemainingMs <= 0L,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.amber),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Start Partner-Approved Bypass", fontWeight = FontWeight.Bold, color = colors.amberBtnFg)
                    }

                    if (bypassError != null) {
                        Text(text = bypassError!!, color = colors.red, fontSize = 12.sp)
                    }
                }
            }
        }

        // 4. Emergency Monotonic Cooldown Delay Card (real unlock targets only)
        val isCooldownFinished = tamperLockManager.isCooldownFinished()
        val cooldownPurpose = tamperLockManager.getCooldownPurpose()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("partner_cooldown_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = colors.amberSoftText, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Emergency Timed Delay", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                    ZevStatusBadge(
                        label = when {
                            isCooldownFinished -> "READY"
                            isCooldownActive -> "ACTIVE"
                            else -> "STANDBY"
                        },
                        color = if (isCooldownFinished) colors.green else if (isCooldownActive) colors.amber else colors.textMuted
                    )
                }

                Text(
                    text = "If your partner is unreachable, schedule a monotonic clock delay (verified via SystemClock.elapsedRealtime()) before unpairing or changing locks.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )

                if (isCooldownActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.background)
                            .border(1.dp, colors.amber, RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatDurationClock(cooldownRemainingMs),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = colors.amberSoftText
                            )
                            Text("Purpose: $cooldownPurpose", fontSize = 11.sp, color = colors.textMuted)
                        }
                    }
                    OutlinedButton(
                        onClick = onCancelCooldown,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.red.copy(alpha = 0.6f))
                    ) {
                        Text("Cancel Cooldown & Stay Protected", color = colors.red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (isCooldownFinished) {
                    Button(
                        onClick = {
                            if (tamperLockManager.consumeCompletedCooldown()) {
                                if (cooldownPurpose == TamperLockManager.COOLDOWN_PURPOSE_UNPAIR) {
                                    tamperLockManager.clearPartnerSecret()
                                    refreshSecretState()
                                    onCompleteCooldownUnpair()
                                }
                                Toast.makeText(context, "Completed delayed unlock", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Confirm Completed Unlock ($cooldownPurpose)", color = colors.greenBtnFg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else if (partnerSecret != null) {
                    OutlinedButton(
                        onClick = {
                            onStartCooldown(TamperLockManager.COOLDOWN_24_HOURS_MS, TamperLockManager.COOLDOWN_PURPOSE_UNPAIR)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.65f))
                    ) {
                        Text("Request 24-Hour Partner Unpair Delay", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.amberSoftText)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun openUriInAuthenticator(context: Context, uriString: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No authenticator app on this device. Scan QR on your partner's phone.", Toast.LENGTH_LONG).show()
    }
}

private fun copyToClipboard(context: Context, text: String, msg: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("ZevBlock", text))
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
