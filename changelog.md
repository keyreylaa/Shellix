# Changelog

## Shellix v1.3.1 — File Manager power tools, macOS-style screenshot & guardrails
_2026-07-19_

### Added
- **Swipe navigation in File Manager:** swipe left/right on the file list to go back /
  forward through the navigation history (back stack + forward stack).
- **Undo delete:** deleted files/folders are moved to an app-private trash
  (`fm_trash`) with a sidecar `.meta`; a Snackbar **Undo** restores them. Collisions
  are handled with a numeric suffix.
- **Copy / move progress + cancel:** long operations show a linear progress indicator
  with a Cancel action; cancelling deletes the partial destination and preserves the
  source. Stream-copied across the Ubuntu ↔ Phone Storage boundary (no `renameTo`).
- **Folder sync (two-way):** pair an Ubuntu folder with a Phone Storage folder; a
  pure diff/merge engine syncs on last-write-wins (conflicts saved as
  `.conflict-<ts>`), driven by a `WorkManager` periodic (15-min) worker + a manual
  "Sync now" button.
- **Open with / Share:** files are staged into a scoped `fm_share` directory and
  exposed via FileProvider (`ACTION_VIEW` / `ACTION_SEND`) — works for app-private
  Ubuntu files too. Disk staging runs off the main thread (fixes an ANR on large
  files).
- **PC-style terminal screenshot:** a top-bar button re-renders the active session
  into a macOS-style window (rounded frame, traffic-light dots, real ANSI colors +
  active theme). The title uses the **live** PRoot identity (`<user>@shellix`). Two
  output resolutions: device (portrait) and a 1440px-wide landscape "Desktop" mode
  re-rendered from the character grid (sharp, no upscaling). Saved to
  `Pictures/Shellix` via MediaStore and offered for immediate share.
- **Two-step verification:** a setting (off by default) that requires a confirmation
  tap before clearing the terminal or closing a session — closing previously
  unguarded destructive gaps.

### Fixed
- **ANR on share/open-with:** staging a file into `fm_share` no longer runs on the
  main thread (moved to `Dispatchers.IO`); the activity is started back on the UI
  thread.

### Build / CI
- Version bumped to `1.3.1` (code 6).
- `android.yml` now also builds the APK on pull requests (artifact
  `Shellix-Release-pr<N>`), so PRs get a real compile + APK signal.

---

## Shellix v1.3.0-beta — Performance, File Manager, Editor & Polish
_2026-07-19_

### Added
- **Built-in File Manager** (`core:filemanager`): dual-realm browsing (Ubuntu rootfs +
  phone storage), copy/cut/paste, rename, delete, multi-select, create file/folder, with
  a clear `noexec` warning chip when in FUSE phone storage. Destructive actions and
  overwrites are confirmed first.
- **Built-in Code Editor** (Sora Editor): line numbers, save, binary-file detection, and
  lightweight syntax highlighting for ~20 languages (Kotlin, Java, JS/TS, Python, shell,
  C/C++, Rust, Go, PHP, Ruby, Swift, SQL, CSS, Lua, Dart, JSON, YAML, TOML/INI, XML/HTML).
  No native regex engine or grammar bundle — the terminal render path is untouched.
- **About screen**: app version (via PackageManager), author, MIT license (+ Sora LGPL
  note), lineage (ReTerminal <- Termux, Termux terminal engine, PRoot), GitHub / Issue /
  Wiki links, and a live render of the GitHub Wiki `Home.md` (single source of truth).
- **Proactive crash notice**: if a crash was recorded last run, a one-time dialog on next
  launch offers to view the report (Diagnostics) or dismiss — shown once per distinct crash.
- **Keep screen on** toggle (FLAG_KEEP_SCREEN_ON, no new permission).
- **Copy** button on the in-app App logs and crash report dialogs.

### Added (from 1.3.0 perf line)
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
