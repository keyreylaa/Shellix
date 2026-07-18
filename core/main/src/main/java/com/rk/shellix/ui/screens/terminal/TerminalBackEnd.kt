package com.rk.shellix.ui.screens.terminal

import android.content.res.Configuration
import android.content.res.Resources
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.rk.libcommons.child
import com.rk.settings.Settings
import com.rk.shellix.ui.diagnostics.AppLog
import com.rk.shellix.ui.activities.terminal.MainActivity
import com.rk.shellix.ui.screens.terminal.virtualkeys.SpecialButton
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class TerminalBackEnd(
    private val terminal: TerminalView,
    private val activity: MainActivity,
    private val coroutineScope: CoroutineScope = activity.lifecycleScope
) : TerminalViewClient, TerminalSessionClient {

    private val terminalViewModel by lazy { ViewModelProvider(activity)[TerminalViewModel::class.java] }

    @Volatile private var redrawScheduled = false
    @Volatile private var lastBgRedrawMs = 0L

    private fun isActiveSession(session: TerminalSession): Boolean {
        val binder = activity.viewModel.sessionBinder ?: return true
        val currentId = binder.getService().currentSession.value.first
        return binder.getSession(currentId) === session
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        // Lifecycle guard: skip redraw work when the activity is not in the foreground.
        if (!activity.isTerminalResumed) return

        if (isActiveSession(changedSession)) {
            // Active tab: coalesce a burst of output lines into one redraw per frame (~16ms).
            if (redrawScheduled) return
            redrawScheduled = true
            terminal.post {
                redrawScheduled = false
                terminal.onScreenUpdated()
            }
        } else {
            // Background tab (5-session case): PRoot keeps running, but throttle redraw to 1/500ms.
            com.rk.shellix.ui.diagnostics.PerfStats.backgroundRenderPaused = true
            val now = SystemClock.uptimeMillis()
            if (now - lastBgRedrawMs < 500L) return
            lastBgRedrawMs = now
            terminal.post { terminal.onScreenUpdated() }
        }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = ClipboardUtils.getText().toString()
        if (clip.trim().isNotEmpty() && terminal.mEmulator != null) {
            terminal.mEmulator.paste(clip)
        }
    }

    override fun onBell(session: TerminalSession) {
        if (!Settings.bell) return
        
        coroutineScope.launch {
            val bellFile = activity.cacheDir.child("bell.oga")
            if (!bellFile.exists()) {
                withContext(Dispatchers.IO) {
                    activity.assets.open("bell.oga").use { input ->
                        FileOutputStream(bellFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            MediaPlayer().apply {
                setOnCompletionListener { it?.release() }
                setDataSource(bellFile.absolutePath)
                prepare()
                start()
            }
        }
    }

    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: "Terminal", message ?: "") }
    override fun logWarn(tag: String?, message: String?) {
        val t = tag ?: "Terminal"
        val m = message ?: ""
        // Benign tracee-exit race spam from proot; demote ONLY this exact pattern to verbose
        // so real warnings stay visible. Do not blanket-filter ptrace.
        if (m.contains("ptrace(PEEKDATA)") && m.contains("No such process")) {
            AppLog.v(t, m)
        } else {
            AppLog.w(t, m)
        }
    }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: "Terminal", message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: "Terminal", message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: "Terminal", message ?: "") }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "Terminal", message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: "Terminal", "Stack trace", e)
    }

    override fun onScale(scale: Float): Float {
        val fontScale = scale.coerceIn(11f, 45f)
        terminal.setTextSize(fontScale.toInt())
        return fontScale
    }

    private val isHardwareKeyboardConnected: Boolean
        get() = Resources.getSystem().configuration.keyboard != Configuration.KEYBOARD_NOKEYS

    override fun onSingleTapUp(e: MotionEvent) {
        if (!(isHardwareKeyboardConnected && Settings.hide_soft_keyboard_if_hwd)) {
            showSoftInput()
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = Settings.input_mode != 1
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (KeyShortcutHandler.handle(keyCode, e, activity)) return true
        
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            val binder = activity.viewModel.sessionBinder ?: return false
            val service = binder.getService()
            val currentId = service.currentSession.value.first
            
            binder.terminateSession(currentId)
            
            if (service.sessionList.isEmpty()) {
                activity.finish()
            } else {
                terminalViewModel.changeSession(activity, binder, service.sessionList.keys.first())
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean =
        terminalViewModel.virtualKeysView?.readSpecialButton(SpecialButton.CTRL, true) == true

    override fun readAltKey(): Boolean =
        terminalViewModel.virtualKeysView?.readSpecialButton(SpecialButton.ALT, true) == true

    override fun readShiftKey(): Boolean =
        terminalViewModel.virtualKeysView?.readSpecialButton(SpecialButton.SHIFT, true) == true

    override fun readFnKey(): Boolean =
        terminalViewModel.virtualKeysView?.readSpecialButton(SpecialButton.FN, true) == true

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
        if (terminal.mEmulator != null) {
            terminal.setTerminalCursorBlinkerState(true, true)
        }
    }

    private fun showSoftInput() {
        terminal.requestFocus()
        KeyboardUtils.showSoftInput(terminal)
    }
}
