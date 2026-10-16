package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.rules.CustomShieldRuleManager
import com.example.core.rules.PendingRemovalItem
import com.example.core.rules.RuleOperationResult
import com.example.core.rules.RuleType
import com.example.ui.theme.ShieldAmber
import com.example.ui.theme.ShieldBorderDark
import com.example.ui.theme.ShieldCardDark
import com.example.ui.theme.ShieldGreen
import com.example.ui.theme.ShieldGreenDark
import com.example.ui.theme.ShieldNavyDark
import com.example.ui.theme.ShieldRed
import com.example.ui.theme.ShieldSurfaceDark
import com.example.ui.theme.ShieldTeal
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

/**
 * CustomRulesScreen:
 * Interface for adding and managing user-defined custom blocked domains and keywords.
 * Zero-Bypass Principle: Built-in adult blocklists are permanently enforced.
 * Removing custom rules strictly requires Partner TOTP verification OR 24-hour hardware cooldown.
 */
@Composable
fun CustomRulesScreen(
    ruleManager: CustomShieldRuleManager,
    hasPartnerConfigured: Boolean,
    onNavigateToPartnerSetup: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedRuleTab by remember { mutableIntStateOf(0) } // 0: Domains, 1: Keywords

    val customDomains by ruleManager.customDomains.collectAsState()
    val customKeywords by ruleManager.customKeywords.collectAsState()
    val pendingRemovals by ruleManager.pendingRemovals.collectAsState()

    var domainInput by remember { mutableStateOf("") }
    var keywordInput by remember { mutableStateOf("") }

    // State for removal dialog
    var itemToRemove by remember { mutableStateOf<Pair<String, RuleType>?>(null) }
    var totpInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Zero-Bypass Safeguard Guarantee Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldTeal.copy(alpha = 0.5f)))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ShieldTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = "Shield", tint = ShieldTeal)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Zero-Bypass Custom Shield",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Built-in adult blocklists are permanent and immutable. Custom rules can be added instantly, but deletion requires Partner TOTP or a 24-hour cooldown lock.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Active Pending 24-Hour Cooldown Removals Card (if any)
        if (pendingRemovals.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldAmber))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.HourglassBottom, contentDescription = "Cooldown", tint = ShieldAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Pending 24-Hour Cooldown Removals",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ShieldAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hardware monotonic clock delay active to protect against impulsive relapse. Rules remain 100% blocked until cooldown expires.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    pendingRemovals.forEach { item ->
                        PendingRemovalItemRow(
                            item = item,
                            onCancel = {
                                ruleManager.cancelPendingRemoval(item.item)
                                Toast.makeText(context, "Cooldown cancelled: '${item.item}' remains blocked.", Toast.LENGTH_SHORT).show()
                            },
                            onFinalize = {
                                val res = ruleManager.finalizeCooldownRemoval(item.item)
                                when (res) {
                                    is RuleOperationResult.Success -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    is RuleOperationResult.CooldownStarted -> Toast.makeText(context, "Cooldown still active: ${formatDuration(res.remainingMs)} remaining", Toast.LENGTH_SHORT).show()
                                    else -> {}
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Tab Selector: Domains vs Keywords
        TabRow(
            selectedTabIndex = selectedRuleTab,
            containerColor = ShieldSurfaceDark,
            contentColor = ShieldGreen,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedRuleTab]),
                    color = ShieldGreen
                )
            }
        ) {
            Tab(
                selected = selectedRuleTab == 0,
                onClick = { selectedRuleTab = 0 },
                text = { Text("Blocked Domains (${customDomains.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedRuleTab == 1,
                onClick = { selectedRuleTab = 1 },
                text = { Text("Blocked Keywords (${customKeywords.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }

        // TAB 0: CUSTOM DOMAINS (DNS SINKHOLE)
        if (selectedRuleTab == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Custom Blocked Domain",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sinkholes DNS lookups for specific websites (e.g., gambling, social media, distracting portals).",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = domainInput,
                            onValueChange = { domainInput = it },
                            placeholder = { Text("e.g., reddit.com, bet365.com", color = TextMutedDark, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ShieldGreen,
                                unfocusedBorderColor = ShieldBorderDark,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (domainInput.isNotBlank()) {
                                    val res = ruleManager.addDomain(domainInput)
                                    when (res) {
                                        is RuleOperationResult.Success -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            domainInput = ""
                                        }
                                        is RuleOperationResult.AlreadyExists -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        is RuleOperationResult.ProtectedBySystem -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        is RuleOperationResult.InvalidInput -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        else -> {}
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShieldGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = ShieldNavyDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", color = ShieldNavyDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // List of Custom Domains
            if (customDomains.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Shield Active", tint = ShieldGreen, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Custom Domains Added", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("150+ top global adult tube sites & CDNs are already permanently blocked by default.", fontSize = 12.sp, color = TextSecondaryDark, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Custom Domains (${customDomains.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(12.dp))

                        customDomains.forEach { domain ->
                            CustomItemRow(
                                item = domain,
                                subtitle = "DNS Sinkhole Block",
                                onDelete = { itemToRemove = Pair(domain, RuleType.DOMAIN) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // TAB 1: CUSTOM KEYWORDS (SCREEN & CHAT INSPECTOR)
        if (selectedRuleTab == 1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Custom Blocked Keyword",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scans screen content, browser address bars, and chat apps for custom trigger phrases.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = keywordInput,
                            onValueChange = { keywordInput = it },
                            placeholder = { Text("e.g., custom trigger phrase", color = TextMutedDark, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ShieldTeal,
                                unfocusedBorderColor = ShieldBorderDark,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (keywordInput.isNotBlank()) {
                                    val res = ruleManager.addKeyword(keywordInput)
                                    when (res) {
                                        is RuleOperationResult.Success -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            keywordInput = ""
                                        }
                                        is RuleOperationResult.AlreadyExists -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        is RuleOperationResult.ProtectedBySystem -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        is RuleOperationResult.InvalidInput -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        else -> {}
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShieldTeal),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = ShieldNavyDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", color = ShieldNavyDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // List of Custom Keywords
            if (customKeywords.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Shield Active", tint = ShieldTeal, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Custom Keywords Added", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("70+ explicit terms, multilingual variants, and adult TLDs are permanently filtered by default.", fontSize = 12.sp, color = TextSecondaryDark, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ShieldCardDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ShieldBorderDark))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Custom Keywords (${customKeywords.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(12.dp))

                        customKeywords.forEach { keyword ->
                            CustomItemRow(
                                item = keyword,
                                subtitle = "Screen & Chat Word Filter",
                                onDelete = { itemToRemove = Pair(keyword, RuleType.KEYWORD) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // REMOVAL CONFIRMATION & AUTHENTICATION DIALOG
    itemToRemove?.let { (item, type) ->
        AlertDialog(
            onDismissRequest = {
                itemToRemove = null
                totpInput = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = ShieldRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Rule Deletion", color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "To guarantee zero-bypass accountability, deleting '$item' requires either Partner Authorization or a 24-Hour Cooldown.",
                        fontSize = 13.sp,
                        color = TextSecondaryDark
                    )

                    // Option A: Partner TOTP
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Option A: Partner 6-Digit TOTP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ShieldGreen)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = totpInput,
                                onValueChange = { if (it.length <= 6) totpInput = it },
                                placeholder = { Text("Enter 6-digit code", color = TextMutedDark, fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ShieldGreen,
                                    unfocusedBorderColor = ShieldBorderDark,
                                    focusedTextColor = TextPrimaryDark
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val res = if (type == RuleType.DOMAIN) {
                                        ruleManager.removeDomainWithTotp(item, totpInput)
                                    } else {
                                        ruleManager.removeKeywordWithTotp(item, totpInput)
                                    }
                                    when (res) {
                                        is RuleOperationResult.Success -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            itemToRemove = null
                                            totpInput = ""
                                        }
                                        is RuleOperationResult.TotpFailed -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ShieldGreen),
                                shape = RoundedCornerShape(6.dp),
                                enabled = totpInput.length == 6
                            ) {
                                Text("Verify Partner Code & Delete", color = ShieldNavyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Option B: 24-Hour Cooldown Delay
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ShieldSurfaceDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Option B: 24-Hour Hardware Cooldown", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ShieldAmber)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Starts a 24-hour hardware clock delay. Once 24 hours expire without relapse, deletion can be confirmed.",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    ruleManager.requestCooldownRemoval(item, type)
                                    Toast.makeText(context, "24-Hour Cooldown started for '$item'.", Toast.LENGTH_LONG).show()
                                    itemToRemove = null
                                    totpInput = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ShieldAmber)
                            ) {
                                Text("Start 24-Hour Cooldown", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(
                    onClick = {
                        itemToRemove = null
                        totpInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldSurfaceDark)
                ) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = ShieldCardDark
        )
    }
}

@Composable
fun CustomItemRow(
    item: String,
    subtitle: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ShieldSurfaceDark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked Rule", tint = ShieldTeal, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = item, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimaryDark)
                Text(text = subtitle, fontSize = 11.sp, color = TextSecondaryDark)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = ShieldRed.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PendingRemovalItemRow(
    item: PendingRemovalItem,
    onCancel: () -> Unit,
    onFinalize: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ShieldNavyDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.item, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
            Spacer(modifier = Modifier.height(2.dp))
            if (item.isReady) {
                Text(text = "Cooldown Complete! Ready for removal.", fontSize = 11.sp, color = ShieldGreen, fontWeight = FontWeight.Bold)
            } else {
                Text(text = "Cooldown: ${formatDuration(item.remainingMs)} remaining", fontSize = 11.sp, color = ShieldAmber)
            }
        }

        Row {
            if (item.isReady) {
                Button(
                    onClick = onFinalize,
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldGreen),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Confirm", color = ShieldNavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ShieldRed),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Cancel", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
