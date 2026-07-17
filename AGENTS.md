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
- **Terminal background is a user image already.** `BackgroundImage` (TerminalScreen.kt:175-194) draws `filesDir/background` as a Compose `Image` behind a transparent `TerminalView` (`TerminalViewLayout.kt:42` sets `TRANSPARENT`). `wallTransparency` + `background_blur` settings drive alpha/blur. Image picker is in `Customization.kt:253-312` (copies chosen image to `filesDir/background`). To add a "wallpaper" feel, reuse this path — do not modify the termux `TerminalView` internals.
- **Custom font** loads from `filesDir/font.ttf` (Typeface.createFromFile), picker in `Customization.kt:204-251`. No bundled-font selector.

## Backlog (user-requested, not yet built)
- **Inline terminal image rendering (sixel)**: blocked — needs a sixel-capable `terminal-emulator` AAR upgrade (risky, no local build to verify).
- **Sources.list editor**: the Packages tab already has a read-only sources.list viewer; a full editor (toggle `#`, add repos) is future work.

## Release (how to make the APK downloadable)
Builds run only in GitHub Actions (no local gradle). To cut a release so users can download the APK:

1. **Push to `master`.** The `Android CI` workflow (`.github/workflows/android.yml`) runs `./gradlew assembleRelease`, signs the APK (debug keystore fallback when no real keystore secret), and uploads the artifact named `Shellix-Release`. The APK file is `app/shellix-<short-sha>.apk`.
2. **Wait for the green run** (`gh run list --limit 1`, then `gh run view <id> --log-failed` if red). Fix any compile errors and re-push. Delete failed runs with `gh run delete <id>` so the Actions list stays clean.
3. **Download the APK artifact** from the green run:
   ```
   gh run download <RUN_ID> --name Shellix-Release --dir /tmp/apk
   ```
   The file is `shellix-<sha>.apk`. Copy it to `Shellix-vX.Y.Z.apk` for the release asset.
4. **Tag the version** (semver, e.g. `v1.1.0`):
   ```
   git tag -a vX.Y.Z -m "Shellix vX.Y.Z - <short summary>"
   git push origin vX.Y.Z
   ```
5. **Create the GitHub Release** from the tag and attach the APK so it is downloadable:
   ```
   gh release create vX.Y.Z /tmp/apk/Shellix-vX.Y.Z.apk \
     --title "Shellix vX.Y.Z" \
     --notes "$(cat changelog.md)"
   ```
   Users then download from `https://github.com/keyreylaa/Shellix/releases/download/vX.Y.Z/Shellix-vX.Y.Z.apk`.
6. **Sync the changelog** in two places so they never drift:
   - The wiki `Changelog.md` at `https://github.com/keyreylaa/Shellix/wiki` (edit via the `Shellix.wiki.git` repo).
   - The GitHub Release notes (above `--notes`).
   Keep both detailed and structured (version, date, Added / Fixed / Build-CI). Mirror the README Roadmap checkboxes.

Notes:
- The old Telegram upload step was **removed** (it posted to a non-owner channel) — do not re-add it.
- If a release already exists for that tag, delete it first (`gh release delete vX.Y.Z -y && git push --delete origin vX.Y.Z`) before recreating.
