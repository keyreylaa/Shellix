# Shellix Themes, Background, & Diagnostics Plan

> **For agentic workers:** Use `superpowers:brainstorming` + `superpowers:grill-me` to pressure-test before implementing. Implement task-by-task (see `executing-plans` / `subagent-driven-development`). Each task is independently verifiable via CI.

**Goal:** (1) Background image supports ALL Android image formats on ALL API levels via Coil; (2) Terminal color themes ship as presets (Nord, One Dark, Tokyo Night, Catppuccin Mocha, Dracula, Default) that apply **live without restarting the session/app**; (3) Diagnostics — in-app error notifications, plugin/extension error notifications, system crash-report notification, and a debug-log viewer in Settings.

## Research Summary (Context7 + Tavily, 2026-07-17)

### Image formats (Context7: Android `BitmapFactory` / `ImageDecoder`)
| Format | Native support |
|--------|---------------|
| JPEG, PNG | API 1+ |
| GIF (static) | API 1+ |
| WebP | API 14+ (lossless 18+) |
| HEIF / HEIC | **API 28+** (device-dependent) |
| AVIF | **API 31+** (else needs library) |
| SVG, TIFF | NOT native (need decoder) |

- `BitmapFactory.decodeFile()` (current code) is API-universal but **does not** support HEIC/AVIF and silently returns null on corrupt/unusual files.
- `ImageDecoder.createSource()` (API 28+) supports HEIF/WebP/animated GIF but is unavailable below 28.
- **Coil** (`coil-compose`, `coil-svg` already in `libs.versions.toml`) handles every Android-native format, `content://` URIs (FileUriFetcher), and has extensible `Decoder`s for SVG/GIF. Backward-compatible to low minSdk. `AsyncImage(model = file)` / `rememberAsyncImagePainter` + `ImageLoader.execute()` → `ImageResult.image.asImageBitmap()`.

### Live theme application (Context7: Material `DynamicColors`, `ColorScheme`)
- `MaterialTheme` colorScheme is read at composition; changing it requires recomposition, not `Activity.recreate()`.
- Current blocker: `TerminalThemes.applyDracula()` writes `colors.properties` but `Customization.kt` shows toast *"Restart session to see colors"* — the `TerminalView` ANSI palette is only re-read by `TerminalViewLayout` `update`/`post` block.
- Termux `TerminalView` exposes `mEmulator.mColors.mCurrentColors[256..258]` (bg/fg/accent) — already mutated in `TerminalViewLayout` `post{}` and `update{}`. To apply live: re-run that block after writing the new `colors.properties` (no session restart needed).

### Terminal color schemes — 16-ANSI hex (Tavily, base16/community sources)
`colors.properties` schema already used: `background`, `foreground`, `cursor`, `color0..color15`.
Reference values (verified from Catppuccin Mocha forum + MOLTamp 2026 ranking; Nord/One Dark/Tokyo Night BG+FG from MOLTamp, full 16-color to be filled from base16 sources at implementation time):

- **Dracula** (existing): bg `#282a36` fg `#f8f8f2`.
- **Nord**: bg `#2e3440` fg `#d8dee9`.
- **One Dark**: bg `#282c34` fg `#abb2bf`.
- **Tokyo Night**: bg `#1a1b26` fg `#a9b1d6`.
- **Catppuccin Mocha**: bg `#1e1e2e` fg `#cdd6f4`; accents: rosewater `#f5e0dc`, flamingo `#f2cdcd`, pink `#f5c2e7`, mauve `#cba6f7`, red `#f38ba8`, peach `#fab387`, yellow `#f9e2af`, green `#a6e3a1`, teal `#94e2d5`, sky `#89dceb`, blue `#89b4fa`, lavender `#b4befe`.

> Full `color0..15` for Nord/One Dark/Tokyo Night/Catppuccin to be pulled from base16 repositories at implementation (mini.base16 / tinted-theming). The 16-color block is mandatory — `color0..15` drive the shell prompt and `ls`/`grep` colors.

