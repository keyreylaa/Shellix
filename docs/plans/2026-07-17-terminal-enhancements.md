# Shellix Terminal Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three independent terminal enhancements to Shellix — a Packages tab (APT management inside Ubuntu PRoot), a Clear action, and Voice input — without changing the rebrand, Ubuntu migration, or build pipeline.

**Architecture:** Each feature is a self-contained unit. Packages tab is a new Compose screen reached from the drawer; it runs apt via a `UbuntuCommand` bridge that writes a marked command to the active Ubuntu `TerminalSession` and captures the screen transcript between two unique marker echoes (polling `getTranscriptText()`). Clear is a drawer action that pastes Ctrl+L into the active session. Voice is a top-bar mic button that uses Android's built-in `SpeechRecognizer` and pastes the recognized text into the active terminal emulator (editable via the on-screen keyboard before Enter).

**Tech Stack:** Kotlin + Jetpack Compose, Termux terminal-emulator (`com.github.termux.termux-app:terminal-emulator:v0.118.3`), Android `SpeechRecognizer` / `RecognizerIntent` (no new dependency), AndroidX `ActivityCompat` for runtime permission.

## Global Constraints
- Package root for app code: `com.rk.shellix`. Gradle namespace for `core/main` is `com.rk.shellix`; app `applicationId` is `com.shellix.terminal` (do not change).
- Navigation follows existing pattern: routes in `MainActivityRoutes`, screens wired in `MainActivityNavHost`, drawer items in `TerminalDrawer`, top-bar actions in `TerminalTopBar` called from `TerminalScreen`.
- All apt commands inside the interactive Ubuntu session MUST be prefixed with `sudo` (the `shellix` user has NOPASSWD sudo; see spec privilege model).
- Keep Compose UI lightweight (user wants a snappy app). No new tab row.
- Every command issued via `UbuntuCommand` uses unique marker echoes to delimit output and only one command runs at a time.
- Do not modify rebrand, Ubuntu migration, or CI/build files.
- Build verification is via GitHub Actions (no local gradle available); commit frequently and push so CI validates.

---

## File Structure

- Create `core/main/src/main/java/com/rk/shellix/ui/screens/packages/PackagesScreen.kt` — the Packages tab composable (list, search, detail sheet, install/uninstall/update UI).
- Create `core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt` — bridge that runs a sudo command in the active Ubuntu session and returns captured transcript between markers. KDoc-documented (java-docs skill).
- Create `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/VoiceInput.kt` — wraps `SpeechRecognizer` lifecycle with locale + timeout, returns recognized text via callback. KDoc-documented.
- Modify `core/main/src/main/java/com/rk/shellix/ui/routes/MainActivityRoutes.kt` — add `Packages` route.
- Modify `core/main/src/main/java/com/rk/shellix/ui/navHosts/MainActivityNavHost.kt` — add `composable(Packages)`.
- Modify `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalDrawer.kt` — add "Packages" and "Clear" items.
- Modify `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalTopBar.kt` — add `onMicClick` param + mic IconButton.
- Modify `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalScreen.kt` — pass `onMicClick`, wire Clear + Voice output into the active emulator.
- Modify `app/src/main/AndroidManifest.xml` — add `RECORD_AUDIO` permission.

---

### Task 1: Add Packages route + nav host entry

**Files:**
- Modify: `core/main/src/main/java/com/rk/shellix/ui/routes/MainActivityRoutes.kt`
- Modify: `core/main/src/main/java/com/rk/shellix/ui/navHosts/MainActivityNavHost.kt`

**Interfaces:**
- Produces: `MainActivityRoutes.Packages` (route string `"packages"`) consumed by Tasks 3, 4.

- [ ] **Step 1: Add the Packages route object**

In `MainActivityRoutes.kt`, add after `MainScreen`:
```kotlin
data object Packages : MainActivityRoutes("packages")
```

