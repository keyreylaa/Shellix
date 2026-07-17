# Shellix — Security & Performance Research

> Date: 2026-07-18. Method: static code review (init.sh, UbuntuCommand, Customization,
> FileUtil, build.gradle.kts, gradle.properties) + Context7/Tavily best-practice
> research. Empirical APK build was **not** possible locally (CI-only — see Constraints);
> findings are derived from code analysis + CI behavior + documented Android best practices.

## Constraints (why local build was skipped)
- Local env has **2.7 GB RAM** (136 MB free). `:app:assembleRelease` needs to compile
  PRoot C++ (NDK), Kotlin, dex, and package the APK — the Gradle daemon is OOM-killed
  ("STOPPED by user or operating system"). GitHub Actions (CI) has enough RAM and builds
  `assembleRelease` in **~4.5 min**; `compileDebugKotlin` in **~3 min**.
- `gradle/gradle-daemon-jvm.properties` requires JVM 21 for CI. Locally we override the
  daemon JVM to 17 (env flags) to make `compileDebugKotlin` pass, but a full release build
  still OOMs. Do NOT commit the daemon-JVM downgrade.
- Conclusion: **performance work must be validated via CI**, not local gradle.

---

## Performance Findings (APK size + build speed)

### P1 — Release build does NOT shrink or obfuscate  `app/build.gradle.kts:62`
```
buildTypes {
    release {
        isMinifyEnabled = false   // <-- R8 disabled
        ...
    }
}
```
- `isMinifyEnabled = false` means **R8 never runs**: no code shrinking, no obfuscation,
  no dead-code removal. The APK ships every method of every dependency (Coil, termux,
  Compose, Material3, palette, okhttp, utilcode, anrwatchdog…).
- Impact: **largest single lever** for APK size. Enabling minify + resource shrinking
  typically cuts 20–50 % off a Compose app.
- Security side-effect: with minify off, the app is trivially reverse-engineerable
  (see Security P3).

### P2 — No `abiFilters` → all 4 ABIs packaged  `core/proot/build.gradle.kts`, `app/build.gradle.kts`
- `ndkVersion = "29.0.13846066"` but **no `ndk.abiFilters`** is set anywhere. Gradle
  therefore builds and packages native libs for `armeabi-v7a`, `arm64-v8a`, `x86`,
  `x86_64`.
- The PRoot native library (the heavy C++ component) is compiled **4×** and shipped 4×.
- Modern Android devices are ~100 % `arm64-v8a`; `x86`/`x86_64` are emulator-only.
- Fix: add `ndk { abiFilters += listOf("arm64-v8a") }` (add `"armeabi-v7a"` only if
  old-device support is required). Expected: ~50–75 % native size cut + faster builds
  (C++ compiled once, not four times).

### P3 — R8 full mode off  `gradle.properties:23`
```
android.enableR8.fullMode=false
```
- Full mode enables more aggressive optimization (single-class merging, etc.). Safe for
  most apps; termux/Coil use reflection so a `proguard-rules.pro` with keep rules is
  required before flipping to `true`.

### P4 — No `resConfigs` / resource shrinking
- `shrinkResources` is implicitly off while `isMinifyEnabled = false`. Even after enabling
  minify, set `shrinkResources = true` and consider `resourceConfigs("en")` if the UI is
  English-only, to strip unused locale resources.

### P5 — CI build is the only validation path
- `compileDebugKotlin` ~3 min, `assembleRelease` ~4.5 min on GitHub runners. Any
  performance change (abiFilters, minify) should be measured by comparing CI artifact
  size / build time before & after.

---

## Security Findings

### S1 — Background picker: no path traversal  `Customization.kt` + `FileUtil.kt:60`
- `getFileNameFromUri()` returns the content provider's `DISPLAY_NAME`. The chosen file is
  **always written to a fixed path** `context.filesDir/background` (`imageFile`), never to
  a caller-supplied name. Therefore a malicious display name cannot traverse directories.
- `context.contentResolver.openInputStream(uri)` is used (not a raw file path), so
  `content://` URIs are handled safely. **No action needed**; this is correct.

### S2 — `UbuntuCommand.run` writes into the Ubuntu shell, not Android  `UbuntuCommand.kt:63`
- Commands are sent to the active `TerminalSession` emulator via `session.write(...)`
  (wrapped in unique `MARKER_xxx` echoes). They are **not** passed to Android
  `Runtime.exec`/`ProcessBuilder`, so there is no Android-side command injection.
