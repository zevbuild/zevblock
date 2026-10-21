# ZevBlock Startup Crash Fix & High-Speed Performance Plan

Eliminate the immediate startup and background service crashes in ZevBlock, offload heavy initialization from the main UI thread for instant app launch, replace heavy continuous animations with lightweight battery-friendly motion, and introduce a self-healing Crash Guard with a live diagnostic log viewer in the Tools tab.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX choices incorporate your confirmed preferences from our diagnostic check:

- **Confirmed Decision 1 — Immediate App Launch Crash Elimination**: Focus primary fixes on the exact startup path when tapping the app icon—specifically moving heavy regex/blocklist/history initialization off the UI thread, converting the Dashboard to lazy virtualized rendering, and fixing the `BootCompletedReceiver` / `DnsBlockerVpnService` foreground service crash loop on app install/launch.
- **Confirmed Decision 2 — Fast Lightweight Animations (Minimal Battery Use)**: Replace the 7 concurrent 60fps infinite animations and per-character rolling digit state machines with lightweight, hardware-accelerated transitions and pre-allocated Canvas paths that only animate when active VPN traffic is flowing.
- **Confirmed Decision 3 — Silent Auto-Recovery & Tools Crash Diagnostics**: Add a built-in `CrashGuardManager` (`SupervisorJob` + `CoroutineExceptionHandler` + uncaught exception guard) that automatically recovers background services without crashing the app and records detailed diagnostic logs in the **Tools** tab.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms ZevBlock from a heavy, synchronous-startup app into an instant-opening, crash-resilient protection suite. Tapping the ZevBlock icon renders the UI shell immediately while blocklists, regex engines, 365-day streak calculations, and Device Policy checks load asynchronously in the background.
- **Target Audience / Persona**: Users relying on 24/7 anti-pornography and anti-bypass protection who need the app to open instantaneously without freezes, ANRs (Application Not Responding), or unexpected foreground service crashes.
- **Key Value**:
  - **Zero Startup Crashes**: Eliminates `ForegroundServiceDidNotStartInTimeException`, `ForegroundServiceStartNotAllowedException`, and main-thread ANRs.
  - **Sub-100ms Cold Launch**: Moves 100,000-capacity hash set allocations, 30+ regex compilations, and 365-day streak calculations off the Main UI thread.
  - **Smooth 60fps Scrolling**: Replaces the monolithic Dashboard scroll view with lazy on-demand composition (`LazyColumn`) and zero-allocation Canvas drawing.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Instant App Launch Flow**: User taps the ZevBlock launcher icon -> Main scaffold and Dashboard header appear immediately with zero main-thread blocking -> Live protection metrics, 7-day bar chart, and telemetry stream populate smoothly within milliseconds via reactive state flows.
  2. **Resilient VPN & Notification Flow**: User starts or stops the DNS Sinkhole VPN -> Service verifies VPN permission first, wraps `startForeground()` in full exception guards, and updates the live Download (`↓`) / Upload (`↑`) speed notification without risking foreground service timeout exceptions.
  3. **Self-Healing Diagnostics Flow (Tools Tab)**: User opens the **Tools** tab -> Views the new **Crash Guard & Performance Diagnostics** card showing app startup time (`ms`), memory health, service recovery status, and any caught errors with a one-tap copy/clear option.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Tactical, high-contrast cybersecurity dashboard ("Atmospheric Minimalism & High-Craft Dark UI").
  - *Color Palette & Mood*:
    - `--background`: Deep Obsidian Navy (`#0A0F1D`)
    - `--surface`: Elevated Slate Navy (`#121829`) and Card Surface (`#1B243B`)
    - `--primary`: Emerald Shield Green (`#00E676`) for active defense and download telemetry
    - `--secondary`: Cyber Teal (`#00B0FF`) for SafeSearch VIP and upload telemetry
    - `--accent` / `--warning`: Amber Alert (`#FFB300`) and Crimson Block (`#FF5252`)
  - *Typography & Hierarchy*: Bold geometric sans-serif headers paired with tabular `FontFamily.Monospace` for live network speeds (`↓`/`↑`), DNS latency (`ms`), and diagnostic stack traces.
  - *Component Styling & Layout*: Material Design 3 dark surfaces with 1dp subtle borders (`#2B3757`), 16dp rounded cards, and generous 16dp spacing rhythm.

- **Interactive Feedback & Motion**:
  - **Lightweight Battery-Friendly Motion**:
    - The Telemetry Wave Canvas reuses pre-allocated `Path` objects and pauses continuous wave animation when the VPN is idle, cutting GPU/CPU frame work by over 75%.
    - Counter metrics use lightweight crossfade/numeric transitions instead of spawning dozens of simultaneous per-character `AnimatedContent` containers during initial screen composition.
    - Status dots use static glowing rings when idle and a single shared pulse rhythm when active.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Asynchronous Background Initialization vs. Synchronous `remember {}` Startup**
  - *Chosen Approach*: Initialize `DomainBlocklistMatcher`, `NsfwContentDetector` regex pre-compilation, `ProtectionStatsManager` 365-day streak calculation, and `DeviceAdminManager` status checks on `Dispatchers.Default` / `Dispatchers.IO`, exposing results via `StateFlow`.
  - *Why*: Previously, all four engines initialized synchronously inside `MainActivity` composition on the UI thread, causing severe frame drops and startup crashes/ANRs when opening the app icon.
  - *Alternatives Considered*: Keeping synchronous initialization with smaller seed lists (rejected because full adult blocklist coverage must remain intact).