- [ ] **Step 2: Add the composable in MainActivityNavHost**

In `MainActivityNavHost.kt`, add after the `Customization` composable block:
```kotlin
composable(MainActivityRoutes.Packages.route) {
    UpdateStatusBar(mainActivity.window, true)
    PackagesScreen(mainActivity = mainActivity, navController = navController)
}
```
(Import `PackagesScreen` will resolve once Task 3 creates it; the project will not compile until then — that is expected. CI will catch it, and Task 3 completes it.)

- [ ] **Step 3: Commit**
```bash
git add core/main/src/main/java/com/rk/shellix/ui/routes/MainActivityRoutes.kt core/main/src/main/java/com/rk/shellix/ui/navHosts/MainActivityNavHost.kt
git commit -m "feat: add Packages route and nav host entry"
```

---

### Task 2: UbuntuCommand bridge (run sudo command, capture between markers)

**Files:**
- Create: `core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt`

**Interfaces:**
- Consumes: active session via `SessionService.SessionBinder` (from `MainViewModel.sessionBinder`), read transcript via `terminal.mEmulator.getScreen().getTranscriptText()`.
- Produces: `suspend fun UbuntuCommand.run(sessionBinder: SessionService.SessionBinder?, command: String): Result<String>` returning transcript text captured between markers. `command` should NOT include `sudo` (this function prefixes it).

- [ ] **Step 1: Write UbuntuCommand.kt**

```kotlin
package com.rk.shellix.ui.screens.packages

import com.rk.shellix.service.SessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random
import com.termux.terminal.TerminalSession

/**
 * Runs a command inside the active Ubuntu PRoot session and returns the
 * captured output. The command is wrapped with two unique marker echoes so
 * the emitted transcript between them can be extracted reliably, avoiding
 * races with manual typing or concurrent commands.
 */
object UbuntuCommand {
    private const val TIMEOUT_MS = 60_000L
    private const val POLL_MS = 150L

    /**
     * Executes [command] (without sudo) as root via the NOPASSWD sudo user.
     *
     * @param sessionBinder the active session binder, or null if no session.
     * @param command the shell command to run (sudo is added automatically).
     * @return success with transcript text between markers, or failure.
     */
    suspend fun run(sessionBinder: SessionService.SessionBinder?, command: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val binder = sessionBinder
                    ?: return@withContext Result.failure(IllegalStateException("No session"))
                val id = binder.getService().currentSession.value.first
                val session = binder.getSession(id)
                    ?: return@withContext Result.failure(IllegalStateException("No active session"))
                val emulator = session.emulator
                    ?: return@withContext Result.failure(IllegalStateException("No emulator"))

                val marker = "MARKER_${Random.nextInt(1_000_000, 9_999_999)}"
                val wrapped = "echo $marker; sudo $command; echo $marker\n"
                val before = emulator.getScreen().getTranscriptText()

                session.write(wrapped)

                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < TIMEOUT_MS) {
                    delay(POLL_MS)
                    val now = emulator.getScreen().getTranscriptText()
                    val idx1 = now.indexOf(marker)
                    val idx2 = now.indexOf(marker, idx1 + marker.length)
                    if (idx1 >= 0 && idx2 > idx1) {
                        val between = now.substring(idx1 + marker.length, idx2)
                            .removePrefix("\r\n").removePrefix("\n")
                        return@withContext Result.success(between)
                    }
                }
                Result.failure(IllegalStateException("Command timed out: $command"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles by pushing to CI**

This file is self-contained (uses existing APIs). Commit and let CI confirm:
```bash
git add core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt
git commit -m "feat: add UbuntuCommand bridge (sudo + marker capture)"
git push origin master
```
Expected: CI `compileReleaseKotlin` passes (no PackagesScreen yet, but this file alone compiles). If CI fails only due to missing `PackagesScreen`, that is resolved in Task 3.

---

### Task 3: PackagesScreen UI (list, search, detail, install/uninstall/update)

**Files:**
- Create: `core/main/src/main/java/com/rk/shellix/ui/screens/packages/PackagesScreen.kt`

**Interfaces:**
- Consumes: `UbuntuCommand.run(...)` from Task 2; `mainActivity.viewModel` (MainViewModel) for `sessionBinder`; `Rootfs.isInstalled`.
- Produces: the Packages screen reachable from the drawer (Task 4 wires navigation).

- [ ] **Step 1: Write PackagesScreen.kt**

```kotlin
package com.rk.shellix.ui.screens.packages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.libcommons.toast
import com.rk.shellix.App
import com.rk.shellix.service.SessionService
import com.rk.shellix.ui.activities.terminal.MainActivity
import com.rk.shellix.ui.screens.terminal.Rootfs
import kotlinx.coroutines.launch

