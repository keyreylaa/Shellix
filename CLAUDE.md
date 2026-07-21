# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & verify

- **Pre-push:** `./verify.sh` — fast static check (module graph, desugaring consistency, lib refs, import boundaries, shell syntax). Set `VERIFY_KOTLIN=1` for kotlinc probe.
- **Kotlin-only check:** `./gradlew :core:main:compileDebugKotlin` (takes minutes, CI is faster)
- **CI:** `.github/workflows/verify.yml` (test + lint, every push); `.github/workflows/android.yml` (assembleRelease, master only)
- **Watch CI:** `gh run list` / `gh run view <id> --log-failed` / `gh run delete <id>`
- **JDK 17 required** for Gradle daemon (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64`). Override via env, never edit `gradle/gradle-daemon-jvm.properties`.

## Architecture

```
app/                    # Application module (manifest, signing)
core/
  main/                 # Terminal UI, sessions, nav (namespace com.rk.shellix)
  components/           # Shared Compose components
  resources/            # Strings, drawables
  proot/                # Native PRoot engine (C + JNI) + launch scripts
  filemanager/          # File Manager + Sora code editor (standalone, NO dep on core:main)
```

- **PRoot:** native C, ptrace-based sandbox. Launch in `assets/init-host.sh` (host-side args) → `assets/init.sh` (guest-side bootstrap). Ubuntu rootfs downloaded at first run.
- **Terminal:** Termux `terminal-view` + `terminal-emulator` AAR. Render coalesced per frame, background tabs throttled.
- **Navigation:** routes in `ui/routes/MainActivityRoutes.kt`, nav host in `ui/navHosts/MainActivityNavHost.kt`.
- **Screens:** `ui/screens/{terminal,settings,customization,downloader,packages,about}`.
- **Session commands:** write to active `TerminalSession` + poll transcript text (see `UbuntuCommand` marker pattern).
- **Filemanager boundary:** MUST NOT depend on `core:main`. Uses plain Android APIs for I/O.

## Hard constraints

- `applicationId` = `com.shellix.terminal` (NOT the Kotlin namespace `com.rk.shellix`). Gradle `namespace` drives `R`/`BuildConfig`.
- Core library desugaring (`isCoreLibraryDesugaringEnabled`) required in EVERY module that consumes Sora Editor: `app`, `core:main`, `core:filemanager`.
- Ubuntu session runs as non-root `shellix` user (PRoot `-0` → `init.sh` → `exec su - shellix`). All `apt` commands in app code must be `sudo`-prefixed.
- Always `command -v <util>` before calling `nice`/`ionice` — they may not exist on host Android.
- `tar` extraction from app code must use pure-Kotlin `commons-compress`, never external `tar` binary.

## Known bug patterns (do not repeat)

- **ESC literal bytes:** Kotlin compiler parses `` in source; literal 0x1b bytes cause syntax error. Use companion object `const val ESC = ""`.
- **snapshotFlow + collectAsState:** `collectAsState()` is `@Composable` — cannot be nested inside `remember {}`. Call `remember { snapshotFlow { ... } }.collectAsState()`.
- **talloc fork-safety:** After fork/CLONE_VM, use `talloc_unlink` not `TALLOC_FREE` — sibling tracees hold talloc references via `talloc_reference(child, parent->heap)`.
- **link2symlink ENOENT:** `decrement_link_count` must tolerate `ENOENT` on `unlink()` — npm/pnpm bulk `rm -rf` races the intermediate file before this handler runs.
- **WeakReference for Activity:** Use `java.lang.ref.WeakReference<SessionService.SessionBinder>` in ViewModel — never hold a hard reference that leaks across config changes.
- **SpeechRecognizer lifecycle:** Always `recognizer?.destroy(); recognizer = null` at the START of `toggle()` before creating a new instance.

## Release

1. Push to `master`. Wait for both workflows green.
2. `gh run download <RUN_ID> --name Shellix-Release --dir /tmp/apk`
3. `gh release create vX.Y.Z /tmp/apk/Shellix-vX.Y.Z.apk --title "Shellix vX.Y.Z" --notes "$(cat changelog.md)" --draft`
4. Test draft APK on device, then publish.
5. Keep changelog synced to GitHub Wiki `Changelog.md` + Release notes.
