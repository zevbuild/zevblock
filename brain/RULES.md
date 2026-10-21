# ZevBlock Development & Safety Rules 🛡️

All developers and AI assistants contributing to **ZevBlock** must adhere to the rules outlined below. Violations of these rules can compromise user security, introduce bypasses, or drain battery life.

---

## 🔒 1. Security & Anti-Bypass Rules (NON-NEGOTIABLE)

1. **No Backdoors or Bypasses**: Never add bypass shortcuts, master override passwords, or debug escape hatches to `TamperLockManager`, `SettingsTamperGuard`, or `DeviceAdminManager`.
2. **Cryptographic Integrity**: Partner unlock verification must use strong cryptographic hashing (HMAC-SHA256) with salted nonces. Never store raw unhashed unlock secrets in cleartext.
3. **SafeSearch VIP Permanence**: Never alter or disable SafeSearch VIP rewrites in `SafeSearchManager` without deliberate user intention. Keep the VIP mapping restricted to official forced-search IPs.
4. **Settings Shield Persistence**: Never weaken `SettingsTamperGuard` detection when scanning package `com.android.settings`. Any attempt to disable Accessibility, Device Admin, or clear app storage must be intercepted.

---

## 👁️ 2. Privacy & Telemetry Guardrails (100% ON-DEVICE)

1. **Zero Remote Analytics**: Never include third-party tracking, crash analytics (e.g. Firebase Analytics, Sentry, Mixpanel), or ad SDKs.
2. **Zero URL Logging**: Never write visited URLs, browsing queries, or chat text to persistent disk storage or remote endpoints.
3. **Aggregated Counters Only**: `ProtectionStatsManager` is only permitted to record aggregate, non-identifying counters (e.g., total blocks, categories prevented).

---

## ⚡ 3. Performance & Battery Optimization

1. **Virtual Split Routing Only**:
   - `DnsBlockerVpnService` must exclusively capture **UDP port 53** queries.
   - Never capture or route standard TCP/UDP app traffic through the TUN interface. Capturing bulk traffic causes significant battery drain, latency spikes, and thermal throttling.
2. **Accessibility Event Throttling**:
   - Never traverse UI hierarchies without depth and child count limits.
   - Maintain a debounce or throttle interval (minimum 400ms - 1000ms) between screen content scans.
   - For chat applications, apply notification and toast throttles (minimum 4 seconds) to prevent notification flooding.
3. **In-Memory Blocklist Lookups**:
   - Keep domain and CDN matching algorithms $O(\text{labels})$ using hierarchical suffix trees or set lookups. Avoid unbounded regex iterations across massive host lists.

---

## 🧑‍💻 4. Code Standards & Kotlin Idioms

1. **Structured Concurrency**: Always launch background work inside appropriate coroutine scopes (`CoroutineScope(Dispatchers.IO + SupervisorJob())`). Avoid unmanaged threads or `GlobalScope`.
2. **Compose Best Practices**:
   - Hoist state to screen-level composables.
   - Use `remember` and `derivedStateOf` to prevent unnecessary recompositions.
   - Keep UI elements responsive and fluid with Material 3 design tokens.
3. **Exception Safety**: Wrap network and system service calls (especially `VpnService` and `AccessibilityService` APIs) in proper try-catch handlers to prevent service crashes.

---

## 📝 5. The Change Lock Mandate

Every time you modify code:
1. Update `brain/CHANGELOG.md` with full details of your changes.
2. Update the relevant section of `brain/SYSTEM_STATE.md` or `brain/ARCHITECTURE.md`.
