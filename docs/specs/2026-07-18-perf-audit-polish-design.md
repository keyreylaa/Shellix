# Shellix — Performance, Audit & Polish Design (v1.3.0)

> Date: 2026-07-18
> Method: brainstorming (user-approved design) + Context7 research + static code review.
> Scope: runtime responsiveness (UI stays smooth under load), PRoot overhead mitigation,
>        bug audit (session rename + ptrace warning + obvious JNI/launch issues),
>        and lightweight perf instrumentation.
> Out of scope: APK build-size shrinks (already shipped in v1.2.0: R8 minify P1, arm64-only
>        ABI P2, R8 full mode P3, en-only resources P4), PRoot native engine (C/JNI) rewrite.

## Context & Why

User reports the app feels "macet/delay/stuck" when running heavy tools inside the Ubuntu
PRoot session (Codex / Claude Code CLI / OpenCode, `npm install`, `pnpm install`), and even
light interactions stutter. Two concrete bugs were called out:

1. **Session tabs cannot be renamed** — labels are raw ids (`"main"`, `"main2"`), no rename UX.
2. **PRoot warning spam** — `proot warning: ptrace(PEEKDATA): No such process` floods logcat.

User also runs **up to 5 sessions simultaneously**, which multiplies both PRoot ptrace
overhead and render scheduling load.

### Research basis (not training-data guesses)

- Context7 `/termux/proot-distro`: PRoot is slow *by design* — it emulates `chroot`/`mount`
  via Linux `ptrace` syscall interception + path rewriting. "performance overhead due to
  system call interception" is inherent; we cannot remove it, only reduce its impact on the
  Android UI thread and reduce mount/path-rewrite work.
- `core/main/.../TerminalBackEnd.kt`: `onTextChanged()` calls `terminal.onScreenUpdated()` on
  the main thread for **every** output line. Under a firehose of output (e.g. `npm install`)
  this floods the UI thread with invalidations → jank/freeze. This is the primary app-level
  cause of "walau sepele jadi ngebut" complaints.
- `core/main/.../ui/screens/packages/UbuntuCommand.kt`: polls
  `emulator.getScreen().getTranscriptText()` on an interval; if that poll runs on the main
  thread it adds to the load.
- `SessionService.kt`: `sessionList` is `MutableMap<String,Int>` keyed by raw session id and
  used directly as the tab label in `SessionTabBar.kt`. No display-name / rename concept.
- Existing `docs/research/2026-07-18-security-performance.md` already shipped build-size perf
  (P1–P4) in v1.2.0, so this design deliberately does NOT re-do those.
- Environment constraint (from research file): local gradle OOMs (2.7 GB RAM); **all
  validation is via CI + real device, not local gradle**.

## Success criteria

**Perceived smoothness (user-approved criterion A):** UI interactions — switching tabs,
opening the drawer, typing in the terminal, scrolling the session tab bar — remain smooth
(~60fps, no freeze > ~100ms) even while a heavy process runs in one or more PRoot sessions,
including the 5-session case. Verified via `perfetto`/`systrace` on a real device + observation.

## Design (Approach 3 — Hybrid: surgical patches + light thread-offload + measurement)

### Section 1 — App-thread offload, throttle & visibility-aware multi-session (CORE)

- **Coalesce terminal redraws:** In `TerminalBackEnd.onTextChanged()`, do not call
  `terminal.onScreenUpdated()` directly per line. Schedule it via `terminal.post { }` or a
  `Choreographer` frame callback, coalescing multiple updates within a frame (~16ms) into a
  single `onScreenUpdated()`. This removes the per-line invalidation flood.
- **Offload `UbuntuCommand` polling:** Move the transcript polling loop to `Dispatchers.Default`
  with a `delay()` debounce; read `getTranscriptText()` off the main thread and only post the
  parsed result back to the main thread / state.
- **Visibility-aware rendering (for the 5-tab case):** Track the active/visible session. Only
  the active session gets full per-frame redraws. Background sessions (non-active tabs) get
  their redraws throttled aggressively (e.g. 1x/500ms–1s) or skipped entirely while not
  visible — the PRoot process keeps running, only the redraw is paused. When a tab becomes
  active again, force one immediate full redraw so it is never blank.
- **Lifecycle guard:** Skip redraw scheduling when the activity is in the background or the
  screen is off (saves CPU/battery; also avoids wasted work).

### Section 2 — PRoot overhead mitigation (reduce, not remove)

- **Cull unnecessary bind mounts:** In `MkSession.kt` / `init-host.sh`, bind only what is used
  (`/dev`, `/proc`, `/sys`, required storage). Fewer mounts → less path-rewrite per syscall.
- **`--link2symlink` review:** `npm install`/`pnpm install` create many hardlinks; PRoot's
  link2symlink emulation adds overhead. Test disabling it on internal ext4 storage; if safe,
  disable. **Risk:** must be verified on-device — if extraction/install fails on some storage,
  revert to default. Ship behind a safe default (keep on unless device-known-safe).