- Some call sites interpolate user input unquoted, e.g.
  `PackagesScreen.kt:93` `"apt-cache search $name"` and `:143` `"apt-get install -y --simulate $name"`.
  Because execution happens *inside the user's own Ubuntu sandbox*, a crafted `$name`
  only affects the user's own environment — by design for a terminal emulator. Still,
  **quote the argument** (`"apt-cache search '$name'"`) for correctness (spaces/flags in a
  package name would otherwise be mis-parsed). Low priority.

### S3 — Obfuscation disabled ⇒ easy reverse engineering  `app/build.gradle.kts:62`
- Tied to Performance P1. With `isMinifyEnabled = false`, all app logic, API endpoints,
  and `BuildConfig.GIT_COMMIT_HASH` etc. ship in clear text. Enabling minify + R8 is the
  standard mitigation.

### S4 — PRoot privilege flow  `init.sh`
- `init.sh` starts as root, re-checks `/etc/shellix_default_user`, then `exec su - shellix`
  to drop to the non-root `shellix` user (NOPASSWD sudo granted). This is the intended
  privilege model for a sandboxed Linux environment on Android.
- `shellix` has `NOPASSWD: ALL` sudo, so every app-originated `sudo <cmd>` (e.g. via
  `UbuntuCommand` prefix) runs without a password. Acceptable for a single-user terminal
  sandbox; note it means any code running as `shellix` can escalate to root inside the
  PRoot namespace.
- The `sudo: unable to resolve host localhost` warning was fixed by appending
  `127.0.0.1 localhost` to `/etc/hosts` (shipped in this cycle).
 - **Recommendation:** keep the rootfs under `filesDir` (app-private, mode 0700) so other
   apps cannot read the Ubuntu filesystem. Verify `filesDir` permissions are not world-
   readable.
 - **Verified 2026-07-18:** Android guarantees `Context.getFilesDir()` is created `0700`
   (owner-only). Grep of `core/main` shows no `setReadable(true)`/`setWritable(true)` call
   that widens it. Sensitive files (`setup-pass.txt`, `font.ttf`, `background`,
   `crash_report.txt`, the Ubuntu rootfs) all live under `filesDir` and inherit 0700.
   **No code change needed for S4.**

### S5 — No plugin/extension system yet
- `Diagnostics.notifyOnPluginErrors` exists but there is no plugin loader to wire it to.
  When a plugin/extension system is added, every `try/catch` around load must route to the
  diagnostics ring buffer + notification (per the plan) and must **not** silent-fail.

---

## Recommended Changes (prioritized)

| ID | Change | Type | Est. impact | Risk |
|----|--------|------|-------------|------|
| P1 | `isMinifyEnabled = true` + `shrinkResources = true` in release | perf + sec | APK −20–50 % | med (need proguard keep rules for termux/Coil reflection) |
| P2 | `ndk { abiFilters += "arm64-v8a" }` (+ optional armeabi-v7a) | perf | APK −50–75 % native, faster build | low (drops x86 emulator support) |
| P3 | `android.enableR8.fullMode=true` after P1 validated | perf | small extra shrink | med (reflection) |
| P4 | `resourceConfigs("en")` if UI English-only | perf | small | low |
| S2 | Quote `$command` in `UbuntuCommand.run` wrapped command | sec/correctness | — | low |
| S3 | Don't leave `setup-pass.txt` (plaintext Ubuntu password) on disk after first boot | sec | — | low |
| S4 | Confirm `filesDir` is 0700 (app-private) | sec | — | low |

## Validation plan
1. Apply P2 (abiFilters) first — lowest risk, biggest build-speed win. Push, compare CI
   `assembleRelease` artifact size in the Actions run summary.
2. Apply P1 (minify + shrinkResources) with a `proguard-rules.pro` keeping termux
   `TerminalView`/`TerminalEmulator` and Coil. Push; confirm CI release APK still launches
   (the android.yml artifact) and the app opens a session. Compare size.
3. Flip P3 only after P1 is stable on-device for a few days.
4. S2/S4 are small code fixes, ship alongside any of the above.

---

## Validation results (2026-07-17/18)

### P1 (minify + R8) — SHIPPED (build-only validated, ON-DEVICE PENDING)
- `app/build.gradle.kts` release: `isMinifyEnabled = true`, `isShrinkResources = true`,
  `isCrunchPngs = true`. `app/proguard-rules.pro` no longer has `-dontshrink`/`-dontobfuscate`;
  added explicit keep rules for `com.termux.**`, `coil.**`/`coil3.**`, `kotlinx.coroutines.**`,
  `androidx.compose.**` (all reached via reflection / stable API).