### Diagnostics (Tavily: crash logging best practices)
- Default Android `UncaughtExceptionHandler` prints stack to Logcat then kills process. To surface to user: install a custom `Thread.setDefaultUncaughtExceptionHandler` in `Application.onCreate` that (a) writes the stack to an in-app ring buffer / file under `filesDir/`, (b) shows a notification "Shellix crashed — tap to view report", (c) delegates to the default handler so the process still terminates cleanly.
- In-app log viewer: read `Logcat` via `Runtime.getRuntime().exec("logcat -d")` (or a `logcat` `Process`) into a scrollable `LazyColumn` in Settings → Diagnostics. Filter by tag/level. Requires `READ_LOGS` only on debug builds (release can show app's own `Log` tags written via a custom `Tree`).
- Plugin/extension error notifications: every `try/catch` around plugin/extension load should emit a `toast` + a logged entry surfaced in the Diagnostics viewer; do not silent-fail.

## Global Constraints (inherit from terminal-enhancements plan)
- Package root `com.rk.shellix`; `core/main` namespace `com.rk.shellix`; app `applicationId` com.shellix.terminal (do not change).
- Keep Compose UI lightweight. No new heavy dependencies — Coil is already present.
- Build verification via GitHub Actions only; commit frequently, push so CI validates.
- Do not modify rebrand, Ubuntu migration, or CI/build files unless a task explicitly says so.

---

## File Structure
- Modify `core/main/.../ui/screens/customization/Customization.kt` — BackgroundSection (Coil), ThemeSection (presets + live apply), DiagnosticsSection.
- Modify `core/main/.../ui/screens/terminal/TerminalScreen.kt` — `BackgroundImage` uses Coil `AsyncImage`.
- Modify `core/main/.../ui/screens/terminal/TerminalThemes.kt` — add preset definitions (Nord/One Dark/Tokyo Night/Catppuccin) + live-apply helper.
- Modify `core/main/.../ui/screens/terminal/TerminalViewLayout.kt` — expose `applyColorScheme()` callable after theme change.
- Create `core/main/.../ui/screens/terminal/TerminalColorSchemes.kt` — data class holding 16+ colors per preset.
- Create `core/main/.../ui/diagnostics/Diagnostics.kt` — log viewer + crash handler wiring.
- Modify `core/main/.../App.kt` — install `UncaughtExceptionHandler` + custom `Log` tree.
- Modify `core/main/.../ui/screens/customization/Customization.kt` (Theme section) — separate "Terminal Theme" group as requested ("kasih tempat terpisah").

---

### Task 1: Background image — Coil, all formats, all API levels
- [ ] Step 1: In `BackgroundSection`, replace `BitmapFactory.decodeFile` with Coil:
  - Preview: `AsyncImage(model = imageFile, contentDescription = null, error = painterResource(...))`.
  - On pick success: `ImageLoader(context).execute(ImageRequest.Builder(context).data(imageFile).build()).image?.asImageBitmap()?.let { viewModel.bitmap = it }`.
  - Keep the existing `toast` error paths; Coil `onError` adds a precise message.
- [ ] Step 2: In `TerminalScreen.BackgroundImage`, replace `Image(bitmap = ...)` with `AsyncImage(model = viewModel.bitmapFile /* store File not just ImageBitmap */, ...)`. Keep blur logic: if `backgroundBlur > 0` and API < S → `blurBitmap()` on the resolved `ImageBitmap`; if API >= S → `Modifier.blur()`.
- [ ] Step 3: Add `bitmapFile: File?` to `TerminalViewModel` alongside `bitmap` so `AsyncImage` can reload after theme/format change without manual decode.
- [ ] Step 4: Commit + push; CI must compile. Verify on device: pick a HEIC (API28+) and a WebP — both load.

### Task 2: Terminal color presets (Nord / One Dark / Tokyo Night / Catppuccin / Dracula / Default)
- [ ] Step 1: Create `TerminalColorSchemes.kt` with a `data class Scheme(val name: String, val bg: String, val fg: String, val cursor: String, val c0..c15: String)`.
- [ ] Step 2: Fill `Scheme` objects from research hex (full 16-color from base16 sources at impl time).
- [ ] Step 3: Refactor `TerminalThemes` to write the chosen scheme's `colors.properties` (reuse existing format).
- [ ] Step 4: Add `applyLive(activity)` that, after writing the file, calls `TerminalViewLayout.applyColorScheme()` to re-read `mColors.mCurrentColors` on the active session — **no session restart**.
- [ ] Step 5: In `Customization`, add a dedicated "Terminal Theme" `PreferenceGroup` listing the presets as `FilterChip`/`ListItem`; clicking applies live + toast "Applied".
- [ ] Step 6: Commit + push; verify live apply on device (no restart needed).

### Task 3: Diagnostics — error notifications + crash report + log viewer
- [ ] Step 1: `App.onCreate` installs `Thread.setDefaultUncaughtExceptionHandler` → writes stack to `filesDir/crash_report.txt` + posts a high-priority notification "Shellix crashed — tap to view".
- [ ] Step 2: Custom `Log` tree writes app logs to an in-memory ring buffer (cap ~2000 lines) for the viewer.
- [ ] Step 3: `Diagnostics.kt` — Settings screen with: (a) Log viewer (`LazyColumn` from ring buffer, filter by level/tag), (b) "Last crash report" viewer + "Share" button, (c) "Notify on plugin/extension errors" toggle (default on) that routes plugin `catch` blocks to notification + ring buffer.
- [ ] Step 4: Wire plugin/extension load `try/catch` sites to the diagnostics logger (do not silent-fail).
- [ ] Step 5: Commit + push; verify: force a crash (throw in a button) → notification appears, report viewable, logs visible in Settings.

### Task 4: Separate Theme settings section (polish)
- [ ] Step 1: In `Customization`, split "App Theme" and "Terminal Theme" into clearly separated `PreferenceGroup`s with headers, as requested ("kasih tempat terpisah").
- [ ] Step 2: Commit + push.

---

## Open Questions (grill-me before implementing)
1. Should background presets and terminal color presets be linked (pick Nord → also sets Nord bg suggestion)? Or independent?
2. Crash notification on release builds — keep it (privacy: report stays local, only shared via "Share")?
3. Log viewer scope: full Logcat (needs READ_LOGS, debug only) or just app's own tagged logs (release-safe)?
4. Should theme presets persist per-session (ties to Roadmap item #3) or global?

## Self-Review Notes
- Research used Context7 (Android dev docs, Coil) + Tavily (scheme hex, crash best practices) — no blind web search.
- Coil already a dependency → no new lib.
- Live theme apply reuses the EXISTING `mColors.mCurrentColors` mutation path; no new terminal internals.
- All tasks independently CI-verifiable; commit frequently.
