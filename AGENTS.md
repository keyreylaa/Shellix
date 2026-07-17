# AGENTS.md

Compact guidance for agents working in the **Shellix** repo (Android/Kotlin, a rebrand of the Termux/ReTerminal fork).

## Build & verification (critical)
- **There is NO local gradle/Android SDK in this environment.** Do not attempt `./gradlew`, `gradle`, or any Android build locally — Gradle 9.4.1 is broken here and the SDK is absent.
- **Verify builds via GitHub Actions only.** Push to `master`; the `Android CI` workflow (`.github/workflows/android.yml`) runs `./gradlew assembleRelease`. Watch it with `gh run list` / `gh run view <id> --log-failed`.
- CI builds release APK and uploads it as the `Shellix-Release` artifact. APK is copied to `app/shellix-<short-sha>.apk` then (previously) posted to Telegram — **the Telegram step was removed** (it sent to a non-owner channel). Do not re-add it.

## Repo layout & naming
- App module: `app/`. Core library: `core/main/` (namespace `com.rk.shellix`), `core/proot/`, `core/components/`, `core/resources/`.
- `applicationId` is `com.shellix.terminal` (independent of the Kotlin namespace).
- App screen packages live under `core/main/.../ui/screens/{terminal,settings,customization,downloader,packages}`.
- Navigation: routes in `ui/routes/MainActivityRoutes.kt`, wired in `ui/navHosts/MainActivityNavHost.kt`, drawer items in `ui/screens/terminal/TerminalDrawer.kt`, top-bar actions in `TerminalTopBar.kt`.

## Hard-won facts (do not repeat these mistakes)
- **Namespace mismatch**: Kotlin `R`/`BuildConfig` are generated from the gradle `namespace` (`com.rk.shellix`), NOT from `applicationId`. If you rename packages, keep the gradle `namespace` equal to the source package, or you get `Unresolved reference 'R'`.
- **Do not run `tar` as an external binary on Android.** `extractTar()` in `SetupScreen.kt` must use the pure-Kotlin `commons-compress` reader (added dep `org.apache.commons:commons-compress:1.26.0`). External `tar` is unreliable/absent on Android hosts and was the cause of "Failed to extract ubuntu.tar.gz".
- **Ubuntu session runs as non-root `shellix` user** (PRoot launched with `-0`, then `init.sh` does `exec su - shellix`). `shellix` has `NOPASSWD: ALL` sudo, so every apt command from app code must be `sudo`-prefixed.
- **Running commands inside Ubuntu = write to the active `TerminalSession` + capture output.** There is no `runCommand(): String` helper. `UbuntuCommand.run(...)` writes `echo MARKER; sudo <cmd>; echo MARKER` and polls `emulator.getScreen().getTranscriptText()` between the two unique markers. Only one command at a time.
- **No Composable input field for the terminal.** Voice/keyboard input goes to the emulator via `session.emulator.paste(text)` (see `VoiceInput.kt` and Clear action in `TerminalScreen.kt`).

## Conventions
- Commit small, push frequently (CI is the only build check).
- Internal tooling (`.kilo/`, `.agents/`, `docs/superpowers/`, `shellix_rebrand.sh`) is gitignored — never commit it. Specs/plans go in `docs/specs/` and `docs/plans/` (NOT `docs/superpowers/`).
- Keep Compose UI lightweight (user wants a snappy app).
- Use the `java-docs` skill conventions (KDoc/Javadoc) for new public helpers.

## Release
- Tag format `vMAJOR.MINOR.PATCH` (e.g. `v1.0.0`), pushed with `git push origin v1.0.0`.
- GitHub Release is created from the green CI run's APK artifact. Asset name is `Shellix-vX.Y.Z.apk`.
- Delete failed CI runs (`gh run delete <id>`) so the Actions list stays clean.
