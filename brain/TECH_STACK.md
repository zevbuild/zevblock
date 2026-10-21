# ZevBlock Technology Stack & Build Tools 🛠️

This document details the languages, SDK targets, build configurations, dependencies, and execution commands for **ZevBlock**.

---

## 💻 Core Specifications

| Component | Target / Version | Notes |
| :--- | :--- | :--- |
| **Platform** | Android 7.0+ (API 24 to 36) | `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36.1` |
| **Language** | Kotlin 2.2.10 | Target JVM bytecode: Java 11 (`JavaVersion.VERSION_11`) |
| **UI Framework** | Jetpack Compose + Material 3 | Compose BOM `2024.09.00` |
| **Build System** | Gradle (Kotlin DSL `.gradle.kts`) | AGP (Android Gradle Plugin) `9.1.1` |
| **Packaging Version** | VersionCode `13`, VersionName `2.2` | Defined in `app/build.gradle.kts` |

---

## 📦 Key Dependencies & Libraries

- **UI & Layout**:
  - `androidx.compose.material3:material3`
  - `androidx.compose.material:material-icons-extended`
  - `androidx.activity:activity-compose:1.10.1`
  - `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`
  - `androidx.lifecycle:lifecycle-runtime-compose:2.8.7`
- **Concurrency**:
  - `kotlinx-coroutines-core:1.10.2`
  - `kotlinx-coroutines-android:1.10.2`
- **Secrets Management**:
  - `com.google.android.libraries.mapsplatform.secrets-gradle-plugin:2.0.1` (reads `.env` / `.env.example`)
- **Testing & Verification**:
  - `junit:junit:4.13.2`
  - `androidx.test.ext:junit:1.3.0`
  - `org.robolectric:robolectric:4.16.1`
  - `kotlinx-coroutines-test:1.10.2`
  - `androidx.compose.ui:ui-test-junit4`

---

## ⚙️ Build & Test Commands

Run these commands from the root directory of the workspace:

### 1. Compile & Build APKs
- **Debug Build**:
  ```bash
  # Windows PowerShell
  .\gradlew.bat assembleDebug

  # Linux / macOS / Bash
  ./gradlew assembleDebug
  ```
  *Output APK location*: `app/build/outputs/apk/debug/app-debug.apk`

- **Release Build** (requires keystore environment variables or fallback configuration):
  ```bash
  .\gradlew.bat assembleRelease
  ```

### 2. Run Automated Unit Tests
```bash
# Run all local JVM unit tests (Robolectric + JUnit)
.\gradlew.bat testDebugUnitTest --continue
```

### 3. Clean Build Cache
```bash
.\gradlew.bat clean
```

---

## 🔐 Environment & Signing Configuration

- **Secrets Plugin**: Configured to read `.env` (template located at `.env.example`).
- **Signing Configurations**:
  - `debugConfig`: Uses default Android debug key at root `debug.keystore`.
  - `release`: Configured to use environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) or fallback `my-upload-key.jks`.
