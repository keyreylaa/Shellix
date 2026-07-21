package com.rk.shellix.ui.activities.terminal

import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rk.shellix.ui.navHosts.MainActivityNavHost
import com.rk.shellix.ui.routes.MainActivityRoutes
import com.rk.shellix.ui.diagnostics.AppLog
import com.rk.shellix.ui.diagnostics.PerfStats
import com.rk.shellix.ui.screens.terminal.TerminalViewModel
import com.rk.shellix.ui.theme.ShellixTheme

class MainActivity : ComponentActivity() {
    val viewModel: MainViewModel by viewModels()
    private val terminalViewModel: TerminalViewModel by viewModels()
    private var isKeyboardVisible = false
    private var wasKeyboardOpen = false

    @Volatile var isTerminalResumed = true
        private set

    private var lastFrameNs = 0L
    private var frameCount = 0
    private var jankCount = 0
    private var windowStartNs = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameNs != 0L) {
                val deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000.0
                frameCount++
                if (deltaMs > 32.0) jankCount++ // > ~2 frames = visible jank
            }
            lastFrameNs = frameTimeNanos
            if (windowStartNs == 0L) windowStartNs = frameTimeNanos
            if (frameTimeNanos - windowStartNs >= 1_000_000_000L) {
                val pct = if (frameCount > 0) jankCount * 100 / frameCount else 0
                PerfStats.update(jankPercent = pct, frames = frameCount)
                if (pct > 0) AppLog.v("Perf", "jank ${pct}% (${jankCount}/${frameCount} frames >32ms)")
                frameCount = 0; jankCount = 0; windowStartNs = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                // Optional: Handle permission denied
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Realme/ColorOS API 30: AutofillManager.requestHideFillUi blocks main thread
        // on every touch event causing ANR. Terminal emulator doesn't need autofill.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window?.decorView?.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        enableEdgeToEdge()
        requestPermission()
        applyKeepScreenOn()

        if (intent.hasExtra("awake_intent")) {
            moveTaskToBack(true)
        }

        setContent {
            ShellixTheme {
                Surface {
                    val navController = rememberNavController()
                    if (viewModel.isBound) {
                        MainActivityNavHost(
                            navController = navController,
                            mainActivity = this@MainActivity
                        )
                    }

                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val focusManager = LocalFocusManager.current
                    val keyboardController = LocalSoftwareKeyboardController.current

                    LaunchedEffect(backStackEntry?.destination?.route) {
                        if (backStackEntry?.destination?.route != MainActivityRoutes.MainScreen.route) {
                            focusManager.clearFocus(force = true)
                            terminalViewModel.terminalView?.clearFocus()
                            keyboardController?.hide()
                        }
                    }

                    // Proactive crash notice: show once per distinct crash from a previous run.
                    val crashReport = remember { com.rk.shellix.ui.diagnostics.Diagnostics.lastCrashReport }
                    val crashId = remember { com.rk.shellix.ui.diagnostics.Diagnostics.crashId(crashReport) }
                    var showCrashNotice by remember {
                        mutableStateOf(crashId.isNotEmpty() && crashId != com.rk.settings.Settings.last_seen_crash_id)
                    }
                    if (showCrashNotice) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = {
                                com.rk.settings.Settings.last_seen_crash_id = crashId
                                showCrashNotice = false
                            },
                            title = { androidx.compose.material3.Text("Shellix crashed last time") },
                            text = { androidx.compose.material3.Text("A crash was recorded during your previous session. You can view the report for details.") },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    com.rk.settings.Settings.last_seen_crash_id = crashId
                                    showCrashNotice = false
                                    navController.navigate(MainActivityRoutes.Customization.route)
                                }) { androidx.compose.material3.Text("View details") }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    com.rk.settings.Settings.last_seen_crash_id = crashId
                                    showCrashNotice = false
                                }) { androidx.compose.material3.Text("Dismiss") }
                            }
                        )
                    }
                }
            }
        }
        
        setupKeyboardListener()
    }

    override fun onStart() {
        super.onStart()
        viewModel.startAndBindService(this)
    }

    override fun onStop() {
        isTerminalResumed = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        lastFrameNs = 0L
        windowStartNs = 0L
        super.onStop()
        viewModel.unbindService(this)
    }

    override fun onPause() {
        super.onPause()
        wasKeyboardOpen = isKeyboardVisible
    }

    override fun onResume() {
        super.onResume()
        isTerminalResumed = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
        applyKeepScreenOn()
        if (wasKeyboardOpen && !isKeyboardVisible) {
            terminalViewModel.terminalView?.let { terminalView ->
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    /**
     * Apply the user's "Keep screen on" preference at the window level using
     * FLAG_KEEP_SCREEN_ON (no permission, battery-friendly). When off, the flag is
     * cleared; the terminal view's own keepScreenOn (active while a session is focused)
     * still applies as before.
     */
    fun applyKeepScreenOn() {
        if (com.rk.settings.Settings.keep_screen_on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupKeyboardListener() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            isKeyboardVisible = keypadHeight > screenHeight * 0.15
        }
    }
}
