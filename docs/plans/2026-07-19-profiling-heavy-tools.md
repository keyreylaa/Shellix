# Profiling Plan — Heavy-Tool Lag in PRoot Sessions

**Goal:** Find the SPECIFIC bottleneck that causes UI lag / blank / freeze when a
heavy tool runs inside the PRoot Ubuntu session (large test suite, big compile/build
flooding the terminal with output). **No optimization code is written from this plan
— profiling only.** Every fix decision waits for the data collected below.

**Who runs it:** You, on a real device, via `adb` + the on-device Diagnostics screen.
No Android SDK / local build is needed for data collection — only `adb` (install
`android-tools-adb` or use any `adb` binary) and a USB/WiFi connection to the phone.

**App package:** `com.shellix.terminal`

---

## 0. Prerequisites (one time)

1. Enable **USB debugging** on the phone (Settings → Developer options).
2. On your computer: `adb devices` should list the phone. If empty, accept the
   authorization prompt on the phone.
3. Open Shellix, let the Ubuntu session fully start (terminal shows a prompt).
4. Note the **Diagnostics** screen location: open the app menu → Settings →
   Diagnostics → Performance. It shows **"Frame drops (last second)"**,
   **"Active sessions"**, and **"Background render"**. We read these before/after.

> Tip: keep a notes app or a text file open on your computer to paste the command
> outputs + the numbers you observe. Screenshot the Diagnostics screen too.

---

## 1. Baseline (idle, no heavy load)

Run this while Shellix is open but idle (nothing running in the terminal):

```bash
adb logcat -c                      # clear log buffer
adb logcat -s Perf:V &            # start streaming the frame-jank log
```

Wait ~10 seconds. You should see lines like:

```
Perf    : jank 0% frames=60 t=...
```

- Note the **jank %** (should be near 0) and **frames** (near 60).
- Open the in-app **Diagnostics → Performance** screen and screenshot the
  "Frame drops (last second)" value.

This is your healthy baseline. Anything dramatically worse during the heavy run is
the signal we care about.

> To stop the streaming log later: `Ctrl-C` in that terminal.

---

## 2. Start the heavy workload (on the phone)

In the Shellix Ubuntu terminal, start a representative heavy task — e.g. a real test
suite or a large compile that floods output. Example stand-ins if you don't have a
specific one:

```bash
# A CPU+output-heavy stand-in (adjust to your real workload):
yes | head -c 5000000 | tr 'y' 'x' | sort | uniq -c     # output flood
# or a real build / test suite you actually use
```

