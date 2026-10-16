# ZevBlock

> **Open-Source Anti-Bypass Protection Core, Local DNS Sinkhole & Accountability OS for Android**

ZevBlock is a hardened open-source Android protection platform designed to enforce screen-time safeguards, eliminate pornography and explicit content, intercept tracker telemetries, and prevent self-tampering or unauthorized app uninstallation.

---

## 📦 Download & Install APK

You can download the pre-compiled APK directly from GitHub:

### Option A: From GitHub Releases (Recommended)
1. Go to the [**Releases**](https://github.com/) section on the right side of this repository.
2. Under the latest release, download `ZevBlock-v1.0-debug.apk`.
3. Tap the downloaded APK on your Android phone to install.

### Option B: From GitHub Actions (Automated CI Builds)
1. Navigate to the [**Actions**](https://github.com/) tab at the top of the repository.
2. Click on the latest workflow run under **"Build & Release ZevBlock APK"**.
3. Scroll down to the **Artifacts** section at the bottom of the page.
4. Click **`ZevBlock-v1.0-debug-apk`** to download the ZIP file containing the APK.

---

## 🛡️ Core Defense Pillars

### 1. Device Owner & Anti-Uninstall Lockdown
- **Uninstall Prevention:** Invokes `DevicePolicyManager.setUninstallBlocked()` to lock app deletion at the Android system level.
- **Safe Mode Blocking:** Enforces `UserManager.DISALLOW_SAFE_BOOT` so restrictions cannot be bypassed via rebooting into Android Safe Mode.
- **Private DNS Guard:** Restricts `UserManager.DISALLOW_CONFIG_PRIVATE_DNS` to prevent bypassing local DNS sinkholes via Android 9+ DNS-over-TLS (DoT).
- **Always-On VPN Lockdown:** Enforces `setAlwaysOnVpnPackage(adminComponent, packageName, lockdown = true)` to block all network traffic if the VPN service is ever disconnected.

### 2. Local DNS Sinkhole (`DnsBlockerVpnService`)
- **Battery-Friendly Virtual DNS Routing:** Allocates virtual IP `10.254.1.1/32` (and IPv6 `fd00::1/128`) and routes **only port 53 DNS queries** through the TUN interface. Standard TCP/UDP data traffic (browsing, streaming, downloads) continues at full wire speed without VPN latency.
- **Raw IP/UDP DNS Packet Parser & Builder:** Zero-dependency binary parser for IPv4 and IPv6 packets, UDP headers, and RFC 1035 DNS messages with label decompression and 16-bit Internet checksum calculation.
- **Strict SafeSearch VIP Rewriting:** Intercepts DNS queries targeting search engines and forces them to strict VIPs:
  - **Google:** `216.239.38.120` (`forcesafesearch.google.com`)
  - **YouTube:** `216.239.38.119` (Strict Restricted Mode VIP)
  - **Bing:** `204.79.197.220` (`strict.bing.com`)
  - **DuckDuckGo:** `safe.duckduckgo.com`
- **In-Memory StevenBlack Hosts Matcher:** Parses standard StevenBlack unified hosts lists and performs hierarchical subdomain matching (e.g. blocking `doubleclick.net` automatically blocks `ads.tracking.doubleclick.net`).
- **Protected Upstream Resolution:** Uses `protect(datagramSocket)` to query upstream resolvers (Cloudflare `1.1.1.1:53` and Quad9 `9.9.9.9:53`) outside the TUN interface.

### 3. Accessibility Browser Scraper & Settings Guard
- **Browser Address Bar Extraction:** Scrapes active URLs and search terms across **Google Chrome**, **Mozilla Firefox**, **Brave Browser**, **Microsoft Edge**, and **Samsung Internet**.
- **Incognito & Private Window Blocker:** Detects Incognito mode triggers and immediately executes `GLOBAL_ACTION_BACK` accompanied by a security warning overlay.
- **NSFW Keyword & Search Parameter Inspection:** Extracts search parameters (`?q=...`, `&search_query=...`) to catch explicit lookups before web pages render, while employing regex boundary logic to prevent false positives (e.g., "essex").
- **Settings Anti-Tamper Guard:** Inspects `com.android.settings` windows to prevent unauthorized navigation into:
  - App Info management (blocks "Force stop", "Uninstall", "Clear data")
  - Accessibility Service settings (prevents toggling off the service)
  - Device Administrator settings (prevents deactivation)
  - VPN & Private DNS configuration screens

### 4. Accountability Partner & Monotonic Cooldown Lock
- **Offline RFC 6238 TOTP Engine:** Generates a 160-bit cryptographically secure Base32 secret and renders an interactive visual QR Code on a Compose `Canvas` scannable by Google Authenticator, Aegis, Bitwarden, or 2FAS on a friend's phone.
- **Hardware Monotonic Clock Timer:** Employs `SystemClock.elapsedRealtime()` (hardware clock running since device boot, including deep sleep). Changing device date/time, advancing the system clock, or shifting timezones cannot accelerate or bypass countdown delays.
- **HMAC-SHA256 Integrity Sealing:** Seals lock state with an HMAC signature to detect and invalidate manual storage tampering.

---

## 📱 User Interface & Onboarding Flow

- **Dashboard:** Hero defense posture card with dynamic emerald pulsing animation, 2x2 module grid, live telemetry counters, and real-time security activity feed.
- **Android 13+ "Restricted Settings" Bypass Visual Guide:** Step-by-step illustrated walkthrough resolving Android 13/14/15 sideloaded accessibility blocks with direct intent launchers.
- **Partner Setup Screen:** Pure-Kotlin QR code canvas view, copyable Base32 secret key, and instant 6-digit rolling code verifier.
- **Interactive Tools & Simulators:** Test DNS lookups against SafeSearch VIPs and StevenBlack blocklists, evaluate search query URLs, and preview the translucent warning overlay.

---

## 🚀 Setup & Provisioning

### 1. Activating Device Owner (ADB)

To unlock hardware-level anti-uninstall protection and Safe Mode blocking, provision ZevBlock as the Device Owner via ADB:

```bash
# Connect phone via USB with USB Debugging enabled
adb shell dpm set-device-owner com.example/.receiver.ShieldDeviceAdminReceiver
```

*Note: Android requires that personal accounts (Google, etc.) are temporarily removed before provisioning Device Owner on an active device, or provisioned during initial setup wizard.*

### 2. Activating via Shizuku / Root

```bash
dpm set-device-owner com.example/.receiver.ShieldDeviceAdminReceiver
```

### 3. Bypassing Android 13+ "Restricted Settings" for Sideloaded APKs

On Android 13, 14, and 15, accessibility services on sideloaded APKs are restricted by default:

1. Open **Settings > Apps > See all apps > ZevBlock** (or tap **"1. Open App Info"** in the ZevBlock setup tab).
2. Tap the **three vertical dots (⋮)** in the top right corner.
3. Tap **"Allow restricted settings"** and authenticate with your device PIN / fingerprint.
4. Return to ZevBlock and tap **"2. Open Accessibility"** to toggle on the service.

---

## 🧪 Testing & Verification

The project includes unit and Robolectric tests for local verification without an emulator:

```bash
# Run all unit and Robolectric tests
gradle :app:testDebugUnitTest
```

Tested components include:
- Base32 encoding / decoding and RFC 6238 TOTP rolling verification
- Monotonic clock countdown computation and reboot recovery
- Raw IPv4/IPv6 UDP packet parsing and DNS response synthesis (A, AAAA, CNAME, NXDOMAIN)
- Internet checksum and UDP pseudo-header checksum calculations
- Google and YouTube SafeSearch VIP rewriting
- StevenBlack hosts file parser and hierarchical subdomain matching
- NSFW search query parameter extractor and keyword classifier
- Browser URL extractor package identification

---

## 🏗️ Project Architecture

```
app/src/main/
├── AndroidManifest.xml                        # Service, receiver, and permission declarations
├── java/com/example/
│   ├── MainActivity.kt                        # Primary Material 3 App Shell & Navigation
│   ├── core/
│   │   ├── admin/
│   │   │   └── DeviceAdminManager.kt          # DevicePolicyManager anti-bypass enforcer
│   │   ├── dns/
│   │   │   ├── DnsPacket.kt                   # Binary IP/UDP and DNS data models
│   │   │   ├── DnsPacketParser.kt             # Raw IPv4/IPv6 and RFC 1035 packet parser
│   │   │   ├── DnsPacketBuilder.kt            # Synthesizer for A/AAAA/CNAME records & checksums
│   │   │   ├── SafeSearchManager.kt           # Google / YouTube / Bing / DDG VIP rewriter
│   │   │   └── DomainBlocklistMatcher.kt      # StevenBlack hosts in-memory hierarchical matcher
│   │   ├── accessibility/
│   │   │   ├── BrowserUrlExtractor.kt         # Address bar scraper & Incognito detector
│   │   │   ├── NsfwContentDetector.kt         # Adult terms, leetspeak, and query detector
│   │   │   └── SettingsTamperGuard.kt         # com.android.settings inspection & blocker
│   │   └── security/
│   │       └── TamperLockManager.kt           # RFC 6238 TOTP engine & monotonic cooldown
│   ├── receiver/
│   │   ├── ShieldDeviceAdminReceiver.kt       # Device Administrator lifecycle receiver
│   │   └── BootCompletedReceiver.kt           # Boot completed system start receiver
│   ├── vpn/
│   │   ├── DnsBlockerVpnService.kt            # High-performance local TUN DNS sinkhole
│   │   └── ShieldVpnService.kt                # Service alias
│   ├── accessibility/
│   │   └── ShieldAccessibilityService.kt      # Accessibility window inspection service
│   └── ui/
│       ├── overlay/
│       │   ├── SecurityWarningOverlayActivity.kt # Translucent Compose warning overlay
│       │   └── OverlayManager.kt              # Debounced overlay trigger
│       ├── qr/
│       │   └── QrCodeGenerator.kt             # Pure-Kotlin QR matrix generator & Canvas view
│       ├── screens/
│       │   ├── DashboardScreen.kt             # Hero defense status & live telemetry
│       │   ├── OnboardingScreen.kt            # Restricted Settings bypass & permissions guide
│       │   └── PartnerSetupScreen.kt          # Friend QR code pairing & cooldown timer
│       └── theme/
│           ├── Color.kt                       # ZevBlock security palette
│           ├── Theme.kt                       # Material Design 3 Dark Theme
│           └── Type.kt
└── res/
    ├── xml/
    │   ├── accessibility_service_config.xml   # Accessibility window configuration
    │   └── device_admin_rules.xml             # Device administrator policy declarations
    └── values/
        └── strings.xml                        # Localized labels and string resources
```

---

## 🔒 Privacy & Security

- **100% On-Device:** All blocklist evaluation, browser scraping, and SafeSearch VIP rewriting happen directly in local device memory.
- **Zero Cloud Leakage:** No browsing logs, query metadata, or device analytics are ever sent to remote servers.
- **Open-Source:** Fully auditable Kotlin codebase.