private data class Pkg(val name: String, val version: String, val desc: String)

@Composable
fun PackagesScreen(mainActivity: MainActivity, navController: NavController) {
    val scope = rememberCoroutineScope()
    val sessionBinder = remember { App.instance.mainViewModel.sessionBinder }
    var query by remember { mutableStateOf("") }
    var packages by remember { mutableStateOf<List<Pkg>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Pkg?>(null) }
    var confirmText by remember { mutableStateOf<String?>(null) }
    var confirmInstall by remember { mutableStateOf(false) }

    fun refresh() {
        if (Rootfs.isInstalled.value.not()) return
        scope.launch {
            busy = true
            val out = UbuntuCommand.run(
                sessionBinder,
                "dpkg-query -W -f='\${Package}\\t\${Version}\\t\${Status}\\t\${Description}\\n'"
            )
            busy = false
            out.onSuccess { text ->
                packages = text.lines().mapNotNull { line ->
                    val p = line.split("\t")
                    if (p.size >= 4 && p[2].contains("install ok installed"))
                        Pkg(p[0], p[1], p[3]) else null
                }
            }.onFailure { toast(it.message ?: "list failed") }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (Rootfs.isInstalled.value.not()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Set up Ubuntu first")
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search installed / apt") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val name = query.trim()
                if (name.isEmpty()) return@Button
                scope.launch {
                    busy = true
                    val sim = UbuntuCommand.run(sessionBinder, "apt-get install -y --simulate $name")
                    busy = false
                    sim.onSuccess { confirmText = it }.onFailure { toast(it.message ?: "sim failed") }
                    confirmInstall = true
                }
            }, modifier = Modifier.weight(1f)) { Text("Install") }
            Button(onClick = {
                scope.launch {
                    busy = true
                    UbuntuCommand.run(sessionBinder, "apt-get update && apt-get upgrade -y")
                        .onFailure { toast(it.message ?: "update failed") }
                    busy = false
                    refresh()
                }
            }, modifier = Modifier.weight(1f)) { Text("Update") }
        }
        Spacer(Modifier.height(8.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val filtered = packages.filter { it.name.contains(query.trim(), ignoreCase = true) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered) { pkg ->
                Card(Modifier.fillMaxWidth().clickable { selected = pkg }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(pkg.name, style = MaterialTheme.typography.titleSmall)
                        Text("v${pkg.version}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    selected?.let { pkg ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text(pkg.name, style = MaterialTheme.typography.titleMedium)
                Text("Version: ${pkg.version}")
                Text(pkg.desc)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    scope.launch {
                        busy = true
                        val sim = UbuntuCommand.run(sessionBinder, "apt-get remove -y --simulate ${pkg.name}")
                        busy = false
                        sim.onSuccess { confirmText = it; confirmInstall = false }
                            .onFailure { toast(it.message ?: "sim failed") }
                    }
                }) { Text("Uninstall") }
            }
        }
    }

    confirmText?.let { txt ->
        AlertDialog(
            onDismissRequest = { confirmText = null },
            title = { Text(if (confirmInstall) "Install these?" else "Remove these?") },
            text = { Text(txt.take(2000)) },
            confirmButton = {
                Button(onClick = {
                    val cmd = if (confirmInstall)
                        "apt-get install -y ${query.trim()}"
                    else
                        "apt-get remove -y ${selected?.name ?: ""}"
                    scope.launch {
                        busy = true
                        UbuntuCommand.run(sessionBinder, cmd)
                            .onFailure { toast(it.message ?: "failed") }
                        busy = false
                        confirmText = null; selected = null; confirmInstall = false
                        refresh()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirmText = null; confirmInstall = false }) { Text("Cancel") } }
        )
    }
}
```

- [ ] **Step 2: Fix the `App.instance.mainViewModel` access**

Verify `App` exposes `mainViewModel`. If it does not, obtain the binder via `mainActivity.viewModel<MainViewModel>()` instead. Check `core/main/src/main/java/com/rk/shellix/App.kt` for the view-model holder; use whichever pattern `TerminalScreen` uses (`val mainViewModel = viewModel<MainViewModel>(mainActivity)`). Adjust the `remember { ... }` line accordingly.

- [ ] **Step 3: Commit + push (this completes compilation with Task 1)**
```bash
git add core/main/src/main/java/com/rk/shellix/ui/screens/packages/PackagesScreen.kt
git commit -m "feat: add PackagesScreen (list/search/detail/install/uninstall/update)"
git push origin master
```
Expected: CI compiles (Tasks 1+2+3 together now form a complete, valid graph).

---

### Task 4: Drawer items — Packages + Clear

**Files:**
- Modify: `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalDrawer.kt`
- Modify: `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalScreen.kt` (to expose a Clear callback if needed)

**Interfaces:**
- Consumes: `MainActivityRoutes.Packages` (Task 1), active session for Clear via `sessionBinder.getService().currentSession.value.first` then `sessionBinder.getSession(id).emulator`.
- Produces: navigation to Packages; Clear writes Ctrl+L (`\f`) to the active emulator.

- [ ] **Step 1: Add Packages + Clear items to TerminalDrawer**

In `TerminalDrawer.kt`, inside the `Row` of IconButtons (after the Add button), add:
```kotlin
IconButton(onClick = {
    navController.navigate(MainActivityRoutes.Packages.route)
}) { Icon(Icons.Default.Inventory2, contentDescription = "Packages") }

IconButton(onClick = onClearClick) { Icon(Icons.Default.DeleteSweep, contentDescription = "Clear") }
```
Add `onClearClick: () -> Unit` to the `TerminalDrawer` function signature and pass it from `TerminalScreen`.

- [ ] **Step 2: Wire Clear in TerminalScreen**

In `TerminalScreen.kt`, compute the clear action and pass it:
```kotlin
val onClearClick = {
    val binder = sessionBinder
    val id = binder?.getService()?.currentSession?.value?.first
    val session = id?.let { binder.getSession(it) }
    session?.emulator?.paste("\u000c") // Ctrl+L clears the screen
}
```
Then update the `TerminalDrawer(...)` call to pass `onClearClick = onClearClick`.

- [ ] **Step 3: Commit + push**
```bash
git add core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalDrawer.kt core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalScreen.kt
git commit -m "feat: add Packages + Clear items to drawer"
git push origin master
```

---

### Task 5: Voice input (top-bar mic, SpeechRecognizer, paste to emulator)

**Files:**
- Create: `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/VoiceInput.kt`
- Modify: `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalTopBar.kt`
- Modify: `core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `MainActivity` (for `SpeechRecognizer.createSpeechRecognizer` + permission), `Locale`.
- Produces: `VoiceInput.recognize(activity, onResult: (String) -> Unit, onError: (String) -> Unit)`, with runtime `RECORD_AUDIO` request. Result text is pasted into the active emulator by the caller (TerminalScreen).

- [ ] **Step 1: Add RECORD_AUDIO permission to manifest**

In `app/src/main/AndroidManifest.xml`, add inside `<manifest>` after the other permissions:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- [ ] **Step 2: Write VoiceInput.kt**

```kotlin
package com.rk.shellix.ui.screens.terminal

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import java.util.Locale

/**
 * Wraps Android's built-in [SpeechRecognizer] to convert speech to text.
 * Recognized text is delivered via [onResult]; the caller decides what to do
 * with it (e.g. paste into the terminal, editable before Enter).
 */
object VoiceInput {
    private const val TIMEOUT_MS = 10_000L

    /**
     * Starts speech recognition.
     *
     * @param activity the host activity (used for permission + recognizer).
     * @param onResult called with the recognized text (best match).
     * @param onError called with a human-readable error description.
     */
    fun recognize(
        activity: Activity,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onError("Voice input unavailable")
            return
        }
        if (ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001
            )
            onError("Microphone permission required")
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        val timeout = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            recognizer.stopListening()
            onError("Voice input timed out")
        }
        timeout.postDelayed(timeoutRunnable, TIMEOUT_MS)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                timeout.removeCallbacks(timeoutRunnable)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (text.isNullOrBlank()) onError("No speech recognized") else onResult(text)
                recognizer.destroy()
            }
            override fun onError(error: Int) {
                timeout.removeCallbacks(timeoutRunnable)
                onError("Voice error: $error")
                recognizer.destroy()
            }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }
}
```

- [ ] **Step 3: Add mic button to TerminalTopBar**

In `TerminalTopBar.kt`, add param `onMicClick: () -> Unit` to the signature and add inside `actions = { ... }`:
```kotlin
IconButton(onClick = onMicClick) {
    Icon(Icons.Default.Mic, contentDescription = "Voice", tint = color)
}
```

- [ ] **Step 4: Wire mic in TerminalScreen**

In `TerminalScreen.kt`, where `TerminalTopBar` is called, pass:
```kotlin
onMicClick = {
    VoiceInput.recognize(
        activity = mainActivity,
        onResult = { text ->
            val binder = sessionBinder
            val id = binder?.getService()?.currentSession?.value?.first
            val session = id?.let { binder.getSession(it) }
            session?.emulator?.paste(text)
        },
        onError = { com.rk.libcommons.toast(it) }
    )
}
```
Also add `import com.rk.shellix.ui.screens.terminal.VoiceInput` if needed.

- [ ] **Step 5: Commit + push (full feature set)**
```bash
git add core/main/src/main/java/com/rk/shellix/ui/screens/terminal/VoiceInput.kt \
        core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalTopBar.kt \
        core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalScreen.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add Voice input (mic button -> paste to terminal)"
git push origin master
```
Expected: CI compiles and the APK builds. (Runtime voice behavior requires a device with Gboard/recognizer; not testable in CI.)

---

## Self-Review Notes
- Spec coverage: A (route, drawer, screen, UbuntuCommand, dpkg-query, simulate preview, sudo, markers, lock note in Global Constraints, spinner/toast) ✓; B (drawer Clear via Ctrl+L) ✓; C (top-bar mic, SpeechRecognizer, RECORD_AUDIO + runtime, paste-to-emulator, locale, timeout) ✓.
- Marker race condition handled by unique `MARKER_<random>` echoes + polling transcript (no multi-listener needed).
- Types consistent: `UbuntuCommand.run(sessionBinder, command)` used uniformly; `VoiceInput.recognize(activity, onResult, onError)` used in Task 5; `MainActivityRoutes.Packages` created Task 1, used Task 3/4.
- dpkg lock: Global Constraints states only one command runs at a time; for v1 this is sufficient (no concurrent apt from UI). A retry-on-lock refinement can follow if users report lock errors.
