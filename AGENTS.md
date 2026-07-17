# AGENTS.md

Compact guidance for agents working in the **Shellix** repo (Android/Kotlin, a rebrand of the Termux/ReTerminal fork).

## Build & verification (critical)
- **Local Gradle/SDK build IS possible** (proven 2026-07-17): install Android cmdline-tools + `platforms;android-34` + `build-tools;34.0.0` + `platform-tools` into an `ANDROID_HOME`, then run gradle with a **Java 17** toolchain (NOT the gradle-default Java 21 daemon JVM, which crashes with `NoClassDefFoundError: FailureFactory`). Set `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64`, `ANDROID_HOME=...`, and pass `-Dorg.gradle.java.installations.auto-download=false -Dorg.gradle.java.installations.paths=/usr/lib/jvm/java-17-openjdk-arm64`. Do NOT edit `gradle/gradle-daemon-jvm.properties` (keep it at 21 for CI) — override the daemon JVM via env/flags only. Use `./gradlew :core:main:compileDebugKotlin` to catch Kotlin errors locally before pushing. Gradle output is heavily buffered; a full `compileDebugKotlin` takes several minutes, so **GitHub CI (`gh run`) remains the fastest signal** — push and watch `gh run list`.
- **Local verification:** Run `./verify.sh` before every push. It checks file structure, known bug patterns, shell syntax, and Kotlin syntax (files without Android deps). Shows ALL errors at once — fix everything before pushing.
- **CI verification:** `.github/workflows/verify.yml` runs `./gradlew test` + lint on every push. Watch it with `gh run list`.
- **APK build:** `.github/workflows/android.yml` runs only on push to master/main/dev. Runs `./gradlew assembleRelease`, signs APK, uploads as `Shellix-Release` artifact. APK is copied to `app/shellix-<short-sha>.apk`.
- **Telegram step was removed** (it sent to a non-owner channel). Do not re-add it.

## Push workflow
1. Run `./verify.sh` locally — fix ALL errors shown (not one at a time).
2. Git add + commit with descriptive message.
3. Push. Watch CI with `gh run list --limit 3`.
4. If any workflow fails (`gh run view <id> --log-failed`), fix all errors and re-push.
5. Delete failed runs: `gh run delete <id>` (keeps Actions list clean).
6. Repeat until both `verify.yml` and `android.yml` (if applicable) are green.

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
- **UbuntuCommand marker bug (FIXED):** Marker detection must skip the first occurrence (the echoed command line). Uses `m0/m1/m2` pattern: `m0` = echo line, `m1` = first output, `m2` = second output; captures text between `m1` and `m2`.
- **init.sh user switch (FIXED):** Must re-check `/etc/shellix_default_user` AFTER running `setup-user.sh`, not just at the top — otherwise the first session stays root.
- **Settings.wallTransparency default (FIXED):** Default is now `1f` (fully opaque), not `0f` (invisible).
- **No Composable input field for the terminal.** Voice/keyboard input goes to the emulator via `session.emulator.paste(text)` (see `VoiceInput.kt` and Clear action in `TerminalScreen.kt`).

## Conventions
- Commit small, push frequently (CI is the only build check).
- Internal tooling (`.kilo/`, `.agents/`, `docs/superpowers/`, `shellix_rebrand.sh`) is gitignored — never commit it. Specs/plans go in `docs/specs/` and `docs/plans/` (NOT `docs/superpowers/`).
- Keep Compose UI lightweight (user wants a snappy app).
- **Terminal background is a user image already.** `BackgroundImage` (TerminalScreen.kt:175-194) draws `filesDir/background` as a Compose `Image` behind a transparent `TerminalView` (`TerminalViewLayout.kt:42` sets `TRANSPARENT`). `wallTransparency` + `background_blur` settings drive alpha/blur. Image picker is in `Customization.kt:253-312` (copies chosen image to `filesDir/background`). To add a "wallpaper" feel, reuse this path — do not modify the termux `TerminalView` internals.
- **Custom font** loads from `filesDir/font.ttf` (Typeface.createFromFile), picker in `Customization.kt:204-251`. No bundled-font selector.
- **App Theme controls** are in Customization (Light/System/Dark toggle, OLED mode, Monet toggle). Calls `ThemeManager.apply(activity)` on change.

## Release (how to make the APK downloadable)
Builds run only in GitHub Actions (no local gradle). To cut a release:

1. **Push to `master`.** Wait for both `verify.yml` and `android.yml` to be green.
2. **Download the APK artifact** from the green android run:
   ```
   gh run download <RUN_ID> --name Shellix-Release --dir /tmp/apk
   ```
   The file is `shellix-<sha>.apk`. Copy it to `Shellix-vX.Y.Z.apk`.
3. **Create a DRAFT release first** (for testing before public):
   ```
   gh release create vX.Y.Z /tmp/apk/Shellix-vX.Y.Z.apk \
     --title "Shellix vX.Y.Z" \
     --notes "$(cat changelog.md)" \
     --draft
   ```
4. **Test the draft APK** on a device. Only mark as published when stable.
5. **Tag the version** if you didn't use `gh release create` (it auto-creates a tag):
   ```
   git tag -a vX.Y.Z -m "Shellix vX.Y.Z - <short summary>"
   git push origin vX.Y.Z
   ```
6. **Sync the changelog** in two places:
   - The wiki `Changelog.md` at `https://github.com/keyreylaa/Shellix/wiki` (edit via `Shellix.wiki.git` repo).
   - The GitHub Release notes (from the draft).
   Keep both detailed and structured (version, date, Added / Fixed / Build-CI). Mirror the README Roadmap checkboxes.

Notes:
- The old Telegram upload step was **removed** — do not re-add it.
- If a release already exists for that tag, delete it first (`gh release delete vX.Y.Z -y && git push --delete origin vX.Y.Z`) before recreating.
- Always draft first, test, then publish. Never release untested builds.