- **Nice priority:** Launch the PRoot shell with `nice -n 10` (or `renice` after start) so heavy
  in-session work cannot starve the Android UI thread. UI stays responsive while `codex`/`claude`
  grind.
- **`PROOT_TMP_DIR` isolation:** Already per-session (`MkSession.kt:84` → `cacheDir/child(sessionId)`).
  Verify it stays on `cacheDir` (not a ramdisk) so 5 sessions don't collide or exhaust RAM.

### Section 3 — ptrace warning & log hygiene

- **Demote the specific noise:** `proot warning: ptrace(PEEKDATA): No such process` is a benign
  tracee-exit race, not a crash. In `TerminalBackEnd.logWarn()` add a narrow filter that
  downgrades *only* this exact pattern to `DEBUG`/`VERBOSE`. Do NOT blanket-filter `ptrace*` so
  real warnings stay visible.
- **JNI/launch audit:** Review `MkSession.kt` + `init-host.sh` argument passing to `libproot.so`
  (consistent with prior fix `e6823fe` that quoted commands). Fix any mis-quoted / malformed
  arg (per user directive: "sekalian cari JNI kurang bagus, langsung lakukan"). Keep changes at
  the launch-script/flag level — do not modify the C engine.

### Section 4 — Session rename + smart default naming

- **Data model:** Separate internal `sessionId` (unique key) from `displayName` (shown + renameable).
  Change `SessionService.sessionList` to `MutableMap<String, SessionMeta>` where
  `SessionMeta(name: String, mode: Int)`. Keep `sessionId` as the map key.
- **Smart default:** New sessions auto-named `"Session 1"`, `"Session 2"`, … (not `"main2"`).
  First session can stay `"Session 1"` for consistency (backward-compat: existing `"main"` session
  still boots, only its label becomes a displayName).
- **Rename UX:** Long-press a tab in `SessionTabBar.kt` → reuse existing `ui/components/InputDialog.kt`
  → write back to `SessionMeta.name`. Tab updates immediately via the existing state map.
- **Persistence (light):** Prefer in-memory for the service lifetime (SessionService lives for the
  app session). If cheap, persist id→name in `Settings`/DataStore so rotation/rebind doesn't reset.
  Keep it minimal — no new storage format unless trivial.

### Section 5 — Lightweight perf instrumentation (measurement)

- **Frame-drop counter:** In `MainActivity`, use `Choreographer.FrameCallback` (or
  `Window.OnFrameMetricsAvailableListener`) to count frames exceeding ~16ms / ~32ms. Aggregate
  once per second into `AppLog` (not per-frame) to keep overhead near zero.
- **Session telemetry:** Log active session count + mode (relevant to the 5-tab scenario) and
  whether background render is paused.
- **Diagnostics UI:** Add a "Performance" section to the existing `DiagnosticsScreen.kt` showing
  last frame-drop %, session count, render-pause state.
- **Low overhead / toggle:** Instrumentation only counts; default can be on-but-silent or off via
  a Settings flag. No heavy allocation on the hot path.

### Section 6 — Verification & rollout

- **Local gates:** `./verify.sh` must be fully green; `./gradlew :core:main:compileDebugKotlin`
  (Java 17 override) to catch Kotlin errors. (Local full release build is impossible — OOM.)
- **Device perf check:** 5 tabs open, one running `npm install`/`pnpm install` → switch tabs,
  open drawer, type. Measure frame-drop% via `perfetto`/`systrace` before vs after. Target: no
  UI freeze > ~100ms during interaction. Confirm `ptrace(PEEKDATA)` no longer spams `warn`.
- **CI:** Push → `gh run list` → `verify.yml` (unit + lint) green; `android.yml` runs on master
  push. Perf itself is validated on-device, not CI.
- **Rollout:** Bump `1.2.0 → 1.3.0`; update `changelog.md` + `ROADMAP.md` (mirror README roadmap).
  Draft release, test APK on device (5-tab scenario), then publish.
- **Risks / rollback:** `--link2symlink` off may break install on some storage → test, revert if
  needed. Visibility-aware render must never leave a tab blank on reopen (force redraw).

## Files touched (expected)

- `core/main/.../ui/screens/terminal/TerminalBackEnd.kt` (Section 1, 3)
- `core/main/.../ui/screens/packages/UbuntuCommand.kt` (Section 1)
- `core/main/.../ui/screens/terminal/MkSession.kt` (Section 2, 3)
- `core/main/.../assets/init-host.sh` (Section 2, 3)
- `core/main/.../service/SessionService.kt` (Section 4)
- `core/main/.../ui/screens/terminal/SessionTabBar.kt` (Section 4)
- `core/main/.../ui/components/InputDialog.kt` (reused, Section 4)
- `core/main/.../ui/activities/terminal/MainActivity.kt` (Section 5)
- `core/main/.../ui/diagnostics/DiagnosticsScreen.kt` + `AppLog.kt` (Section 5)

## Non-goals

- Rewriting the PRoot C engine or JNI loader.
- Re-doing build-size perf (already in v1.2.0).
- Full profiling/observability suite (perfetto remains the deep tool).
