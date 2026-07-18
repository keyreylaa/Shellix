# Performance, Audit & Polish — Implementation Plan (v1.3.0)

> REQUIRED SUB-SKILL: follow `docs/specs/2026-07-18-perf-audit-polish-design.md` exactly.
> Every task maps to a spec Section (1–6). No placeholders — real Kotlin/sh code, exact paths.
> Validation is CI + real device (local gradle OOMs at 2.7 GB RAM). Run `./verify.sh` before every push.
> Git identity for all commits: `keyreylaa <zyskyv@gmail.com>`.

## Goal
Make the UI stay smooth (~60fps, no freeze > ~100ms) even while heavy tools (codex, claude, `npm`/`pnpm install`)
run inside one or more PRoot sessions — including the 5-simultaneous-session case — plus fix the session-rename
and `ptrace(PEEKDATA)` bugs and add lightweight perf instrumentation.

## Architecture (touch points)
- Render hot path: `TerminalBackEnd.onTextChanged()` → `terminal.onScreenUpdated()` (per-line, main thread).
- Command runner: `UbuntuCommand.run()` (poll loop, already on `Dispatchers.IO`).
- Session model: `SessionService.sessionList: MutableMap<String,Int>` keyed by raw id; label = raw id.
- PRoot launch: `MkSession.createSession()` builds env/args → `init-host.sh` builds proot `$ARGS` → `libproot.so`.
- Instrumentation sink: `AppLog` / `Diagnostics` ring buffer + `DiagnosticsSection` UI.

## Tech Stack
Kotlin, Jetpack Compose, termux terminal-view/terminal-emulator, PRoot (native, unchanged), coroutines.

---

## T1 — App-thread offload, coalescing & visibility-aware rendering (Spec §1) [CORE]

**File:** `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalBackEnd.kt`

### T1.1 Coalesce redraws in `onTextChanged()`
Replace the per-line direct call:

```kotlin
override fun onTextChanged(changedSession: TerminalSession) {
    terminal.onScreenUpdated()
}
```

with a frame-coalesced, visibility-aware, background-throttled scheduler. Add fields + method to the class:

```kotlin
import android.os.SystemClock

// ... inside TerminalBackEnd
@Volatile private var redrawScheduled = false
@Volatile private var lastBgRedrawMs = 0L

/** true only when this backend's session is the currently attached/visible one. */
private fun isActiveSession(session: TerminalSession): Boolean {
    val binder = activity.viewModel.sessionBinder ?: return true
    val currentId = binder.getService().currentSession.value.first
    return binder.getSession(currentId) === session
}

override fun onTextChanged(changedSession: TerminalSession) {
    // Lifecycle guard: skip work when the activity is not started (backgrounded / screen off).
    if (!activity.isTerminalResumed) return

    if (isActiveSession(changedSession)) {
        // Active tab: coalesce every burst of output into one redraw per frame (~16ms).
        if (redrawScheduled) return
        redrawScheduled = true
        terminal.post {
            redrawScheduled = false
            terminal.onScreenUpdated()
        }
    } else {
        // Background tab (5-session case): PRoot keeps running, but throttle redraw to 1/500ms.
        val now = SystemClock.uptimeMillis()
        if (now - lastBgRedrawMs < 500L) return
        lastBgRedrawMs = now
        terminal.post { terminal.onScreenUpdated() }
    }
}
```

### T1.2 Lifecycle flag + force-redraw on tab activation
**File:** `core/main/src/main/java/com/rk/shellix/ui/activities/terminal/MainActivity.kt`
Add a resumed flag the backend can read:

```kotlin
// field
@Volatile var isTerminalResumed = true
    private set

override fun onResume() {
    super.onResume()
    isTerminalResumed = true
    // ... existing keyboard restore ...
}

override fun onStop() {
    isTerminalResumed = false
    super.onStop()
    viewModel.unbindService(this)
}
```

**File:** `TerminalViewModel.changeSession()` — after `terminal.attachSession(session)`, force one
immediate full redraw so a re-opened background tab is never blank:

```kotlin
terminal.attachSession(session)
terminal.setTerminalViewClient(client)
terminal.onScreenUpdated() // force redraw on activation (visibility-aware guarantee)
```

### T1.3 Offload `UbuntuCommand` transcript parsing
**File:** `core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt`
The loop already runs in `withContext(Dispatchers.IO)`. Move the transcript **read + parse** onto
`Dispatchers.Default` (CPU work) so the IO thread isn't blocked and nothing touches main. Wrap the
`getTranscriptText()` + `indexOf` parsing in each poll iteration:

```kotlin
delay(POLL_MS)
val (m1, m2, now) = withContext(Dispatchers.Default) {
    val t = emulator.getScreen().getTranscriptText()
    val m0 = t.indexOf(marker)
    val a = t.indexOf(marker, m0 + marker.length)
    val b = t.indexOf(marker, a + marker.length)
    Triple(a, b, t)
}
if (m1 >= 0 && m2 > m1) {
    val between = now.substring(m1 + marker.length, m2)
        .removePrefix("\r\n").removePrefix("\n")
    return@withContext Result.success(between)
}
```
Apply the same `withContext(Dispatchers.Default)` wrap to the uid-detection poll loop.

**Verify:** `./verify.sh` green; push and watch `gh run list` (compile). Commit:
`perf(terminal): coalesce redraws + visibility-aware throttle + offload command poll (§1)`

---

## T2 — PRoot overhead mitigation (Spec §2)

**File:** `core/main/src/main/assets/init-host.sh`

### T2.1 Remove duplicate bind
There are two `ARGS="$ARGS -b $PREFIX"` lines. Delete the second one (near `/sys`). One bind is enough
— fewer binds = less path-rewrite per syscall.

### T2.2 Nice priority for the PRoot process
Launch proot at lower CPU priority so heavy in-session work can't starve the Android UI thread.
Change the final launch line:

```sh
exec nice -n 10 $PROOT $ARGS sh $PREFIX/local/bin/init "$@"
```
Fallback if `nice` is unavailable on the host (guard so launch never fails):

```sh
if command -v nice >/dev/null 2>&1; then
  exec nice -n 10 $PROOT $ARGS sh $PREFIX/local/bin/init "$@"
else
  exec $PROOT $ARGS sh $PREFIX/local/bin/init "$@"
fi
```

### T2.3 `--link2symlink` — keep ON by default (research-backed)
Context7 `/termux/proot-distro`: `--no-link2symlink` is "generally safe only on devices with SELinux in
permissive mode." Most Android devices are enforcing, so **do NOT disable it by default** (would break
`npm`/`pnpm` hardlink-heavy installs). Leave `--link2symlink` in place. Document this decision inline:

```sh
# link2symlink emulates hardlinks (needed by npm/pnpm). Disabling it is only safe on
# SELinux-permissive devices (per termux/proot-distro docs), so we keep it ON.
ARGS="$ARGS --link2symlink"
```

### T2.4 Verify `PROOT_TMP_DIR` stays on cacheDir
`MkSession.kt` sets `PROOT_TMP_DIR=${getTempDir(this).child(sessionId)}` (cacheDir, per-session). No
change needed — add a one-line confirmation in the commit message that 5 sessions each get an isolated
cache tmp dir (not ramdisk), so they don't collide or exhaust RAM.

**Verify:** `bash -n core/main/src/main/assets/init-host.sh`; `./verify.sh`. Commit:
`perf(proot): nice -10 launch + drop duplicate bind + document link2symlink (§2)`

---

## T3 — ptrace warning filter + JNI/launch audit (Spec §3)

**File:** `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalBackEnd.kt`

### T3.1 Narrow-filter the benign ptrace warning
Route terminal logs through `AppLog` and demote only the exact benign pattern to VERBOSE:

```kotlin
import com.rk.shellix.ui.diagnostics.AppLog

override fun logWarn(tag: String?, message: String?) {
    val t = tag ?: "Terminal"
    val m = message ?: ""
    // Benign tracee-exit race spam from proot; demote to verbose so real warnings stay visible.
    if (m.contains("ptrace(PEEKDATA)") && m.contains("No such process")) {
        AppLog.v(t, m)
    } else {
        AppLog.w(t, m)
    }
}
```
Do NOT blanket-filter `ptrace` — only this exact `PEEKDATA` + `No such process` combination.

### T3.2 JNI / launch argument audit
Review arg passing from `MkSession.kt` → `init-host.sh` → `libproot.so`:
- Confirm `arrayOf("-c", initFile.absolutePath)` for `/system/bin/sh` is correct (it is; `-c` + path).
- In `init-host.sh`, `$PROOT $ARGS ...` is unquoted word-splitting (intentional for `$ARGS`), but
  `$PREFIX` paths with spaces would break. App-private paths have no spaces, so leave as-is; note it.
- Ensure `PROOT_LOADER` / `PROOT` env point to real `.so` files (guarded `libloader32.so` check exists).
  No malformed arg found → record "audit clean" in commit body. Only patch if a concrete defect appears.

**Verify:** `./verify.sh`. Commit:
`fix(log): demote benign proot ptrace(PEEKDATA) warning to verbose + JNI launch audit (§3)`

---

## T4 — Session rename + smart default naming (Spec §4)

### T4.1 Introduce `SessionMeta`
**File:** `core/main/src/main/java/com/rk/shellix/service/SessionService.kt`
Add the data class and change the map value type:

```kotlin
data class SessionMeta(val name: String, val mode: Int)

// was: val sessionList = mutableStateMapOf<String, Int>()
val sessionList = mutableStateMapOf<String, SessionMeta>()
```
Update `createSession` to store meta and give a smart default name:

```kotlin
fun createSession(id: String, client: TerminalSessionClient, workingMode: Int): TerminalSession {
    return MkSession.createSession(...).also {
        sessions[id] = it
        val displayName = "Session ${sessionList.size + 1}"
        sessionList[id] = SessionMeta(name = displayName, mode = workingMode)
        updateNotification()
    }
}
```
Add a rename API on the binder:

```kotlin
fun renameSession(id: String, newName: String) {
    val meta = sessionList[id] ?: return
    if (newName.isNotBlank()) sessionList[id] = meta.copy(name = newName.trim())
}
```

### T4.2 Fix every consumer of the old `Int` value type
Search-and-fix all `sessionList[...]` reads that expect an `Int`:
- `TerminalViewModel.changeSession()` last line:
  `currentSession.value = Pair(sessionId, sessionBinder.getService().sessionList[sessionId]!!.mode)`
- Any other `.sessionList[id]` used as mode → append `.mode`.
Run `grep -rn "sessionList\[" core/main/src` and fix each.

### T4.3 Smart default id/name in `TerminalScreen.kt`
`generateUniqueSessionId()` may keep returning unique internal ids (`main1`…) — that's fine as a key.
The **display name** now comes from `SessionMeta.name` ("Session N"), so no change to the id generator
is required for the label. Leave `generateUniqueSessionId` as the internal-key source.

### T4.4 Tab bar: show name + long-press rename
**File:** `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/SessionTabBar.kt`
- Read entries as `(id, meta)` pairs; label uses `meta.name`.
- Add `onRenameSession: (String) -> Unit` param and a long-press via `Modifier.combinedClickable`.

```kotlin
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionTabBar(
    sessionBinder: SessionService.SessionBinder?,
    onSessionSelected: (String) -> Unit,
    onCreateSession: () -> Unit,
    onCloseSession: (String) -> Unit,
    onRenameSession: (String) -> Unit
) {
    val service = sessionBinder?.getService()
    val entries = service?.sessionList?.entries?.map { it.key to it.value }?.toList() ?: emptyList()
    val currentSessionId = service?.currentSession?.value?.first
    // ...
    entries.forEach { (sessionId, meta) ->
        val selected = sessionId == currentSessionId
        InputChip(
            selected = selected,
            onClick = { onSessionSelected(sessionId) },
            modifier = Modifier.combinedClickable(
                onClick = { onSessionSelected(sessionId) },
                onLongClick = { onRenameSession(sessionId) }
            ),
            label = { Text(meta.name) },
            trailingIcon = /* unchanged close button */
        )
    }
}
```

### T4.5 Rename dialog wiring in `TerminalScreen.kt`
Reuse `InputDialog`. Add state + dialog, and pass `onRenameSession` to both `SessionTabBar` calls:

```kotlin
var renameTargetId by remember { mutableStateOf<String?>(null) }
var renameText by remember { mutableStateOf("") }

renameTargetId?.let { id ->
    InputDialog(
        title = "Rename session",
        inputLabel = "Name",
        inputValue = renameText,
        onInputValueChange = { renameText = it },
        onConfirm = {
            sessionBinder?.renameSession(id, renameText)
            renameTargetId = null
        },
        onDismiss = { renameTargetId = null }
    )
}
// in SessionTabBar(...) call:
onRenameSession = { id ->
    renameText = sessionBinder?.getService()?.sessionList?.get(id)?.name ?: ""
    renameTargetId = id
}
```

### T4.6 Optional light persistence
If trivial: persist `id→name` in `Settings` (DataStore) so a rebind keeps names. If it adds a new storage
format, skip — spec says in-memory is acceptable for the service lifetime.

**Verify:** `grep -rn "sessionList\[" core/main/src` returns zero unfixed `Int` usages; `./verify.sh`;
push + `gh run list`. Commit:
`feat(session): SessionMeta display names + long-press rename + smart defaults (§4)`

---

## T5 — Lightweight perf instrumentation (Spec §5)

### T5.1 Frame-drop counter in `MainActivity`
**File:** `core/main/src/main/java/com/rk/shellix/ui/activities/terminal/MainActivity.kt`
Use `Choreographer.FrameCallback`; aggregate once/second into `AppLog` (never per-frame):