- **Decision 2: Strict Foreground Service Lifecycle & Permission Guard**
  - *Chosen Approach*:
    1. Always call `startForeground()` at the very top of `DnsBlockerVpnService.onStartCommand()` before any early return (even if `_isRunning.value` is already `true`), wrapped in a safe `try/catch` block.
    2. Check `VpnService.prepare(context) == null` in `BootCompletedReceiver` before attempting to auto-start the VPN service on boot or package update, and use `START_NOT_STICKY` when VPN permission is missing so Android never enters a crash-restart loop.
  - *Why*: Android 12–15 strictly enforces a 5-second `startForeground()` contract whenever `startForegroundService()` is called. Returning early when `_isRunning` is true—or starting the service from `MY_PACKAGE_REPLACED` when VPN permission is not granted—causes fatal `ForegroundServiceDidNotStartInTimeException` and `ForegroundServiceStartNotAllowedException` crashes.
  - *Alternatives Considered*: Removing `BootCompletedReceiver` entirely (rejected because auto-resuming protection after device reboot is an important safeguard once VPN permission is granted).

- **Decision 3: Virtualized `LazyColumn` Dashboard with Zero-Allocation Wave Canvas**
  - *Chosen Approach*: Convert the Dashboard layout from a monolithic `Column(Modifier.verticalScroll())` to a virtualized `LazyColumn` with stable item keys, and pre-allocate `Path` objects outside the `Canvas` draw loop.
  - *Why*: A non-lazy `Column` forces Jetpack Compose to measure, layout, and draw every card, chart, and log item off-screen during the very first frame of app startup. `LazyColumn` composes only visible viewport items, making app opening near-instantaneous.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        ZevBlock Application Process                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  CrashGuardManager (Global Exception Handler & Diagnostic Store)    │  │
│  │  • Captures uncaught UI/background exceptions & service recoveries  │  │
│  │  • Exposes StateFlow<List<CrashDiagnosticEntry>> & startup timing   │  │
│  └──────────────────┬───────────────────────────────┬──────────────────┘  │
│                     │                               │                     │
│                     ▼                               ▼                     │
│  ┌──────────────────────────────────┐  ┌───────────────────────────────┐  │
│  │   MainActivity & Compose UI      │  │   Background Guard Services   │  │
│  │  • Instant Non-Blocking Launch   │  │  • DnsBlockerVpnService       │  │
│  │  • Async Engine Hydration        │  │    - SupervisorJob + IO Scope │  │
│  │    (Dispatchers.Default / IO)    │  │    - Guaranteed startForeground│ │
│  │  • Virtualized LazyColumn        │  │    - Safe TrafficStats Loop   │  │
│  │    Dashboard                     │  │  • ShieldAccessibilityService │  │
│  │  • Lightweight Motion & Pre-     │  │    - Background Regex Engine  │  │
│  │    allocated Wave Canvas         │  │    - Safe Node Recycle Guards │  │
│  │  • Tools Tab: Crash & Health     │  │  • BootCompletedReceiver      │  │
│  │    Diagnostic Console            │  │    - VpnService.prepare Check │  │
│  └──────────────────────────────────┘  └───────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - **`CrashGuardManager`**:
    - `diagnostics: StateFlow<List<DiagnosticEntry>>`: Stores timestamped error/recovery events (`id`, `timestamp`, `source`, `summary`, `stackTrace`, `autoRecovered`).
    - `lastStartupDurationMs: StateFlow<Long>`: Tracks cold/warm UI launch time in milliseconds.
  - **Singleton Shared Engines**:
    - Single shared `NsfwContentDetector` instance initialized lazily off the main thread instead of instantiating duplicate regex maps across `MainActivity`, `CustomShieldRuleManager`, and `ShieldAccessibilityService`.
    - `ProtectionStatsManager` & `CustomShieldRuleManager` load `SharedPreferences` and compute 365-day streaks asynchronously on a background coroutine scope (`SupervisorJob() + Dispatchers.IO`).

- **Interactive Component & State Mapping**:
  - **App Launch (`MainActivity` -> `ShieldGuardApp`)**:
    - Renders top bar, bottom navigation, and `LazyColumn` Dashboard immediately using fast default states.
    - Background coroutine hydrates `DeviceAdminStatus`, `ProtectionStatsManager`, `CustomShieldRuleManager`, and triggers a single rate-limited `AppUpdateManager.checkForUpdate()` call once per session (instead of re-running on every tab switch).
  - **VPN Service Lifecycle (`DnsBlockerVpnService`)**:
    - Uses `CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuardExceptionHandler)` so an individual DNS socket timeout or malformed packet never cancels the service scope or crashes the app.
    - Guarantees `startForeground()` is called immediately on every `ACTION_START` intent and safely stops foreground state before `stopSelf()`.
  - **Tools Tab Diagnostic Console (`ToolsSimulatorScreen`)**:
    - Adds an interactive **Crash Guard & System Health** card displaying startup speed, memory usage, active service health, and a list of any caught/recovered exceptions with **Copy Report** and **Clear Logs** actions.
