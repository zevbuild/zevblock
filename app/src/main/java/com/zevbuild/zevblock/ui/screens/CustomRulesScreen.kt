package com.zevbuild.zevblock.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevblock.core.rules.CustomShieldRuleManager
import com.zevbuild.zevblock.core.rules.PendingRemovalItem
import com.zevbuild.zevblock.core.rules.RuleOperationResult
import com.zevbuild.zevblock.core.rules.RuleType
import com.zevbuild.zevblock.core.security.TamperLockManager
import com.zevbuild.zevblock.ui.components.SegmentedOtpInput
import com.zevbuild.zevblock.ui.components.SpringBounceBox
import com.zevbuild.zevblock.ui.components.ZevStatusBadge
import com.zevbuild.zevblock.ui.components.formatDurationClock
import com.zevbuild.zevblock.ui.theme.ZevTheme
import com.zevbuild.zevblock.vpn.DnsBlockerVpnService
import com.zevbuild.zevblock.vpn.DomainCategory
import kotlinx.coroutines.delay

/**
 * CustomRulesScreen ("Rules"):
 * Scalable LazyColumn-backed rules manager distinguishing Custom Domains, Custom Keywords,
 * and Immutable Baseline + "Why Blocked?" Lookup per FEATURE_PLAN P1.
 */