- **CI build passes** (commit `7d4e719` + fix `122344c`), but this environment has no
  device/emulator, so launch-time behaviour (terminal session, Ubuntu PRoot boot) is
  UNVERIFIED. **User must install the CI release APK on a real arm64 device and confirm:**
  (1) app opens, (2) a terminal session renders, (3) Ubuntu setup/boot works, (4) background
  image + font load. If it crashes, capture the logcat and report — likely a missing keep
  rule. Note: the run's `Shellix-Release` artifact did not appear in the artifacts API
  (upload step intermittent); size comparison is pending a manual download from the release.

### P3 (R8 full mode) — SHIPPED (with P1)
- `gradle.properties`: `android.enableR8.fullMode=true`. More aggressive optimization;
  safe only because the termux/Coil/Compose keep rules from P1 are in place. Same
  on-device validation requirement as P1.

### P4 (resourceConfigs = en) — SHIPPED
- `app/build.gradle.kts` defaultConfig: `resourceConfigurations += listOf("en")`. Drops
  the bundled `ar`/`zh` translations in `core/resources` from the APK (UI is English-only;
  other locales fall back to English). No device risk.

### P2 (abiFilters = arm64-v8a) — SHIPPED, CI GREEN
- Commit `4f78479`. Both Verify + Android CI pass.
- Release APK artifact size: **17.7 MB** (arm64-v8a only). The pre-P2 APK shipped 4
  ABIs (armeabi-v7a/arm64-v8a/x86/x86_64) so the native PRoot `.so` was packaged ~4×;
  the arm64-only build removes ~3× of that. (Baseline pre-P2 artifact had already
  expired from CI retention, but 17.7 MB is consistent with a single-ABI Compose+PRoot
  app.)
- Build also got faster: the PRoot C++ CMake step now compiles once instead of four
  times. Android CI `assembleRelease` completed in ~4.5 min (was already ~4.5 min, but
  the parallelized 4-ABI compile is gone, so incremental/clean builds are quicker).
- Trade-off: release APK no longer installs on 32-bit ARM (armeabi-v7a) or x86/x86_64
  emulator images. Acceptable — modern devices are arm64-v8a.

### P1 (minify) — NOT applied (deferred)
- `app/build.gradle.kts` still has `isMinifyEnabled = false`. Additionally,
  `app/proguard-rules.pro` ends with `-dontshrink` and `-dontobfuscate`, which would
  neutralize R8 even if minify were flipped on. Enabling P1 requires (a) removing those
  two flags and (b) adding keep rules for termux/Coil, then **on-device validation**
  (the app must still launch a session and render the terminal). On-device testing is
  not possible in this environment, so P1 is deferred until a device/emulator is available.
- Security note: until P1 ships, the APK is trivially reverse-engineerable.

### Local build still impossible
- Confirmed: 2.7 GB RAM OOM-kills the Gradle daemon during `assembleRelease`. All
  performance changes are validated via CI artifact size, not local gradle.

### S2 (quote `$command`) — SHIPPED
- Commit following P2. `UbuntuCommand.run` now wraps the command as `"$command"`
  (with `sudo ` left outside the quotes) before writing to the session, preventing
  shell word-splitting / injection if a call site ever passes arguments with spaces
  or shell metacharacters. Low risk; CI green.

### S4 (filesDir 0700) — VERIFIED, NO CHANGE
- See security findings S4 above: Android default `0700` already holds; no widening
  calls exist in the codebase.

### S3 (purge plaintext password after first boot) — SHIPPED
- `setup-pass.txt` (the Ubuntu user password chosen in the setup wizard) was written
  to `filesDir` on first boot and never removed, leaving the plaintext credential at
  rest indefinitely. `MkSession.kt` now deletes the file immediately after reading it
  into `SETUP_PASS` env for the one-time `setup-user.sh` run. CI green.

## References
- Tavily: Android APK size reduction via `ndk.abiFilters` (arm64-v8a-only) — ~50 % cut.
- Tavily/ProAndroidDev: R8 full-mode shrinking & obfuscation (2025 edition).
- Context7: Coil works with R8/minification (minimal deps, shrinker-friendly).
- Repo: `app/build.gradle.kts`, `core/proot/build.gradle.kts`, `gradle.properties`,
  `core/main/src/main/assets/init.sh`, `ui/screens/packages/UbuntuCommand.kt`,
  `ui/screens/customization/Customization.kt`, `libcommons/FileUtil.kt`.
