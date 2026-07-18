package com.rk.shellix.ui.screens.terminal

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.material.R
import com.rk.libcommons.child
import com.rk.libcommons.localDir
import com.rk.settings.Settings
import com.rk.shellix.service.SessionService
import com.rk.shellix.ui.activities.terminal.MainActivity
import com.rk.shellix.ui.screens.terminal.virtualkeys.VirtualKeysListener
import com.rk.shellix.ui.screens.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalColors
import com.termux.view.TerminalView
import java.io.File
import java.lang.ref.WeakReference
import java.util.Properties

class TerminalViewModel : ViewModel() {

    companion object {
        // Set by MainViewModel when the SessionService binds, so settings screens
        // can live-apply changes (e.g. color schemes) to every running session.
        var currentBinder: SessionService.SessionBinder? = null
    }
    private var terminalViewRef = WeakReference<TerminalView>(null)
    private var virtualKeysViewRef = WeakReference<VirtualKeysView>(null)

    val terminalView: TerminalView? get() = terminalViewRef.get()
    val virtualKeysView: VirtualKeysView? get() = virtualKeysViewRef.get()

    fun setTerminalView(view: TerminalView?) { terminalViewRef = WeakReference(view) }
    fun setVirtualKeysView(view: VirtualKeysView?) { virtualKeysViewRef = WeakReference(view) }

    var bitmapFile by mutableStateOf<File?>(null)
    var wallAlpha by mutableFloatStateOf(Settings.wallTransparency)
    var backgroundBlur by mutableFloatStateOf(Settings.background_blur)

    var showToolbar by mutableStateOf(Settings.toolbar)
    var showVirtualKeys by mutableStateOf(Settings.virtualKeys)
    var showHorizontalToolbar by mutableStateOf(Settings.toolbar)

    // Real mic listening state for the voice-input toggle button.
    var voiceListening by mutableStateOf(false)

    fun setFont(typeface: Typeface) {
        TerminalUtils.typeface = typeface
        terminalView?.apply {
            setTypeface(typeface)
            onScreenUpdated()
        }
    }

    fun changeSession(context: Context, sessionBinder: SessionService.SessionBinder, sessionId: String) {
        val terminal = terminalView ?: return
        val activity = context as? MainActivity ?: return
        val client = TerminalBackEnd(terminal, activity)
        
        val session = sessionBinder.getSession(sessionId)
            ?: sessionBinder.createSession(sessionId, client, Settings.working_Mode)
            
        session.updateTerminalSessionClient(client)
        terminal.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        terminal.attachSession(session)
        terminal.setTerminalViewClient(client)
        // Force one immediate full redraw so a re-opened (previously throttled)
        // background tab is never left blank on activation.
        terminal.onScreenUpdated()
        
        terminal.post {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
            terminal.keepScreenOn = true
            terminal.requestFocus()
            terminal.isFocusableInTouchMode = true

            terminal.mEmulator?.mColors?.mCurrentColors?.apply {
                set(256, typedValue.data)
                set(257, TerminalUtils.getBackgroundColor())
                set(258, typedValue.data)
            }
        }
        
        virtualKeysView?.apply {
            virtualKeysViewClient = terminal.mTermSession?.let { VirtualKeysListener(it) }
        }
        
        sessionBinder.getService().activeSessionId = sessionId
        sessionBinder.getService().currentSession.value = Pair(sessionId, sessionBinder.getService().sessionList[sessionId]!!.mode)
    }

    /**
     * Apply a terminal color [scheme] and push it to every live session without
     * restarting any of them. Writes the termux `colors.properties` file, updates
     * the global [TerminalColors] scheme, then re-tints each session's emulator
     * and redraws the attached view.
     */
    fun applyColorSchemeGlobally(context: Context, scheme: ColorScheme) {
        TerminalThemes.applyScheme(context, scheme)

        val props = Properties()
        val file = context.localDir().child("colors.properties")
        if (file.exists() && file.isFile) {
            java.io.FileInputStream(file).use { props.load(it) }
        }
        TerminalColors.COLOR_SCHEME.updateWith(props)

        // Re-tint EVERY live session's own emulator color copy. Each emulator caches its
        // own TerminalColors at attach time, so updating only the global scheme leaves
        // running sessions on the old colors until the app is restarted. Pushing the new
        // props into each emulator's mColors makes the change apply instantly.
        currentBinder?.allSessions()?.forEach { session ->
            session.emulator?.mColors?.updateWith(props)
        }
        // Redraw the visible view so the change is seen immediately.
        terminalView?.onScreenUpdated()
        terminalView?.invalidate()
    }
}