@Composable
fun CustomRulesScreen(
    ruleManager: CustomShieldRuleManager,
    tamperLockManager: TamperLockManager,
    hasPartnerConfigured: Boolean,
    onNavigateToPartnerSetup: () -> Unit
) {
    val context = LocalContext.current
    val colors = ZevTheme.colors

    var selectedSegment by remember { mutableIntStateOf(0) } // 0: Domains, 1: Keywords, 2: Baseline & Lookup
    val customDomains by ruleManager.customDomains.collectAsState()
    val customKeywords by ruleManager.customKeywords.collectAsState()
    val pendingRemovals by ruleManager.pendingRemovals.collectAsState()

    var domainInput by remember { mutableStateOf("") }
    var keywordInput by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("") }
    var lookupInput by remember { mutableStateOf("www.google.co.in") }

    var itemToRemove by remember { mutableStateOf<Pair<String, RuleType>?>(null) }
    var totpInput by remember { mutableStateOf("") }
    var lockoutRemainingMs by remember { mutableLongStateOf(tamperLockManager.getLockoutRemainingMs()) }

    // Live 1-second tick for 24h pending removal countdowns
    LaunchedEffect(pendingRemovals.isNotEmpty()) {
        while (pendingRemovals.isNotEmpty()) {
            delay(1000L)
            ruleManager.refreshPendingRemovals()
        }
    }

    LaunchedEffect(itemToRemove, lockoutRemainingMs) {
        if (itemToRemove != null) {
            while (lockoutRemainingMs > 0L) {
                delay(1000L)
                lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
            }
        }
    }

    val filteredDomains = remember(customDomains, searchFilter) {
        customDomains.sorted().filter {
            searchFilter.isBlank() || it.contains(searchFilter.trim(), ignoreCase = true)
        }
    }

    val filteredKeywords = remember(customKeywords, searchFilter) {
        customKeywords.sorted().filter {
            searchFilter.isBlank() || it.contains(searchFilter.trim(), ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("custom_rules_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 3-Segment Selector Chips
        item(key = "rules_segments") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    "Domains (${customDomains.size})",
                    "Keywords (${customKeywords.size})",
                    "Baseline & Lookup"
                )
                tabs.forEachIndexed { idx, label ->
                    val selected = selectedSegment == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp)),
                        onClick = { selectedSegment = idx },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) colors.teal else colors.surface,
                        border = BorderStroke(1.dp, if (selected) colors.teal else colors.border)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) colors.tealBtnFg else colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 2. Active Pending 24-Hour Cooldown Removals (live-ticking)
        if (pendingRemovals.isNotEmpty()) {
            item(key = "pending_cooldown_header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.amber)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = colors.amberSoftText, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pending 24-Hour Cooldown Removals",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.amberSoftText
                            )
                        }
                        Text(
                            text = "Monotonic clock delay active. Rules remain 100% enforced until countdown hits 00:00.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )

                        pendingRemovals.forEach { pending ->
                            PendingRemovalItemRow(
                                item = pending,
                                onCancel = {
                                    ruleManager.cancelPendingRemoval(pending.item)
                                    Toast.makeText(context, "'${pending.item}' remains blocked.", Toast.LENGTH_SHORT).show()
                                },
                                onFinalize = {
                                    val res = ruleManager.finalizeCooldownRemoval(pending.item)
                                    when (res) {
                                        is RuleOperationResult.Success -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        is RuleOperationResult.CooldownStarted -> Toast.makeText(context, "Still active: ${formatDurationClock(res.remainingMs)}", Toast.LENGTH_SHORT).show()
                                        else -> {}
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Segment Content
        if (selectedSegment == 0 || selectedSegment == 1) {
            val isDomain = selectedSegment == 0
            item(key = "add_and_search_card_$selectedSegment") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isDomain) "Add Custom Blocked Domain" else "Add Custom Screen & Chat Keyword",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (isDomain) "Sinkholes DNS lookups for custom distractions (gambling, social media). Removable via Partner TOTP or 24h cooldown."
                            else "Monitors browser address bars, titles, and messaging alerts on-device in RAM.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = if (isDomain) domainInput else keywordInput,
                                onValueChange = { if (isDomain) domainInput = it else keywordInput = it },
                                placeholder = {
                                    Text(
                                        text = if (isDomain) "e.g., reddit.com" else "e.g., custom phrase",
                                        color = colors.textMuted,
                                        fontSize = 13.sp
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.teal,
                                    unfocusedBorderColor = colors.border,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val input = if (isDomain) domainInput else keywordInput
                                    if (input.isNotBlank()) {
                                        val res = if (isDomain) ruleManager.addDomain(input) else ruleManager.addKeyword(input)
                                        when (res) {
                                            is RuleOperationResult.Success -> {
                                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                                if (isDomain) domainInput = "" else keywordInput = ""
                                            }
                                            is RuleOperationResult.AlreadyExists -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                            is RuleOperationResult.ProtectedBySystem -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                            is RuleOperationResult.InvalidInput -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            else -> {}
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Rule", tint = colors.greenBtnFg)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", color = colors.greenBtnFg, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = searchFilter,
                            onValueChange = { searchFilter = it },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted) },
                            placeholder = { Text("Filter active rules...", color = colors.textMuted, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.teal,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                    }
                }
            }

            val itemsToShow = if (isDomain) filteredDomains else filteredKeywords
            if (itemsToShow.isEmpty()) {
                item(key = "empty_rules_$selectedSegment") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchFilter.isNotBlank()) "No matching custom rules"
                                else if (isDomain) "No Custom Domains Added" else "No Custom Keywords Added",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Built-in adult domains, SafeSearch VIPs, and explicit keywords remain permanently active.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                items(itemsToShow, key = { "${selectedSegment}_$it" }) { entry ->
                    CustomItemRow(
                        item = entry,
                        subtitle = if (isDomain) "Custom DNS Sinkhole (0.0.0.0)" else "Custom Screen & Chat Trigger",
                        onDelete = {
                            lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
                            itemToRemove = Pair(entry, if (isDomain) RuleType.DOMAIN else RuleType.KEYWORD)
                        }
                    )
                }
            }
        } else {
            // Segment 2: Rule Category Lookup ("Why Was X Blocked?") + Read-Only Baseline Summary
            item(key = "rule_lookup_card") {
                val lookup = remember(lookupInput, customDomains) {
                    DnsBlockerVpnService.inspectDomainLookup(lookupInput)
                }
                val catColor = when (lookup.category) {
                    DomainCategory.SAFESEARCH_VIP -> colors.teal
                    DomainCategory.ADULT_SHIELD -> colors.red
                    DomainCategory.CUSTOM_RULE -> colors.amber
                    DomainCategory.ALLOWED -> colors.green
                    DomainCategory.INVALID -> colors.textMuted
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Why Was a Website Handled This Way?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Inspect any hostname to see which rule category applies and whether it can be changed.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )

                        OutlinedTextField(
                            value = lookupInput,
                            onValueChange = { lookupInput = it },
                            placeholder = { Text("e.g., www.google.co.in or pornhub.com", color = colors.textMuted, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rule_lookup_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.teal,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.background)
                                .border(1.dp, catColor.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lookup.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = catColor
                                    )
                                    ZevStatusBadge(
                                        label = if (lookup.isEditable) "USER RULE" else "SYSTEM",
                                        color = catColor
                                    )
                                }
                                Text(
                                    text = lookup.detail,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            item(key = "baseline_summary_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Built-In Safeguard Baseline (Immutable)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                        Text("• Adult Domains & CDNs: 150+ hierarchical hosts & streaming CDNs sinkholed permanently to 0.0.0.0.", fontSize = 12.sp, color = colors.textSecondary)
                        Text("• Strict SafeSearch VIPs: Google (regional ccTLDs), YouTube, Bing, DuckDuckGo, Yahoo, Brave, and Qwant.", fontSize = 12.sp, color = colors.textSecondary)
                        Text("• Screen & Chat Classifier: Explicit terms, adult TLDs (.xxx, .adult), and search parameters checked in RAM.", fontSize = 12.sp, color = colors.textSecondary)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // Secure Custom Rule Deletion Modal (numeric segmented OTP + lockout + 24h cooldown)
    itemToRemove?.let { (item, type) ->
        AlertDialog(
            onDismissRequest = {
                itemToRemove = null
                totpInput = ""
            },
            containerColor = colors.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = colors.red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Rule Deletion", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Deleting '$item' requires Partner 6-digit TOTP authorization or a 24-hour hardware cooldown.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )

                    if (lockoutRemainingMs > 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.red.copy(alpha = 0.14f))
                                .border(1.dp, colors.red.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "TOTP locked for ${formatDurationClock(lockoutRemainingMs)} due to repeated invalid codes.",
                                fontSize = 12.sp,
                                color = colors.red,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Option A: Partner TOTP
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.background),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (hasPartnerConfigured) "Option A: Partner 6-Digit TOTP" else "Option A: Partner Not Paired",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green
                            )
                            if (hasPartnerConfigured) {
                                SegmentedOtpInput(
                                    value = totpInput,
                                    onValueChange = { totpInput = it },
                                    inputTestTag = "rule_delete_totp_input"
                                )
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
                                                lockoutRemainingMs = tamperLockManager.getLockoutRemainingMs()
                                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            }
                                            else -> {}
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = totpInput.length == 6 && lockoutRemainingMs <= 0L
                                ) {
                                    Text("Verify Partner Code & Delete", color = colors.greenBtnFg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        itemToRemove = null
                                        onNavigateToPartnerSetup()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Pair Accountability Partner", color = colors.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Option B: 24-Hour Cooldown
                    OutlinedButton(
                        onClick = {
                            ruleManager.requestCooldownRemoval(item, type)
                            Toast.makeText(context, "24-hour cooldown started for '$item'.", Toast.LENGTH_LONG).show()
                            itemToRemove = null
                            totpInput = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.65f))
                    ) {
                        Text("Option B: Start 24-Hour Cooldown", color = colors.amberSoftText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        itemToRemove = null
                        totpInput = ""
                    }
                ) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}

@Composable
fun CustomItemRow(
    item: String,
    subtitle: String,
    onDelete: () -> Unit
) {
    val colors = ZevTheme.colors
    SpringBounceBox(modifier = Modifier.fillMaxWidth(), scaleDown = 0.98f) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = "Locked Rule", tint = colors.teal, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = item, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.textPrimary)
                    Text(text = subtitle, fontSize = 12.sp, color = colors.textSecondary)
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Request removal", tint = colors.red, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun PendingRemovalItemRow(
    item: PendingRemovalItem,
    onCancel: () -> Unit,
    onFinalize: () -> Unit
) {
    val colors = ZevTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.background)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.item, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            if (item.isReady) {
                Text(text = "Cooldown complete! Ready to confirm.", fontSize = 12.sp, color = colors.green, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = "Remaining: ${formatDurationClock(item.remainingMs)}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.amberSoftText
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        if (item.isReady) {
            Button(
                onClick = onFinalize,
                colors = ButtonDefaults.buttonColors(containerColor = colors.greenBtn),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm", color = colors.greenBtnFg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, colors.red.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Keep Rule", color = colors.red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