Keep the **terminal foreground** (don't switch apps) and the phone **unplugged from
charging throttling concerns** — just use it normally.

---

## 3. Collect the four signals (in parallel, during the run)

Open separate terminals on your computer and run these **while the heavy task runs**
and you observe lag/blank/freeze:

### 3a. Frame drops over time
```bash
adb logcat -s Perf:V
```
Watch `jank %` — record the highest value you see during a visible freeze, and the
`frames` count. Also screenshot the in-app Diagnostics "Frame drops" at the worst
moment.

### 3b. CPU split: app vs PRoot child
```bash
adb shell top -H -d 1 | grep -E "proot|bash|make|gradle|cc1|java|shellix|RenderThread|UnityMain|main"
```
(If `top -H` isn't supported, use `adb shell top -d 1` and look for the same names.)
- Identify the **PRoot / build PIDs** (e.g. `proot`, `bash`, `make`, `cc1`, `java`)
  and the **app's own threads** (e.g. `main`, `RenderThread`, anything under
  `com.shellix.terminal`).
- **Record:** during a freeze, which side is eating CPU? If `proot`/build PIDs are
  near 100%+ and the app's `main`/`RenderThread` is low → scheduler starvation
  (hypothesis **B**). If the app's own `main` thread is also high → it's doing the
  drawing work itself (hypotheses **A/C**).

### 3c. Frame timing (closest CLI proxy to Studio's frame timeline)
```bash
adb shell dumpsys gfxinfo com.shellix.terminal
```
Run this **after** ~20-30s of the heavy run. Look for:
- **"Janky frames"** and **"Total frames rendered"**.
- The **percentile frame times** (e.g. 50th/90th/99th percentile ms).
- **Record** the janky-frame percentage and the 99th-percentile frame time.

### 3d. GC pressure (memory churn)
```bash
adb shell dumpsys meminfo com.shellix.terminal
```
Run once at idle baseline and once during the heavy run. Also stream GC events:
```bash
adb logcat -b all | grep -i "GC_"
```
- **Record:** if you see a burst of `GC_` lines coinciding with freezes, or the
  `meminfo` "Java heap" grows a lot, that points to allocation pressure (a suspect is
  the `UbuntuCommand` transcript polling, though that only runs on the Packages
  screen — note if you had that screen open).

---

## 4. (Optional but best) System Tracing capture

On the phone: enable **Settings → Developer options → System Tracing** (or the
"System Tracing" app). Start a trace, perform the heavy run until a freeze happens,
stop the trace, and pull the `.perfetto-trace` file:

```bash
adb pull /data/local/traces/ .        # adjust path if your device differs
```

This trace lets us see, per thread, whether the app's **main thread** is:
- `Running` inside draw code (TerminalView.onDraw / onScreenUpdated) → hypotheses
  **A/C** (render/redraw cost), or
- `Sleeping` / preempted while `proot` is `Running` → hypothesis **B** (scheduler).

If you can produce this trace, it is the single most decisive piece of evidence.

---

## 5. What to report back

Send me, for analysis:

1. **Baseline** "Frame drops (last second)" number (idle).
2. **Worst** "Frame drops" number during the heavy run + a screenshot of the
   Diagnostics screen at that moment.
3. Output of **3b** (`top -H`) during a freeze — especially which PIDs are hot.
4. Output of **3c** (`dumpsys gfxinfo`) — janky-frame % and 99th-pct frame time.
5. Any **3d** GC burst observations.
6. (If available) the **Perfetto trace** file.
7. A short description: is the UI merely *late to render* but still responsive to
   taps, or does it *fully freeze* (no response to input)?

---

## 6. Hypotheses to confirm from the data

| # | Hypothesis | Confirmed if… | Refuted if… |
|---|---|---|---|
| **A** | Terminal output floods the UI thread faster than the existing 16 ms redraw debouncer can coalesce. | `onTextChanged` rate (see note) ≫ 60/s; high janky-frame %. | Redraw rate is modest; jank comes from elsewhere. |
| **B** | PRoot child does not yield CPU/I/O to the UI (nice/ionice dropped or insufficient vs ptrace overhead). | `proot`/build PIDs near 100%+ while app `main` is low; Perfetto shows main thread sleeping/preempted. | App main thread is itself Running/hot; freezes persist when build is I/O-bound. |
| **C** | `TerminalView.onDraw` text rasterization is expensive per frame, independent of output rate. | A single `onScreenUpdated`/`onDraw` costs >16 ms even at low output (Studio/Perfetto). | Draw cost small; jank tracks output *rate*, not draw duration. |

> **Note on `onTextChanged` rate:** the termux engine (`terminal-view` AAR) is an
> external dependency with no source in this repo, so its exact call frequency is not
> visible from code. To measure it, temporarily add a timestamp log inside
> `core/main/.../terminal/TerminalBackEnd.kt` `onTextChanged()` (e.g.
> `AppLog.v("Perf","onTextChanged dt=${SystemClock.uptimeMillis()-last}")`) and read it
> via `adb logcat -s Perf:V`. This is an *instrumentation* change only — no optimization.

---

## 7. Decision gate (after data is in)

Only after the measurements above label the dominant signal do we choose the fix
layer:

- **A / C with Kotlin headroom** → tune the debounce / batching in
  `TerminalBackEnd.kt` (pure Kotlin, in-repo).
- **termux engine internals** (reader chunk size / per-line `onTextChanged`) →
  needs a fork/patch of the `terminal-view` dependency (currently pinned at
  `v0.118.3` in `core/main/build.gradle.kts`).
- **B (PRoot CPU saturation)** → native C work in `core/proot/src/main/cpp/*`
  (e.g. scheduler policy) or OS-level `nice`/`cgroup` enforcement — native
  territory, not app Kotlin. Note PRoot is *already* launched with
  `nice -n 15` + `ionice -c2 -n7` in `core/main/src/main/assets/init-host.sh`,
  so first verify those are actually applied (`cat /proc/<pid>/stat`, field 18)
  before assuming they're missing.

**No code changes are made until this plan's data is collected and reviewed.**
