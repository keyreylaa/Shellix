# Roadmap

Planned features for Shellix. Items below are **not yet implemented** — they are design notes and will land across future releases. Priorities may shift based on feedback.

Architecture overview is at the bottom of this page so the rationale for each feature is easy to follow.

## 2. Favorite / Starred Packages
- A "star" action on any package in the Package Manager detail sheet.
- Starred packages persist in `Settings` (a `Set<String>` in the app prefs) and show in a dedicated **Favorites** tab, sorted by name.
- Plus a one-tap **"Update all"** button that runs `apt-get update && apt-get upgrade -y` without opening the confirm dialog (the current Update button already does this; this promotes it to a prominent, always-visible action).

## 3. Per-Session Background
- Each terminal session can have its own wallpaper, like WhatsApp chat wallpapers.
- Storage: `filesDir/bg_<sessionId>` (mirrors the existing `filesDir/background` single-image scheme). `TerminalViewModel.bitmap` becomes a `Map<sessionId, ImageBitmap>` keyed by the active session, or each session carries its own bitmap loaded in `changeSession()`.
- The background picker in Customization gets a "apply to: current session / all sessions" choice.
- Falls back to the global `filesDir/background` when a session has no specific image.

## 4. In-App File Manager
- A new screen (Compose `LazyColumn` + `androidx.documentfile` or direct `java.io.File` against the PRoot rootfs path) to browse `/home/shellix`.
- Long-press context menu: rename, copy, move, delete, share, "open in terminal" (writes `cd <path>` + Enter to the active `TerminalSession` via `emulator.paste`, same pattern as VoiceInput).
- Read/write goes through the **same filesystem the Ubuntu session sees** (the PRoot rootfs directory under `filesDir`), so edits are visible inside the terminal immediately.

## 6. Terminal Gestures
- **Edge swipe** from the left to open the navigation drawer (replaces the toolbar hamburger for toolbar-less mode).
- **Double-tap** to toggle zoom (reuses `TerminalView` text size; store zoom level in `Settings.terminal_font_size` or a separate scale pref).
- **Long-press** on the terminal to paste from clipboard (currently paste is only via the virtual-key row / drawer).
- Implemented with `androidx.compose.foundation.gestures.detectTapGestures` / `detectHorizontalDragGestures` wrapped around `TerminalViewLayout`, or `AndroidView.onTouchEvent` if Compose gestures don't reach the `TerminalView`.

## 8. Monet Auto-Accent from Wallpaper
- The background picker already computes a dominant color via `Palette` (used today only to decide light/dark status-bar text — `Settings.blackTextColor`).
- Extend it: when Monet is enabled, feed the dominant (or vibrant) swatch into a runtime `MaterialTheme` `colorScheme` so the **accent color follows the wallpaper**, like Android 12's wallpaper-based theming.
- Requires building a `DynamicColors` / custom `ColorScheme` from the extracted color and calling `ThemeManager.apply()` with the override.

## 9. Home-Screen Shortcuts / Quick Tiles
- **App shortcut** (static + dynamic) per saved session profile — long-press the app icon → jump straight into a named session.
- Optional **Quick Settings tile** that opens a chosen session or runs a saved command (e.g. `apt-get update`).
- Uses `androidx.core.content.pm.ShortcutManagerCompat` for shortcuts and `android.service.quicksettings.TileService` for the tile. Session list comes from `SessionService.sessionList`.

## 10. Terminal Syntax Highlighting (Beta)
- Highlight command names, flags, paths, and common tool output (apt, ls, git) in the `TerminalView` emulator.
- The termux `TerminalView` renders via `TerminalSession`/`TerminalRow`; highlighting means either (a) post-processing the transcript in `TerminalBackEnd.onScreenUpdated()` to assign colors, or (b) a lightweight regex pass before `paste`/`write`.
- Marked **experimental** because it can clash with programs that emit their own ANSI colors. Off by default behind a Settings toggle; report issues at [GitHub Issues](https://github.com/keyreylaa/Shellix/issues).

---

## Architecture Context

Understanding where these fit:

- **Session model**: `SessionService` owns `sessionList: Map<sessionId, TerminalSession>`. `TerminalViewModel` (activity-scoped) holds the active `terminalView`, `virtualKeysView`, `bitmap`, and theme floats (`wallAlpha`, `backgroundBlur`). Switching sessions calls `TerminalViewModel.changeSession()`.
- **Command execution**: there is **no direct `runCommand(): String`**. Anything that must run inside Ubuntu goes through `UbuntuCommand.run(sessionBinder, cmd)`, which writes the command to the active `TerminalSession` emulator, wraps it with unique `MARKER_xxx` echoes, and polls `emulator.getScreen().getTranscriptText()` to capture output between markers. Features 2/4/6/8/9/10 that need to run or inspect commands must reuse this path (one command at a time).
- **UI / settings**: all toggles persist in `Settings` (a `SharedPreferences`-backed object). New persisted state (favorites, per-session bg, zoom, highlight toggle) should be added there, never as in-memory-only state, so it survives restart.
- **Background rendering**: `TerminalScreen` draws `BackgroundImage` first inside a `Box`, then the terminal `Column` on top. The `TerminalView` itself is `TRANSPARENT` so the image shows through. Blur is guarded by API level (`RenderEffect` on S+, `BlurMaskFilter` fallback below).
- **Build / verification**: local `verify.sh` + GitHub Actions (`verify.yml` for tests/lint, `android.yml` for the signed APK). CI is the only build check — keep changes small and push frequently.
