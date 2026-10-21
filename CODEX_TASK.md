# Codex task: harden ZevBlock security

Work only on branch `codex/zevblock-security-fixes`. Do not merge into `main`.
The goal is to fix the security findings below, preserve existing app behavior, add regression tests, and report any item that cannot be safely completed.

## Implementation Status

1. **[x] Partner TOTP replay and brute-force protection**
   - In `TamperLockManager`, rejected any TOTP time step `<= lastAcceptedStep`.
   - Atomically persisted `lastAcceptedStep` on successful verification.
   - Handled ±1-step clock window without accepting older/replayed steps.
   - Added persistent attempt throttling and exponential lockout (30s at 5 attempts, 5m at 10, 15m at 15).
   - Enforced strict 6-digit numeric validation; zero OTP or secret logging.
   - Added unit tests: `tamperLockManager_rejectsReplayedTotpCodes` & `tamperLockManager_throttlesRepeatedInvalidAttempts`.

2. **[x] Protect partner TOTP secret and integrity keys**
   - Created `CryptoKeyManager` utilizing Android Keystore (`AndroidKeyStore`) with authenticated AES-256-GCM.
   - Encrypted partner TOTP secrets and device integrity keys with Keystore keys.
   - Automated safe migration from legacy plaintext `SharedPreferences`.
   - Fails closed on decryption or migration failure (never silently clears secret or unlocks).
   - Added graceful software provider fallback for local JVM/Robolectric test runners.

3. **[x] Validate upstream DNS responses**
   - In `DnsBlockerVpnService.forwardUpstreamDns`, verified packet originates from exact contacted resolver IP and port 53.
   - Added `DnsPacketParser.validateDnsResponse(...)` strictly validating packet length >= 12, matching Transaction ID, QR=1 response flag, Opcode=0, and matching Question name/type/class.
   - Discards malformed, truncated, or spoofed replies and safely retries next configured upstream resolver in the pool.
   - Added unit test: `dnsPacketParser_validatesUpstreamDnsResponses`.

4. **[x] Make cooldown/bypass tamper protection meaningful**
   - Encrypted device integrity key using Android Keystore.
   - Enforced constant-time signature comparisons via `MessageDigest.isEqual(...)` to eliminate timing side-channels.
   - Added strict state bounds validation (duration, remaining, lastElapsed, lastWall); fails closed on corrupt/impossible state.
   - Added unit test: `tamperLockManager_failsClosedOnCorruptedState`.

5. **[x] Release and CI safety**
   - Removed `continue-on-error: true` in `.github/workflows/build.yml` so unit test failures block CI.
   - Maintained separation between development debug signing and production release signing in `app/build.gradle.kts`.
   - Zero credentials or generated secrets in repository.

6. **[x] DNS parser robustness**
   - In `DnsPacketParser`, added RFC 8200 IPv6 extension header bounds-checked traversal (Hop-by-Hop, Routing, Destination Options, Fragment, AH).
   - Validated IPv4 `totalLength` and UDP payload boundaries.
   - Safely rejects non-initial fragments and truncated packets returning `null` without crashing.
   - Added unit test: `dnsPacketParser_handlesIpv6ExtensionHeadersAndBounds`.

7. **[x] Privacy and documentation**
   - Updated `README.md` to transparently disclose:
     - Upstream DNS routing over port 53 to Cloudflare Family / CleanBrowsing / AdGuard.
     - GitHub API requests for update checks.
     - Technical limitations of DNS filtering (DoH, cached DNS, direct IP connections).
     - Android security model: Device Owner vs. standard Device Administrator capabilities.
