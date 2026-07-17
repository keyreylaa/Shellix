# Shellix

[![Build](https://github.com/keyreylaa/Shellix/actions/workflows/android.yml/badge.svg)](https://github.com/keyreylaa/Shellix/actions)
[![Verify](https://github.com/keyreylaa/Shellix/actions/workflows/verify.yml/badge.svg)](https://github.com/keyreylaa/Shellix/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/keyreylaa/Shellix/blob/master/LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://github.com/keyreylaa/Shellix)
[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/keyreylaa/Shellix/releases)
[![PRoot](https://img.shields.io/badge/sandbox-PRoot-orange.svg)](https://github.com/keyreylaa/Shellix)
[![Ubuntu](https://img.shields.io/badge/Ubuntu-24.04%20Noble-E95420.svg)](https://github.com/keyreylaa/Shellix)

A Material 3 terminal emulator for Android that boots a real Ubuntu 24.04 Noble environment via PRoot.

**Shellix** is a sleek, Material 3 terminal emulator for Android that boots a real **Ubuntu 24.04 (Noble Numbat)** Linux environment via PRoot — not just a plain Android shell. Built on Termux's battle-tested TerminalView.

> Note: Shellix runs Ubuntu over PRoot on top of the Android kernel. It is ideal for learning Linux, shell scripting, and light development. Heavy compilation, GPU workloads, and low-port networking need a rooted device or a VPS.

## Features
- **Ubuntu 24.04 Noble** rootfs downloaded on first run (SHA-256 verified), with a first-boot **setup wizard** that creates a sudo user.
- **Material 3** UI with light/dark/OLED and **Dynamic Color (Material You)** themes.
- **App Theme controls** (Customization → App Theme): Light/System/Dark toggle, OLED mode, Monet toggle.
- **Dracula terminal theme** preset (plus default), switchable in Customization.
- **Virtual keys** including a **Shift** key, Ctrl, Alt, arrows, and function keys.
- **Multiple sessions** managed from a navigation drawer + session tab bar (bottom strip).
- **Configurable keyboard shortcuts** (paste, new/close/switch session).
- **Packages tab** — browse installed APT packages, search apt-cache, install/uninstall/update with safety preview.
- **Voice input** — mic button in top bar, speech-to-text pasted into terminal (editable before send).
- **Clear terminal** — one-tap clear from drawer.
- **Custom wallpaper background** — pick your own image with live blur + alpha sliders.
- **Wake-lock** — screen stays on while a terminal session is active.
- **Full storage access** via PRoot bind mounts + `MANAGE_EXTERNAL_STORAGE`.

## Getting Started
1. **Download the APK**: [Shellix-v1.1.0.apk](https://github.com/keyreylaa/Shellix/releases/download/v1.1.0/Shellix-v1.1.0.apk) (or grab the latest from [GitHub Releases](https://github.com/keyreylaa/Shellix/releases)).
2. Install it (you may need to allow "Install from unknown sources" in Android settings).
3. On first launch, Shellix downloads the Ubuntu Noble rootfs (needs internet), extracts it, and opens a **setup wizard**.
4. Enter a username and password for your sudo user (defaults: user `shellix`, auto-generated password shown once — save it! You can also type your own password).
5. You boot into Ubuntu bash as that user. Run `sudo apt update && sudo apt upgrade` to get started.

> Having trouble? If setup fails to extract the rootfs, the error screen shows a **copyable** message (tap **Copy error** and share it) — usually it means the download was incomplete or storage is full.

## Build from source
- Requires Android SDK + JDK 17.
- `./gradlew assembleDebug` (or use the GitHub Actions workflow).
- The app is built via GitHub Actions (`.github/workflows/android.yml`). Unit tests run separately in `.github/workflows/verify.yml`.
- Local verification: `./verify.sh` (checks file structure, known bug patterns, syntax).

## v1.1.0 Changes
- **Fix: User switch after setup** — first-boot user creation now correctly drops into the non-root user immediately (init.sh re-checks `/etc/shellix_default_user` after setup-user.sh runs).
- **Fix: Wallpaper visibility** — default wallpaper alpha changed from 0 (invisible) to 1 (fully opaque).
- **Fix: Package list parsing** — UbuntuCommand marker detection off-by-one fixed (was capturing command echo instead of actual output). All packages now properly appear.
- **Fix: App Theme controls** — Added Light/System/Dark toggle, OLED mode, and Monet (Material You) toggles in Customization.
- **Improve: Package detail view** — tapping a package opens a rich bottom sheet with name, section, full description, version, Close button, and Install/Uninstall buttons.
- **New: Verification suite** — `verify.sh` checks all file structure, known bug patterns, shell syntax, and Kotlin syntax. `verify.yml` workflow runs Gradle unit tests on CI.
- **Version bump** — 1.0.0 → 1.1.0.

## Roadmap

**v1.0.0**
- [x] Ubuntu 24.04 Noble rootfs + first-run setup wizard (sudo user)
- [x] Pure-Kotlin tar.gz extraction (no external `tar` binary)
- [x] Packages tab (browse / install / uninstall / update apt packages)
- [x] Clear terminal action
- [x] Voice-to-command input (mic → terminal, editable before send)
- [x] Dracula terminal theme + Shift key
- [x] GitHub Wiki (Setup, Features, Changelog)

**v1.1.0**
- [x] Bugfix: user stays root after setup (now correctly drops to non-root)
- [x] Bugfix: wallpaper invisible by default (alpha default 0 → 1)
- [x] Bugfix: package list not showing (UbuntuCommand marker off-by-one)
- [x] App Theme controls (Light/System/Dark, OLED, Monet) in Customization
- [x] Rich package detail bottom sheet (description, section, close, install/uninstall)
- [x] Local verification suite (`verify.sh`) + CI verify workflow
- [x] Performance: SideEffect for virtual keys, key() for stable BackgroundImage

**Blocked / deferred**
- [ ] Inline terminal image rendering (sixel) — blocked: needs a sixel-capable `terminal-emulator` AAR upgrade (risky, no local build to verify). Revisit when upgrading Termux libs.

## Credits
- Fork of [ReTerminal](https://github.com/RohitKushvaha01/ReTerminal), which is a fork of [Termux](https://github.com/termux/termux-app).
- Terminal engine: Termux `terminal-view` / `terminal-emulator`.
- PRoot sandboxing.

## License
See repository license file.
