# Shellix

[![Build](https://github.com/keyreylaa/Shellix/actions/workflows/android.yml/badge.svg)](https://github.com/keyreylaa/Shellix/actions/workflows/android.yml)
[![Verify](https://github.com/keyreylaa/Shellix/actions/workflows/verify.yml/badge.svg)](https://github.com/keyreylaa/Shellix/actions/workflows/verify.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/keyreylaa/Shellix/blob/master/LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%206.0%2B-green.svg)](https://github.com/keyreylaa/Shellix)
[![Version](https://img.shields.io/badge/version-1.3.1-blue.svg)](https://github.com/keyreylaa/Shellix/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg)](https://kotlinlang.org)
[![Ubuntu](https://img.shields.io/badge/Ubuntu-24.04%20Noble-E95420.svg)](https://ubuntu.com)
[![PRoot](https://img.shields.io/badge/sandbox-PRoot-orange.svg)](https://proot-me.github.io)

**A pocket Linux workstation.** Shellix boots a real **Ubuntu 24.04 (Noble Numbat)**
environment on your Android phone via PRoot — no root, no VPS — wrapped in a fast,
Material 3 interface with a built‑in file manager and code editor.

## Why Shellix?

Android ships a locked‑down shell with no package manager, no real filesystem tools,
and no dev environment. Shellix fixes that: it gives you a genuine `apt`‑powered Ubuntu
userland where you can install tools, write and run scripts, and browse/edit files —
all on a device you already carry. It targets learners, tinkerers, and developers who
want a Linux scratchpad in their pocket, with the UI staying smooth even while a heavy
job runs in the background.

> Shellix runs Ubuntu over PRoot on top of the Android kernel. It is ideal for learning
> Linux, shell scripting, and light development. Heavy compilation, GPU workloads, and
> low‑port networking still benefit from a rooted device or a VPS.

## Highlights

- **Real Ubuntu 24.04 Noble** rootfs, downloaded on first run (SHA‑256 verified), with a
  first‑boot **setup wizard** that creates a `sudo` user.
- **Snappy under load** — terminal redraws are coalesced to one per frame, background
  session tabs are render‑throttled, and PRoot is launched deprioritized (`nice` +
  `ionice`) so the UI stays fluid while builds/installs grind. A built‑in **frame‑drop
  counter** (Diagnostics → Performance) makes this measurable.
- **Built‑in File Manager** — browse both realms (Ubuntu rootfs *and* phone storage),
  copy/cut/paste/rename/delete, multi‑select, create files/folders, with a clear
  `noexec` warning when you're in FUSE phone storage.
- **Built‑in Code Editor** — line numbers, save, binary‑file detection, and lightweight
  syntax highlighting for ~20 languages (Kotlin, Python, JS/TS, C/C++, Rust, Go, shell,
  YAML/JSON/TOML, and more) — all without bloating the APK or touching the terminal
  render path.
- **Unified Theme settings** — one place for interface theme (Light/System/Dark, OLED,
  Material You) and terminal color scheme. Ships a soft, low‑glare **Soft Dark** default.
- **Multiple renameable sessions** — long‑press a tab to rename; smart `Session N` names.
- **Packages tab** — browse/search APT packages and install/uninstall/update with a
  safety preview.
- **Voice input, custom wallpaper, custom font, Keep‑screen‑on**, configurable keyboard
  shortcuts, and an **About** screen that renders the live project Wiki.
- **Two‑way Folder Sync** — pair an Ubuntu folder with phone storage and keep them in
  sync (last‑write‑wins, conflict files preserved). Runs on a battery‑safe 15‑min
  `WorkManager` cycle with a manual "Sync now".
- **PC‑style screenshot** — capture the active terminal as a macOS‑style window
  (traffic‑light dots, real ANSI colors, your active theme) at phone or a sharp
  1440px desktop resolution; saved to `Pictures/Shellix` and shareable instantly.
- **Two‑step verification** — optional confirmation tap before clearing the terminal
  or closing a session, so destructive actions can't be triggered by accident.
- **Proactive crash notice** — if the app crashed last run, you're offered the report on
  next launch (once per crash), backed by an in‑app diagnostics log you can copy.

## Architecture

Shellix is a Gradle multi‑module Android app (Kotlin + Jetpack Compose + Termux
terminal engine). Modules are split so heavy or optional features stay isolated:

```
Shellix/
├── app/                     # Application module (manifest, signing, entry Activity)
└── core/
    ├── main/                # Terminal UI, sessions, packages, settings, About, nav
    ├── components/          # Reusable Compose preference/UI components
    ├── resources/           # Strings, drawables, shared resources
    ├── proot/               # Native PRoot engine (C/JNI) + launch scripts
    └── filemanager/         # Self-contained File Manager + Sora-based code editor
```

Key design points:

- **PRoot** (`core/proot`, native C) provides the Ubuntu sandbox; launch args live in
  `core/main/.../assets/init-host.sh`. The terminal is Termux's `terminal-view` /
  `terminal-emulator` (AAR).
- **Render hot path is guarded**: `TerminalBackEnd.onTextChanged` coalesces redraws per
  frame, skips background tabs, and reads the active session via a cached id — no Compose
  state churn per output chunk.
- **`core:filemanager` is dependency‑isolated**: it owns the Sora Editor (LGPL) and its
  highlighter, and does **not** depend on `core:main`, so the editor stack never leaks
  into the terminal path. `core:main` wires it into navigation.
- **Single source of truth for docs**: the About screen renders the GitHub Wiki `Home.md`
  live, so in‑app help follows the Wiki without manual edits.

## Getting Started

1. **Download the APK** from [GitHub Releases](https://github.com/keyreylaa/Shellix/releases).
2. Install it (you may need to allow "Install from unknown sources").
3. On first launch, Shellix downloads the Ubuntu Noble rootfs (needs internet), extracts
   it (pure‑Kotlin, no external `tar`), and opens the **setup wizard**.
4. Choose a username/password for your `sudo` user (default user `shellix`, with an
   auto‑generated password shown once — save it).
5. You boot into Ubuntu bash. Run `sudo apt update && sudo apt upgrade` to begin.

> Setup trouble? The error screen shows a **copyable** message (tap **Copy error**).
> Usually it means an incomplete download or full storage.

## Build from source

- Requires Android SDK + **JDK 17** (the Gradle daemon JVM must be 17; 21 currently
  breaks the build).
- `./gradlew assembleDebug` / `assembleRelease` — or use GitHub Actions.
- **CI**: `.github/workflows/verify.yml` runs unit tests + lint on every push;
  `.github/workflows/android.yml` builds and signs the release APK on `master`.
- **Local pre‑flight**: `./verify.sh` — a fast (<a few seconds) static simulation that
  catches many build‑only failures without a full compile: module graph / project deps,
  core‑library‑desugaring consistency, version‑catalog references, cross‑module import
  boundaries, known‑bug regression guards, and shell syntax. Structured output with a
  pass/warn/fail summary and proper exit codes. Set `VERIFY_KOTLIN=1` for a kotlinc probe.

## Roadmap

**v1.0.0 – v1.1.0** (shipped)
- [x] Ubuntu 24.04 Noble rootfs + first‑run setup wizard (sudo user)
- [x] Pure‑Kotlin tar.gz extraction (no external `tar`)
- [x] Packages tab, Clear terminal, Voice input, Dracula theme + Shift key
- [x] App Theme controls, rich package detail sheet, `verify.sh` + CI

**v1.2.0** (shipped) — build/size + security hardening
- [x] R8 minify/shrink + full mode, arm64‑only ABI, en‑only resources
- [x] Quoted `UbuntuCommand`, no plaintext password at rest

**v1.3.0‑beta** (this release)
- [x] Performance: per‑frame redraw coalescing, background‑tab throttling, off‑main
      command polling, `nice`/`ionice` PRoot launch, frame‑drop diagnostics
- [x] Session rename + smart default names
- [x] Unified Theme settings + **Soft Dark** default scheme
- [x] **File Manager** (dual‑realm browse, full file ops, multi‑select, noexec warning)
- [x] **Code Editor** (line numbers, save, binary detection, ~20‑language highlighting)
- [x] **About** screen (version, lineage, links, live Wiki) + **proactive crash notice**
- [x] Keep‑screen‑on toggle, copyable app logs
- [x] `ptrace(PEEKDATA)` log‑noise demoted; hardened `verify.sh`

**Blocked / deferred**
- [ ] Inline terminal image rendering (sixel) — needs a sixel‑capable `terminal‑emulator`
      AAR upgrade (risky; revisit when upgrading Termux libs).
- [ ] Full‑grammar (TextMate/TreeSitter) editor highlighting — deferred to keep the APK
      small and the terminal render path untouched (current highlighter is heuristic).

## Credits

- Fork of [ReTerminal](https://github.com/RohitKushvaha01/ReTerminal), itself a fork of
  [Termux](https://github.com/termux/termux-app).
- Terminal engine: Termux `terminal-view` / `terminal-emulator`.
- Code editor: [Sora Editor](https://github.com/Rosemoe/sora-editor) (LGPL‑2.1).
- Sandbox: [PRoot](https://proot-me.github.io).

## License

Shellix is released under the **MIT License** (see [LICENSE](LICENSE)). The bundled Sora
Editor is LGPL‑2.1; it is used as an unmodified library dependency.
