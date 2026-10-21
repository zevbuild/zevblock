# Codex task: harden ZevBlock security

Work only on branch `codex/zevblock-security-fixes`. Do not merge into `main`.
The goal is to fix the security findings below, preserve existing app behavior, add regression tests, and report any item that cannot be safely completed.

## Required fixes

### Implementation started
- Added persisted TOTP accepted-step tracking and a failed-attempt throttle (five failures in a five-minute window trigger a one-minute lockout), with regression tests.
- Added upstream DNS source IP/port checks and DNS response transaction/question/structure/truncation validation, with regression tests.
- These changes have not yet been verified by running Gradle in this environment. Keystore-backed secrets/keys, release signing, and broader packet parser work remain open.

1. **Partner TOTP replay and brute-force protection**
   - In `TamperLockManager`, reject a TOTP time step that has already been accepted.
   - Persist the last accepted time step atomically with successful authorization; handle the ±1-step clock window without accepting an older/replayed step.
   - Add a persistent attempt throttle/temporary lockout for invalid codes. Do not log submitted codes or secrets.
   - Add unit tests for replay, valid current/allowed-skew codes, invalid codes, and rate limiting.

2. **Protect partner TOTP secret and integrity keys**
   - Stop storing the partner TOTP secret and HMAC integrity key as plaintext in ordinary `SharedPreferences`.
   - Use Android Keystore-backed cryptography for secret storage and integrity keys, with safe migration from existing installs.
   - Never silently clear an existing partner pairing if migration/decryption fails; fail closed and explain recovery behavior.
   - Add tests for migration and failure paths where test infrastructure permits.

3. **Validate upstream DNS responses**
   - In `DnsBlockerVpnService.forwardUpstreamDns`, accept a response only from the exact resolver address and port contacted.
   - Validate DNS packet length, transaction ID, response bit, opcode, and matching question name/type/class before forwarding.
   - Ignore malformed or mismatched replies and try the next configured resolver. Bound retries/timeouts.
   - Add regression tests for mismatched IDs, wrong source, malformed/truncated packets, and question mismatch.

4. **Make cooldown/bypass tamper protection meaningful**
   - Do not keep the integrity HMAC key alongside the data it authenticates in ordinary preferences.
   - Store the key in Android Keystore; use constant-time signature comparison.
   - Validate stored duration/remaining/time values and fail closed on corrupt or impossible state.
   - Add tests for modified state, missing signature, invalid values, and normal countdown behavior.

5. **Release and CI safety**
   - Unit tests must fail the workflow when they fail (the `continue-on-error` override has already been removed).
   - Do not publish a debug-signed APK as a production release. If release signing secrets are unavailable, keep release packaging disabled/fail clearly rather than generating a new signing identity.
   - Never print keystore contents or secrets to logs.
   - Add a CI check that confirms the intended build variant and verifies artifact existence.

6. **DNS parser robustness**
   - Review `DnsPacketParser` for bounds checks and IPv6 extension-header handling. Reject unsupported/malformed packets safely rather than crashing or mis-parsing.
   - Add IPv4/IPv6 malformed-input and extension-header tests.

## Working rules

- Inspect the existing implementation and Gradle dependencies before choosing APIs.
- Keep changes small and reviewable; do not perform broad unrelated refactors.
- Do not weaken filtering or silently fail open when security validation fails.
- Do not add credentials, generated secrets, or private signing material to the repository.
- Run the relevant unit tests and build tasks. Report exact commands and results; do not claim tests passed if they were not run.
- Update README security/privacy statements if behavior or guarantees change. Explicitly disclose DNS resolver traffic and the limitations of DNS-based filtering.
- Finish with a concise summary, changed files, test results, and remaining risks. Open a pull request for review; do not merge it.
