# ZevBlock: Smooth Features & Fluid Dashboard Experience

Comprehensive architectural blueprint to introduce modern spring-physics transitions, an animated real-time network telemetry visualizer, rolling numeric counters, and fluid interactive feedback across ZevBlock.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following directions were confirmed during interactive clarification and form the foundation of this implementation:

- **Confirmed Decision 1 (Feature Focus)**: UI animations and fluid dashboard interactions as the primary polish focus.
- **Confirmed Decision 2 (Motion Style)**: Modern spring physics (`spring(dampingRatio, stiffness)`) and subtle alpha crossfades, avoiding harsh linear cuts.
- **Confirmed Decision 3 (Telemetry Visualizer)**: Live real-time network traffic wave with smooth sinusoidal multi-layer Canvas rendering and rolling number tickers for queries and blocked threat counts.
- **Confirmed Decision 4 (Zero-Latency Guarantee)**: Telemetry animations run purely on hardware-accelerated draw passes (`Modifier.drawWithCache` / `Canvas`) with decoupled coroutine sampling so network DNS packet parsing and Accessibility filtering are never delayed or bottlenecked by UI frame rendering.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades ZevBlock from a utilitarian security tool into a fluid, responsive defense center. Navigation transitions between screens (Dashboard, Rules, Partner, Setup, Tools) glide with custom spring physics and directional fades. Dashboard metrics roll upward smoothly with animated numeric tickers. A new live network telemetry wave provides continuous, ambient visual feedback of active DNS filtering and threat interception.
- **Target Audience / Persona**: Users seeking personal accountability and digital well-being who want a polished, tactile application that feels alive and reassuring rather than rigid or clunky.
- **Key Value**: Provides clear, aesthetically pleasing visual proof that active protection is working 24/7 without creating battery drain or UI jank.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Fluid Tab Switching**:
   - Tapping bottom navigation tabs smoothly glides the content container using `AnimatedContent` with spring-weighted scale and fade transitions. Active navigation pills smoothly expand with spring damping.
2. **Ambient Network Telemetry Wave**:
   - The dashboard features an animated real-time activity canvas under the Hero Shield: dual-layer harmonic sine waves pulsating at variable frequency based on live query rates (calm ambient ripples at idle, dynamic reactive crests when queries or block events occur).
3. **Rolling Counter Tickers**:
   - DNS rule counts, all-time blocks, today's blocks, and active streak stats smoothly roll up using Compose `animateIntAsState` with spring curves whenever values increment.
4. **Interactive Card Spring Physics**:
   - Defense module cards and lockdown action buttons employ spring-animated touch compression (`Modifier.scale` on press) with tactile feedback, making every state toggle feel immediate and tangible.
5. **Staggered Activity Feed Ingress**:
   - Intercepted domain events in the live activity feed smoothly transition in with spring fade-and-expand animations (`AnimatedVisibility`) rather than abruptly jumping into the list.

### Visual Identity & Theme

- **Color Palette & Atmospheric Glow**:
  - Dominant Background: Deep Tactical Navy (`#0A0F1D`)
  - Surface Elevation: Semi-translucent layered obsidian (`#121829`, `#182238`)
  - Accent Pulses: Cybernetic Emerald (`#10B981`), Electric Teal (`#06B6D4`), Warning Amber (`#F59E0B`), Threat Crimson (`#EF4444`)
  - Telemetry Wave: Dual-tone glowing gradient (`Brush.verticalGradient(listOf(ShieldTeal.copy(0.4f), ShieldGreen.copy(0.05f)))`)
- **Typography & Visual Rhythm**:
  - Monospace telemetry tags for raw counts and status badges (`letterSpacing = 1.sp`)
  - Bold sans-serif display headers for defense readiness ratings
  - High-contrast, scannable data hierarchy with 8dp grid spacing

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: AnimatedContent vs. Fragment Navigation**
  - *Chosen Approach*: Jetpack Compose `AnimatedContent` wrapping `selectedTab` with spring physics (`spring(dampingRatio = 0.82f, stiffness = 380f)`).
  - *Why*: Provides fluid transitions while maintaining the lightweight, instant state model of `MainActivity` without introducing external routing overhead or navigation state fragmentation.
  - *Alternatives Considered*: Standard NavHost with string routes (adds unnecessary serialization overhead for local state switching).
