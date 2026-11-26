# ZevBlock System Architecture 🏛️

This document describes the architectural layout, core design patterns, component interactions, and data flows of **ZevBlock**.

---

## 🏗️ High-Level Architectural Diagram

```
                             +-----------------------------+
                             |       User / System         |
                             +-----------------------------+
                                       |            |
                    Network Queries    |            | Screen Node Events
                           v                        v
            +---------------------------+   +-------------------------------+
            |    DnsBlockerVpnService   |   |   ShieldAccessibilityService  |
            |     (Virtual Split VPN)   |   |   (Address Bar & Node Scraper)|
            +---------------------------+   +-------------------------------+
                      |            |                       |           |
              Allowed |            | Blocked / SafeSearch  | Match     | Settings
                      v            v                       v           v
           +----------------+ +---------------+     +---------+ +--------------------+
           | Cloudflare 1.1 | | VIP Rewrite / |     | Overlay | | SettingsTamperGuard|
           | CleanBrowsing  | | Sinkhole 0.0  |     | Warning | | (Intercept Back)   |
           +----------------+ +---------------+     +---------+ +--------------------+
                                       ^                        ^
                                       |                        |
            +---------------------------------------------------------------+
            |            DeviceAdminManager & TamperLockManager             |
            |        (DPM Policies, Offline HMAC PIN, Partner Lock)         |
            +---------------------------------------------------------------+
                                       ^
                                       | Reads / Configures
            +---------------------------------------------------------------+
            |                     Jetpack Compose UI                        |
            |       (Dashboard, Onboarding, Rules, Diagnostics)            |
            +---------------------------------------------------------------+
```

---

## 🛡️ Core Defensive Layers

### 1. Network Layer: Virtual Split DNS Sinkhole
- **Key Service**: `com.zevbuild.zevblock.vpn.DnsBlockerVpnService`
- **Mechanism**:
  - Sets up an Android `VpnService` with virtual IP `10.254.1.1/32` (and `fd00::1/128`).
  - Routes **only port 53** traffic through the TUN interface. All standard TCP/UDP payload traffic flows direct via cellular/Wi-Fi interfaces without overhead.
- **Sub-components**:
  - `DnsPacketParser`: Parses raw UDP datagrams into structured DNS queries (domain name, record type).
  - `DomainBlocklistMatcher`: Evaluates requested domains using hierarchical label suffix matching against top adult CDNs and known adult domains.
  - `SafeSearchManager`: Intercepts queries to Google, YouTube, Bing, Yahoo, DuckDuckGo, Brave, and Qwant, returning hardcoded SafeSearch VIP A/AAAA records.
  - `DnsPacketBuilder`: Constructs standard DNS response packets (`A 0.0.0.0` or VIP answers) directly in-memory.
  - **Upstream Forwarding**: Allowed queries are forwarded asynchronously via coroutines to Cloudflare Family (`1.1.1.3`) or CleanBrowsing Adult Filter (`185.228.168.10`).

### 2. Screen & App Inspection Layer: Accessibility Shield
- **Key Service**: `com.zevbuild.zevblock.accessibility.ShieldAccessibilityService`
- **Mechanism**:
  - Intercepts `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_WINDOW_STATE_CHANGED`.
  - Maintains strict node traversal depth limits and debounce timers to avoid UI stutter or battery consumption.
- **Sub-components**:
  - `BrowserUrlExtractor`: Recognizes address bar view IDs across 30+ popular Android browsers (Chrome, Firefox, Brave, Samsung Internet, Opera, Kiwi, etc.) and extracts the current URL.
  - `NsfwContentDetector`: Performs regex-based matching on extracted URLs, search parameters (`?q=`, `&search_query=`), and raw on-screen text. Uses word boundary boundaries (`\b`) to eliminate false positives on benign words (e.g., "essex", "analysis").
  - `SettingsTamperGuard`: Detects navigation within `com.android.settings`. If user attempts to enter App Info, force-stop ZevBlock, or disable Accessibility/Device Admin privileges, it immediately dispatches `GLOBAL_ACTION_BACK` or navigates home.

### 3. Device Governance & Anti-Tamper Layer
- **Key Classes**:
  - `com.zevbuild.zevblock.core.admin.DeviceAdminManager`
  - `com.zevbuild.zevblock.receiver.ShieldDeviceAdminReceiver`
  - `com.zevbuild.zevblock.core.security.TamperLockManager`
- **Mechanism**:
  - Integrates with Android `DevicePolicyManager`.
  - Supports Device Owner policies:
    - `DISALLOW_CONFIG_PRIVATE_DNS`: Prevents user from setting DoT (Private DNS) to bypass the local VPN sinkhole.
    - `DISALLOW_SAFE_BOOT`: Prevents booting into safe mode to disable security services.
    - `DISALLOW_INSTALL_UNKNOWN_SOURCES`: Blocks sideloading APKs.
    - Always-On VPN lockdown.
  - `TamperLockManager`: Implements offline partner accountability using HMAC-SHA256 tokens and QR codes. Disabling features requires a cryptographic partner signature or an enforced delay timer.

### 4. Presentation Layer: Jetpack Compose UI
- **Activity**: `com.zevbuild.zevblock.MainActivity`
- **UI Architecture**: Single Activity, declarative Jetpack Compose UI with `ZevBlockTheme` (Slate Dark default & Calm Light support) and a 4-tab Material 3 navigation architecture (`Shield`, `Rules`, `Partner`, `Settings`) with `ON_RESUME` permission auto-sync.
- **Key Screens & Modals**:
  - `DashboardScreen` ("Shield"): Honest independent status for VPN, Accessibility, Device Hardening (Standard vs Admin vs Device Owner), interactive 7-day momentum breakdown, and recovery banner when services are interrupted.
  - `CustomRulesScreen` ("Rules"): `LazyColumn` management of custom domains & keywords, live 24h cooldown countdowns, and a read-only `Baseline & Lookup` explorer (`Why was X blocked?`).
  - `PartnerSetupScreen` ("Partner"): Spec-compliant ISO/IEC 18004 QR & authenticator deep-link pairing wizard, Vault-locked secret storage once verified, segmented 6-digit OTP input, live brute-force lockout feedback, temporary bypass window, and monotonic hardware cooldown unlocks.
  - `SettingsScreen` & `GuidedSetupSheet`: Guard toggle controls (Partner-gated when reducing protection), notification display style, privacy/limits disclosures, OTA updates, guided setup wizard (Android 13+ Restricted Settings, Battery Keep-Alive, Notifications, ADB/Shizuku Device Owner), and collapsible Developer & QA Lab.
  - `LiveDnsTelemetryModal` / `NetworkTelemetryWaveCard`: On-demand RAM-only stream with pause/clear privacy controls and real upstream latency metrics.
  - `OverlayManager` / `SecurityWarningOverlayActivity`: Calm recovery-focused intervention overlay displaying rule category badges and streak encouragement.

### 5. Diagnostics & Lifecycle
- `BootCompletedReceiver`: Automatically reboots VPN and monitoring services when device restarts.
- `CrashGuardManager`: Global uncaught exception handler ensuring that service crashes fail safely and recover quickly.
- `AppUpdateManager`: Checks GitHub Releases API for OTA updates and informs the user when a new version is available.
