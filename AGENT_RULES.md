# AI AGENT RULES & PROJECT BRAIN DIRECTIVE

> **MANDATORY INSTRUCTION FOR ALL AI AGENTS (Cursor, Windsurf, Claude Code, GitHub Copilot, Antigravity, ChatGPT, etc.)**

Welcome to **ZevBlock**. Before analyzing, modifying, or generating code in this repository, you **MUST** review and adhere to the protocol defined below.

---

## 🧭 PHASE 1: MANDATORY INITIALIZATION (READ BEFORE CODING)

Before writing any code or proposing modifications, you must read the **`brain/`** directory located at the project root:

1. **`brain/README.md`**: Project overview, core purpose, navigation, and AI guidelines.
2. **`brain/ARCHITECTURE.md`**: Architecture layers (DNS sinkhole, Accessibility guard, Device Owner, UI).
3. **`brain/TECH_STACK.md`**: Android SDK, Kotlin, Compose, Gradle build targets, and testing commands.
4. **`brain/SYSTEM_STATE.md`**: Current system status, active features, and known constraints.
5. **`brain/RULES.md`**: Non-negotiable security constraints, privacy rules, and code patterns.
6. **`brain/CHANGELOG.md`**: Historical record of changes made by human developers and AI assistants.

---

## 🔒 PHASE 2: THE "CHANGE LOCK" PROTOCOL (MANDATORY ON EVERY TASK)

Every single AI session that modifies the codebase must execute the following **Change Lock Protocol** before finishing:

### 1. Register Every Modification in `brain/CHANGELOG.md`
Prepend a new entry under `## [Unreleased]` or with a current timestamp at the top of the log:

```markdown
## [YYYY-MM-DD HH:MM] - <Short Summary of Changes>
- **AI Agent / Model**: <e.g., Claude 3.5 Sonnet, GPT-4o, Antigravity>
- **Objective**: <Why was this change requested?>
- **Files Touched**:
  - `app/src/main/.../File.kt` - Description of changes made
- **Key Changes & Logic Impact**:
  - Summary of logic changes or newly added capabilities
- **Security & Performance Audit**:
  - Checked for battery drain, memory leaks, or anti-bypass bypasses
- **Verification**:
  - Build or test commands executed (e.g., `./gradlew testDebugUnitTest`)
- **Next Steps / Open Items**:
  - Anything left unfinished or requiring attention from the next agent
```

### 2. Synchronize Project Documentation
- **Architecture changes**: If classes are added, removed, or refactored, update `brain/ARCHITECTURE.md`.
- **Status changes**: If a feature is completed or a new bug/limitation is discovered, update `brain/SYSTEM_STATE.md`.
- **Dependencies or SDK changes**: Update `brain/TECH_STACK.md`.
- **New constraints or policies**: Update `brain/RULES.md`.

---

## 🚫 ZERO-TOLERANCE CODEBASE RULES
1. **Never Bypass Security Guards**: ZevBlock is a hardened adult-content blocker and anti-tamper tool. Never weaken or bypass `SettingsTamperGuard`, `TamperLockManager`, or `DeviceAdminManager`.
2. **Zero Remote Telemetry**: ZevBlock is strictly 100% on-device. Never introduce external tracking, third-party analytics, or telemetry APIs without explicit user instruction.
3. **Battery & Performance Efficiency**: DNS filtering must remain lightweight on port 53; Accessibility node inspection must throttle event processing to prevent device slowdown.
4. **No Silent Changes**: Code edits without an accompanying `brain/CHANGELOG.md` update are considered protocol violations.