- **Decision 2: Hardware-Accelerated Canvas for Telemetry Waves**
  - *Chosen Approach*: Single `Canvas` with phase offset driven by `rememberInfiniteTransition` and amplitude dynamically adjusted by query event frequencies via StateFlow.
  - *Why*: Generates 60/120fps fluid fluid dynamics at near-zero CPU cost by delegating bezier path calculations directly to the GPU shader pipeline.
  - *Alternatives Considered*: Lottie animations (static, cannot respond dynamically to actual live network queries or blocked domains).
- **Decision 3: Decoupled Rolling Numeric Animators**
  - *Chosen Approach*: Self-contained `RollingCounterText` composable utilizing `animateIntAsState(targetValue, animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f))`.
  - *Why*: Isolates recomposition strictly to the numeric text element without triggering recomposition of parent cards or list containers.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                       MainActivity                           │
│  - SelectedTab State (0..4)                                  │
│  - Bottom Navigation Bar with Spring Active Indicators       │
└──────────────────────────────┬───────────────────────────────┘
                               │
            ┌──────────────────▼──────────────────┐
            │   AnimatedContent (Spring + Fade)   │
            └──────┬──────────┬──────────┬────────┘
                   │          │          │
      ┌────────────▼──┐ ┌─────▼────┐ ┌───▼───────────┐
      │DashboardScreen│ │Rules/Part│ │ Setup / Tools │
      └───────┬───────┘ └──────────┘ └───────────────┘
              │
  ┌───────────┴───────────────────────────────┐
  │                                           │
┌─▼──────────────────────┐         ┌──────────▼───────────────┐
│ LiveTelemetryWave      │         │ RollingCounterMetricBox  │
│ - Harmonic Dual Sine   │         │ - Spring Int Interpolate │
│ - Dynamic Amplitude    │         │ - Isolated Recomposition │
│ - Gradient Fill Shader │         └──────────────────────────┘
└────────────────────────┘
```

### Component & State Mapping

1. **`LiveTelemetryWave` (`ui/components/LiveTelemetryWave.kt`)**:
   - Harmonic wave generator computing $y = A \sin(\omega x + \phi) + B \sin(2\omega x + 1.3\phi)$.
   - Observes query bursts: when `DnsBlockerVpnService.recentLogs` receives new entries, wave amplitude transiently excites and settles back via damping curves.
2. **`AnimatedScreenSwitcher` (`MainActivity.kt`)**:
   - Replaces raw `when (selectedTab)` with `AnimatedContent` specifying slide-in-horizontally and spring-alpha transitions based on tab index comparison.
3. **`RollingCounter` (`ui/components/RollingCounter.kt`)**:
   - Reusable animated metric text displaying formatted numbers that glide smoothly when counters change.
4. **`InteractiveSpringCard` (`ui/components/InteractiveSpringCard.kt`)**:
   - Reusable card wrapper applying spring press-down animations (`scale` 0.97f on press, 1.0f on release) with ripple effects.

---

## Implementation Steps & Verification Plan

1. **Step 1**: Create `ui/components/RollingCounter.kt` and `ui/components/LiveTelemetryWave.kt`.
2. **Step 2**: Enhance `DashboardScreen.kt` with the live telemetry wave, spring-animated metric cards, and smooth list item animations.
3. **Step 3**: Upgrade `MainActivity.kt` tab switching to use `AnimatedContent` with custom spring transitions and edge-to-edge navigation insets.
4. **Step 4**: Verify compilation via `compile_applet` and run existing Robolectric tests to ensure zero regressions.
