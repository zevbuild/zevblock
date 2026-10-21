# ZevBlock Project Brain 🧠

> **Single Source of Truth (SSOT) & AI Knowledge Base for ZevBlock**

Welcome to the central intelligence hub of **ZevBlock**. This directory is structured to provide any AI assistant or human engineer with an immediate, deep understanding of the project's purpose, design patterns, security rules, and active state.

---

## 📌 What is ZevBlock?

**ZevBlock** is a hardened, battery-efficient Android protection and accountability platform designed to eliminate pornography and adult material at both the **network layer** and **screen/system layer**.

### Core Value Proposition:
1. **Network-Level DNS Sinkhole (`DnsBlockerVpnService`)**:
   - Intercepts port 53 DNS queries only (battery-friendly split routing; normal data runs at full wire speed).
   - Enforces strict SafeSearch VIP rewrites across Google, YouTube, Bing, DuckDuckGo, Yahoo, Brave, and Qwant.
   - Built-in adult CDN sinking (`phncdn.com`, `xhcdn.com`, etc.) and Cloudflare Family (`1.1.1.3`) / CleanBrowsing upstream fallback.
2. **Screen-Level Accessibility Guard (`ShieldAccessibilityService`)**:
   - Universal URL and address bar scraping across 30+ mobile browsers.
   - On-screen NSFW keyword and regex classifier (`NsfwContentDetector`) with boundary matching to eliminate false positives.
   - Settings anti-tamper guard (`SettingsTamperGuard`) preventing uninstallation, clearing data, or disabling protections.
   - Non-intrusive chat guard for messaging apps with throttled heads-up warnings.
3. **Anti-Bypass Accountability OS (`DeviceAdminManager`, `TamperLockManager`)**:
   - Device Policy Manager integration (`DISALLOW_CONFIG_PRIVATE_DNS`, `DISALLOW_INSTALL_UNKNOWN_SOURCES`, always-on VPN lockdown).
   - Offline cryptographic 2-person rule (HMAC-SHA256 partner QR code sync and unlock PIN).
4. **100% Privacy & Zero Remote Telemetry**:
   - Entirely on-device processing. No external servers or telemetry trackers.

---

## 🗺️ Project Directory Map

```text
zevblock/
├── AGENT_RULES.md             # AI operational mandate & Change Lock rules
├── .cursorrules               # Cursor AI integration rules
├── .github/
│   ├── copilot-instructions.md# GitHub Copilot directives
│   └── workflows/build.yml    # Automated CI/CD for release APK builds
├── app/
│   ├── build.gradle.kts       # App module dependencies, SDK targets, signing
│   └── src/main/
│       ├── AndroidManifest.xml# Permissions, services, receivers, activities
│       ├── java/com/zevbuild/zevblock/
│       │   ├── MainActivity.kt                # Jetpack Compose UI host
│       │   ├── accessibility/                 # ShieldAccessibilityService
│       │   ├── core/
│       │   │   ├── accessibility/             # URL extraction, NSFW detection, TamperGuard
│       │   │   ├── admin/                     # DeviceAdminManager
│       │   │   ├── diagnostics/               # CrashGuardManager
│       │   │   ├── dns/                       # DNS packet parser/builder, matchers, SafeSearch
│       │   │   ├── rules/                     # Custom user domain rules
│       │   │   ├── security/                  # TamperLockManager (cryptographic partner lock)
│       │   │   ├── stats/                     # Block counter & protection telemetry
│       │   │   └── updater/                   # GitHub Releases OTA updater
│       │   ├── receiver/                      # Boot & Device Admin receivers
│       │   └── ui/                            # Jetpack Compose UI (screens, theme, components)
│       └── res/                               # Layouts, XML configs, drawables, strings
├── brain/                                     # 🧠 Project Brain (You are here)
│   ├── README.md                              # This file
│   ├── ARCHITECTURE.md                        # Detailed architecture & flows
│   ├── TECH_STACK.md                          # Versions, tools, commands
│   ├── SYSTEM_STATE.md                        # Feature status & active tasks
│   ├── RULES.md                               # Security policies & code standards
│   └── CHANGELOG.md                           # Change Lock ledger
```

---

## ⚡ Quick Protocol for AI Agents

1. **Orientation**: Read `ARCHITECTURE.md` to understand component interactions before modifying code.
2. **Constraints**: Check `RULES.md` to ensure your change doesn't violate anti-bypass, battery, or privacy constraints.
3. **Change Lock**: When finishing any work, **always add an entry to `CHANGELOG.md`** and update relevant `brain/` docs.
