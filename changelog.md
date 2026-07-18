# Changelog

## Shellix v1.3.0 — Performance, Audit & Polish
_2026-07-18_

### Added
- **Session rename + smart names:** new sessions are auto-named `Session 1`, `Session 2`, …
  instead of raw ids (`main`, `main2`). **Long-press a session tab** (or drawer item) to rename
  it via a dialog. Names show in both the tab bar and the navigation drawer.
- **Performance diagnostics:** Settings → Diagnostics now has a **Performance** section showing
  last-second frame-drop %, active session count, and whether background-tab rendering is throttled.

### Fixed / Improved
- **UI jank under load (§1):** terminal redraws are now **coalesced to one per frame** instead of
  one per output line. Heavy output (e.g. `npm install`, `pnpm install`, `codex`, `claude`) no
  longer floods the UI thread — the app stays smooth even when a session is spewing text.
- **5-session smoothness (§1):** only the **active** tab gets full per-frame redraws; background
  tabs are throttled (their PRoot process keeps running). Switching to a tab forces one immediate
  redraw so it is never blank.
- **Lifecycle guard (§1):** redraw scheduling is skipped while the app is backgrounded/stopped.
- **Off-main command polling (§1):** `UbuntuCommand` reads the terminal transcript off the main
  thread (`Dispatchers.Default`), reducing main-thread contention.
- **ptrace log spam (§3):** the benign `proot warning: ptrace(PEEKDATA): No such process` (a
  tracee-exit race) is demoted to verbose. Real warnings still surface.

### Performance (PRoot)
- **Lower CPU priority (§2):** PRoot launches with `nice -n 10` so heavy in-session work cannot
  starve the Android UI thread. Falls back to a normal launch if `nice` is unavailable.
- **Fewer bind mounts (§2):** removed a duplicate `-b $PREFIX` mount (less path-rewrite per syscall).
- **link2symlink kept ON (§2):** per termux/proot-distro docs, disabling hardlink emulation is only
  safe on SELinux-permissive devices, so it stays enabled to keep `npm`/`pnpm` installs working.

### Build / CI
- Version bumped to `1.3.0` (code 4).
- Design + plan: `docs/specs/2026-07-18-perf-audit-polish-design.md`,
  `docs/plans/2026-07-18-perf-audit-polish.md`.

---

## Shellix v1.2.0 — Performance & Security Hardening
_2026-07-18_

### Added
- None new user-facing features this cycle — focused on build/security hardening.

### Fixed / Improved
- **APK size + build speed (P2):** native ABIs restricted to `arm64-v8a` only. The PRoot
  C++ is now compiled once instead of four times and only arm64 native libraries are
  packaged. Release APK is smaller and CI builds faster. Note: the release APK no longer
  installs on 32-bit ARM devices or x86/x86_64 emulators (targets modern arm64 phones).
- **R8 minify + shrink + obfuscate (P1):** the release now enables R8 tree-shaking,
  resource shrinking, and PNG crunching. Code is obfuscated, making the APK harder to
  reverse-engineer. Keep rules protect termux, Coil, Kotlin coroutines, and Compose.
- **R8 full mode (P3):** more aggressive optimization enabled after the keep rules above
  were validated.
- **English-only resources (P4):** the bundled Arabic/Chinese translations are dropped
  from the APK (UI is English-only; other locales fall back to English).

### Security
- **Quoted commands (S2):** `UbuntuCommand` now wraps the shell command in double quotes
  before sending it to the session, preventing word-splitting / injection from arguments
  with spaces or shell metacharacters.
- **No plaintext password at rest (S3):** the Ubuntu user password file (`setup-pass.txt`)
  is deleted from app-private storage immediately after the one-time first-boot setup, so
  the credential no longer lingers on disk.
- **App-private storage (S4):** confirmed `filesDir` is created `0700` (owner-only) by
  Android; no code widens it. Sensitive files (password, font, background, crash report,
  Ubuntu rootfs) inherit that protection.

### Build / CI
- Local Gradle/SDK build is possible with a Java 17 toolchain + daemon-JVM override
  (CI remains the primary build signal). See `AGENTS.md`.
- Research notes: `docs/research/2026-07-18-security-performance.md`.

---

## Shellix v1.1.0
_2026-07-17_

### Added
- **Terminal color presets (live):** Nord, One Dark, Tokyo Night, Catppuccin, Dracula,
  Default — applied globally to all live sessions, no restart needed.
- **Background supports all image formats:** HEIC / WebP / AVIF decoded via `ImageDecoder`
  + Coil `AsyncImage` on supported OS versions.
- **Diagnostics:** in-app crash report + log ring buffer viewer in Settings, plus a
  persisted crash report and plugin-error notification toggle.

### Fixed
- `sudo: unable to resolve host localhost` warning — `init.sh` now appends
  `127.0.0.1 localhost` to `/etc/hosts`.

---

## Shellix v1.0.0
_2026-07-17_

- Initial public release: termux-based terminal with PRoot Ubuntu, customization
  (background, font, theme), package/setup wizard, and downloader.
