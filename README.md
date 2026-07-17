# Shellix

[![Build](https://github.com/keyreylaa/Shellix/actions/workflows/android.yml/badge.svg)](https://github.com/keyreylaa/Shellix/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/keyreylaa/Shellix/blob/master/LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://github.com/keyreylaa/Shellix)
[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/keyreylaa/Shellix/releases)
[![PRoot](https://img.shields.io/badge/sandbox-PRoot-orange.svg)](https://github.com/keyreylaa/Shellix)
[![Ubuntu](https://img.shields.io/badge/Ubuntu-24.04%20Noble-E95420.svg)](https://github.com/keyreylaa/Shellix)
[![Download](https://img.shields.io/badge/download-APK-blue?logo=android)](https://github.com/keyreylaa/Shellix/releases/download/v1.0.0/Shellix-v1.0.0.apk)

A Material 3 terminal emulator for Android that boots a real Ubuntu 24.04 Noble environment via PRoot.

**Shellix** is a sleek, Material 3 terminal emulator for Android that boots a real **Ubuntu 24.04 (Noble Numbat)** Linux environment via PRoot — not just a plain Android shell. Built on Termux's battle-tested TerminalView.

> Note: Shellix runs Ubuntu over PRoot on top of the Android kernel. It is ideal for learning Linux, shell scripting, and light development. Heavy compilation, GPU workloads, and low-port networking need a rooted device or a VPS.

## Features
- **Ubuntu 24.04 Noble** rootfs downloaded on first run (SHA-256 verified), with a first-boot **setup wizard** that creates a sudo user.
- **Material 3** UI with light/dark/OLED and **Dynamic Color (Material You)** themes.
- **Dracula terminal theme** preset (plus default), switchable in Customization.
- **Virtual keys** including a **Shift** key, Ctrl, Alt, arrows, and function keys.
- **Multiple sessions** managed from a navigation drawer.
- **Configurable keyboard shortcuts** (paste, new/close/switch session).
- **Full storage access** via PRoot bind mounts + `MANAGE_EXTERNAL_STORAGE`.

## Getting Started
1. **Download the APK**: [Shellix-v1.0.0.apk](https://github.com/keyreylaa/Shellix/releases/download/v1.0.0/Shellix-v1.0.0.apk) (or grab the latest from [GitHub Releases](https://github.com/keyreylaa/Shellix/releases)).
2. Install it (you may need to allow "Install from unknown sources" in Android settings).
3. On first launch, Shellix downloads the Ubuntu Noble rootfs (needs internet), extracts it, and opens a **setup wizard**.
4. Enter a username and password for your sudo user (defaults: user `shellix`, auto-generated password shown once — save it!).
5. You boot into Ubuntu bash as that user. Run `sudo apt update && sudo apt upgrade` to get started.

> Having trouble? If setup fails to extract the rootfs, the error screen shows a **copyable** message (tap **Copy error** and share it) — usually it means the download was incomplete or storage is full.

## Build from source
- Requires Android SDK + JDK 17.
- `./gradlew assembleDebug` (or use the GitHub Actions workflow).
- The app is built and verified via GitHub Actions (`.github/workflows/android.yml`).

## Roadmap
- Packages tab (browse/install/uninstall apt packages with search).
- Voice-to-command input.
- Tab bar UI for sessions.
- Inline terminal image rendering.

## Credits
- Fork of [ReTerminal](https://github.com/RohitKushvaha01/ReTerminal), which is a fork of [Termux](https://github.com/termux/termux-app).
- Terminal engine: Termux `terminal-view` / `terminal-emulator`.
- PRoot sandboxing.

## License
See repository license file.
