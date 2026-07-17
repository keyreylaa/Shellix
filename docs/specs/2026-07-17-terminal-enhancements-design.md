# Shellix Terminal Enhancements — Design Spec

Date: 2026-07-17
Scope: Three independent terminal enhancements for the Shellix app
(formerly ReTerminal/Termux fork). All run inside the Ubuntu Noble PRoot
environment or on the active terminal session.

---

## A. Packages Tab

### Goal
Let the user browse, search, install, uninstall, and upgrade APT packages
inside the Ubuntu PRoot environment from a dedicated screen, without typing
apt commands manually.

### Architecture
- **Route**: add `data object Packages : MainActivityRoutes("packages")` in
  `core/main/.../ui/routes/MainActivityRoutes.kt`.
- **Navigation**: add a "Packages" item in `TerminalDrawer.kt` that calls
  `navController.navigate(MainActivityRoutes.Packages.route)`.
- **Screen**: `PackagesScreen(mainActivity: MainActivity, navController: NavController)`
  in a new file `ui/screens/packages/PackagesScreen.kt`.
- **Privilege model**: the interactive Ubuntu session runs as the non-root
  `shellix` user (PRoot is launched with `-0`/root, then `init.sh` does
  `exec su - shellix`). The `shellix` user has `NOPASSWD: ALL` sudo. Therefore
  every apt command issued by this screen MUST be prefixed with `sudo`
  (e.g. `sudo apt-get install -y <pkg>`). No password prompt is shown because
  sudo is NOPASSWD.
- **Command bridge**: a new helper `UbuntuCommand` (in `ui/screens/packages/`)
  that:
  1. obtains the active Ubuntu `TerminalSession` from `SessionService`
     (via `SessionBinder`),
  2. generates a unique marker (`MARKER_<random>`) and writes
     `echo MARKER_<random>; <sudo command>; echo MARKER_<random>` to the
     session via `session.write(...)`,
  3. captures output via a `TerminalSessionClient` listener (the termux
     emulator emits output through `onTextChanged`, see `TerminalBackEnd`),
     collecting screen/stream text **between the two marker echoes**,
  4. returns the captured text (between markers) as a `String` once the
     closing marker is observed.
  Mechanism is "write to active TerminalSession + capture between unique
  markers" (NOT ProcessBuilder / direct proot), per the chosen execution
  model. Markers make end-of-output detection reliable and avoid races with
  manual typing or concurrent commands.
- **Concurrency / dpkg lock**: before running apt, the bridge waits briefly /
  retries if `/var/lib/dpkg/lock` (or `apt` lock) is held, and surfaces a clear
  error if the lock cannot be acquired. Only one `UbuntuCommand` runs at a
  time (a simple in-flight guard / queue) to avoid concurrent apt calls.
- **Precondition**: if `Rootfs.isInstalled.value == false`, the screen shows a
  notice "Set up Ubuntu first" instead of package controls.

### Features (v1)
1. **List + search**: list installed packages with a controlled-format query
   `sudo dpkg-query -W -f='${Package}\t${Version}\t${Status}\t${Description}\n'`
   and parse only lines whose `${Status}` contains `install ok installed`.
   Using `dpkg-query -W -f=...` avoids the fragile column layout of `dpkg -l`.
   A search field filters by package name. Cap displayed rows (e.g. windowed
   list / lazy column) since a system can have hundreds of packages.
2. **Detail + uninstall**: tapping a package opens a bottom sheet with name,
   version, description, installed size (from `dpkg-query -W -f='${Installed-Size}'`),
   and an Uninstall button.
   - **Safety preview**: before uninstalling, first run
     `sudo apt-get remove -y --simulate <pkg>` and show the user the list of
     packages that will be removed (and any auto-removed dependencies). The
     user confirms, then the real `sudo apt-get remove -y <pkg>` runs.