```kotlin
import android.view.Choreographer
import com.rk.shellix.ui.diagnostics.AppLog
import com.rk.shellix.ui.diagnostics.PerfStats

private var lastFrameNs = 0L
private var frameCount = 0
private var jankCount = 0
private var windowStartNs = 0L

private val frameCallback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
        if (lastFrameNs != 0L) {
            val deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000.0
            frameCount++
            if (deltaMs > 32.0) jankCount++ // > ~2 frames = visible jank
        }
        lastFrameNs = frameTimeNanos
        if (windowStartNs == 0L) windowStartNs = frameTimeNanos
        if (frameTimeNanos - windowStartNs >= 1_000_000_000L) {
            val pct = if (frameCount > 0) jankCount * 100 / frameCount else 0
            PerfStats.update(jankPercent = pct, frames = frameCount)
            if (pct > 0) AppLog.v("Perf", "jank ${pct}% (${jankCount}/${frameCount} frames >32ms)")
            frameCount = 0; jankCount = 0; windowStartNs = frameTimeNanos
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}

// onResume(): Choreographer.getInstance().postFrameCallback(frameCallback)
// onStop():  Choreographer.getInstance().removeFrameCallback(frameCallback); lastFrameNs = 0; windowStartNs = 0
```

### T5.2 `PerfStats` holder + session telemetry
**New file:** `core/main/src/main/java/com/rk/shellix/ui/diagnostics/PerfStats.kt`

```kotlin
package com.rk.shellix.ui.diagnostics

object PerfStats {
    @Volatile var lastJankPercent: Int = 0; private set
    @Volatile var lastFrameCount: Int = 0; private set
    @Volatile var activeSessions: Int = 0
    @Volatile var backgroundRenderPaused: Boolean = false

    fun update(jankPercent: Int, frames: Int) {
        lastJankPercent = jankPercent; lastFrameCount = frames
    }
}
```
Set `PerfStats.activeSessions` in `SessionService` on create/terminate; set
`PerfStats.backgroundRenderPaused = true` when a background tab redraw is throttled in T1.1.

### T5.3 Diagnostics UI "Performance" section
**File:** `core/main/src/main/java/com/rk/shellix/ui/diagnostics/DiagnosticsScreen.kt`
Add a `PreferenceGroup(heading = "Performance")` inside `DiagnosticsSection` showing:
- `PerfStats.lastJankPercent`% last-second frame drop
- `PerfStats.activeSessions` session count
- render-pause state (`PerfStats.backgroundRenderPaused`)
Read-only `PreferenceTemplate` rows (recompose on open is fine — no hot-path cost).

**Verify:** `./verify.sh`; push + `gh run list`. Commit:
`feat(diagnostics): frame-drop counter + PerfStats + Performance section (§5)`

---

## T6 — Verification & rollout (Spec §6)

### T6.1 Local + CI gates
- `./verify.sh` fully green (fix ALL reported issues at once).
- Push; watch `gh run list --limit 3`; `verify.yml` (unit + lint) must be green; fix + re-push on failure;
  `gh run delete <id>` for failed runs.

### T6.2 Device perf check (real device, not CI)
- Open 5 sessions; run `npm install`/`pnpm install` in one; switch tabs, open drawer, type.
- Capture `perfetto`/`systrace` before vs after; target no UI freeze > ~100ms.
- Confirm `logcat` no longer spams `proot warning: ptrace(PEEKDATA): No such process` at WARN.
- Confirm long-press rename works and default names read "Session N".

### T6.3 Version bump + changelog
**File:** `app/build.gradle.kts` → `versionCode = 4`, `versionName = "1.3.0"`.
Update `changelog.md` + `ROADMAP.md` (mirror README roadmap): Added (rename, perf instrumentation),
Fixed (ptrace spam, jank under load), Perf (redraw coalescing, nice priority, visibility-aware render).

### T6.4 Draft release, test, publish
Per AGENTS.md release flow: draft release with the signed `Shellix-v1.3.0.apk`, test the 5-tab scenario
on device, then publish + sync wiki `Changelog.md`.

**Commit:** `release: bump v1.3.0 (perf+audit+polish) + changelog/ROADMAP (§6)`

---

## Self-review checklist (before execution)
- [ ] Every spec Section (1–6) has a task. §1→T1, §2→T2, §3→T3, §4→T4, §5→T5, §6→T6.
- [ ] `SessionMeta.name` used consistently across SessionService, TerminalViewModel, SessionTabBar, TerminalScreen.
- [ ] No `sessionList[...]` left reading an `Int` (must be `.mode`).
- [ ] No blanket ptrace filter — only exact `PEEKDATA` + `No such process`.
- [ ] `--link2symlink` kept ON (research-backed); no default behavior change that could break installs.
- [ ] Instrumentation aggregates once/second — no per-frame allocation/logging.
- [ ] No placeholder code; all file paths exist as referenced.
