# ZevBlock System State & Active Roadmap 📊

This document reflects the current operational state, implemented features, verified capabilities, and pending tasks in **ZevBlock**.

---

## 🟢 Operational Status (Version 2.5 / Build 16)

| Component / Subsystem | Status | Description |
| :--- | :--- | :--- |
| **Virtual Split DNS Sinkhole** | ✅ Stable | Captures port 53 traffic on `10.254.1.1/32` & `fd00::1/128`. Routes non-DNS traffic directly. |
| **SafeSearch VIP Interception** | ✅ Stable | Enforces strict VIP rewriting for Google, YouTube, Bing, Yahoo, DDG, Brave, Qwant. |
| **Adult CDN Sinks** | ✅ Stable | Hierarchical in-memory suffix matching for 150+ adult CDN seeds (`phncdn`, `xhcdn`, etc.). |
| **Accessibility URL Scraper** | ✅ Stable | Extracts address bars for 30+ Android browsers via `BrowserUrlExtractor`. |
| **NSFW Content Detector** | ✅ Stable | Multilingual keywords, URL paths, query parameters (`?q=`, `&search_query=`), word-boundary regex. |
| **Settings Anti-Tamper Guard**| ✅ Stable | Intercepts attempts to force-stop or uninstall in `com.android.settings`. |
| **Chat Safeguard** | ✅ Stable | Monitors on-screen explicit text in chat apps; displays throttled 4-second warning toast/heads-up. |
| **Device Admin & Device Owner**| ✅ Stable | Implements `DeviceAdminManager` for anti-tamper, safe boot restriction, and always-on VPN. |
| **TamperLock / Partner Auth** | ✅ Stable | Offline cryptographic HMAC-SHA256 partner QR code verification & unlock mechanism. |
| **Jetpack Compose UI** | ✅ Stable | Dashboard, Onboarding wizard, Partner Setup, Custom Rules, Telemetry Wave Card. |
| **Boot Auto-Start** | ✅ Stable | Restarts VPN and protection services automatically on system boot (`BootCompletedReceiver`). |
| **Crash Protection & Updates**| ✅ Stable | `CrashGuardManager` recovery and GitHub Releases OTA checking via `AppUpdateManager`. |

---

## 🛠️ Known Operating Constraints & Requirements

1. **Device Owner Enrollment**:
   - Device Owner capabilities (`DISALLOW_CONFIG_PRIVATE_DNS`, `DISALLOW_SAFE_BOOT`) require ADB command enrollment on an unprovisioned/new device:
     ```bash
     adb shell dpm set-device-owner com.zevbuild.zevblock/.receiver.ShieldDeviceAdminReceiver
     ```
   - If not set as Device Owner, ZevBlock operates in standard Device Admin + Accessibility + VPN mode.
2. **Android 14/15/16 Background & Notification Policies**:
   - Foreground services require specific permissions (`FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `SYSTEM_ALERT_WINDOW`).
   - Battery optimization exemption must be requested in Onboarding to prevent OS killer daemons from terminating `DnsBlockerVpnService`.

---

## 📋 Active Tasks & Backlog for Incoming AI Agents

- [ ] **Dynamic Blocklist Syncing**: Add optional local caching mechanism for StevenBlack hosts list updates without requiring full APK upgrades.
- [ ] **Enhanced Telemetry Visualization**: Provide deeper historical statistics on daily blocked domains inside `ProtectionStatsManager` and Compose UI.
- [ ] **Multi-Language Accessibility Lexicon**: Expand language coverage for on-screen NSFW classifiers to additional regional languages.