3. **Install**: a search field (apt mode) + Install button.
   - **Safety preview**: first run
     `sudo apt-get install -y --simulate <pkg>` and show what will be installed
     / upgraded. User confirms, then run the real `sudo apt-get install -y <pkg>`.
4. **Update/upgrade**: a top-bar button that runs
   `sudo apt-get update && sudo apt-get upgrade -y`.

### Feedback
- While any apt command runs (10–60s), show a spinner + a short toast
  ("Installing…", "Updating…", etc.).
- After completion, refresh the installed-package list automatically.
- Errors from apt are surfaced via a toast / inline message.

### Error handling
- No active Ubuntu session → toast "Open a Ubuntu terminal session first".
- apt command non-zero exit → show last lines of captured output as error.
- Network unavailable during install/update → toast "Network error".

---

## B. Clear Terminal (separate, standalone)

### Goal
Give the user a one-tap way to clear the terminal screen.

### Design
- Add a "Clear" item in `TerminalDrawer.kt` (sits alongside "Packages").
- Action clears the active terminal session: call
  `session.emulator.clearScreen()` or write Ctrl+L (`\f`) to the session.
- Instant, no APT, no network.

### Error handling
- No active session → toast "No terminal session open".

---

## C. Voice Input (separate, standalone)

### Goal
Let the user speak and have the recognized text inserted into the terminal
input field, editable before sending.

### Design
- Add a microphone `IconButton` in `TerminalTopBar.kt` (sits alongside the
  existing Add button).
- Tapping it launches Android's built-in `SpeechRecognizer`
  (`android.speech.SpeechRecognizer` + `RecognizerIntent`), which requires NO
  new dependency.
- Recognized text is placed into the terminal **input field** (editable),
  NOT sent immediately. The user can edit it, then press Enter to send to the
  active session.
- New helper `VoiceInput.kt` (in `ui/screens/terminal/` or `libcommons/`)
  wraps `SpeechRecognizer` lifecycle and returns the final recognized string
  via callback.
- **Locale**: set `RecognizerIntent.EXTRA_LANGUAGE` to the user's preferred
  locale (from `Locale.getDefault()` or app setting) rather than relying on
  the system default, for better accuracy for non-English users.
- **Timeout**: enforce an explicit timeout (e.g. 8–10s). If `SpeechRecognizer`
  emits no callback (some devices hang), cancel recognition and treat as
  no-op / toast "Voice input timed out".

### Permissions
- Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to
  `app/src/main/AndroidManifest.xml`.
- Request `RECORD_AUDIO` at runtime via
  `ActivityCompat.requestPermissions(...)` before starting recognition
  (handle the denied case with a toast).

### Error handling
- SpeechRecognizer unavailable (no Google app / offline) → toast
  "Voice input unavailable".
- Permission denied → toast "Microphone permission required".
- No speech recognized → no-op (do not insert empty text).

---

## Cross-cutting
- All three are reached from the existing drawer (Packages, Clear) or top bar
  (Voice); no new tab row is introduced (consistent with existing nav pattern).
- No changes to the rebrand, Ubuntu migration, or build pipeline.
- Keep Compose UI lightweight (user wants a snappy app).

## Out of scope (YAGNI)
- Package categories / sources management UI.
- Voice command parsing (voice only fills the input field).
- Persistent voice history.

---

## Open questions resolved (from design review)
- **Q: Does apt need sudo inside PRoot?**
  The interactive session is the non-root `shellix` user (PRoot `-0` then
  `su - shellix`); `shellix` has `NOPASSWD: ALL` sudo. So all apt commands
  from this screen are prefixed with `sudo`. No password prompt. Only the
  one-time `setup-user.sh` bootstrap runs as root inside PRoot.
- **Q: Is there a captured-output size limit?**
  No hard limit, but the list UI is windowed/lazy (hundreds of packages) and
  only text *between the two unique markers* is retained, so memory stays
  bounded per command. Long apt logs are surfaced via "last N lines" on error.
