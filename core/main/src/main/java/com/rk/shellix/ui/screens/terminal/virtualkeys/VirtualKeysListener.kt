package com.rk.shellix.ui.screens.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.termux.terminal.TerminalSession

/**
 * Handles virtual key clicks. When SHIFT is toggled/held, arrow/home/end keys are
 * sent as shifted sequences (e.g. Shift+Tab = ESC[Z) instead of bare control chars.
 */
class VirtualKeysListener(
    val session: TerminalSession,
    private val keysView: VirtualKeysView? = null,
) : VirtualKeysView.IVirtualKeysView {

    companion object {
        private const val ESC = "\u001B"
        private fun esc(suffix: String) = "$ESC$suffix"
    }

    /** True when the SHIFT special button is currently active. */
    private val shiftActive: Boolean
        get() = keysView?.readSpecialButton(SpecialButton.SHIFT, false) == true

    override fun onVirtualKeyButtonClick(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?,
    ) {
        val key = buttonInfo?.key ?: return

        // Shift+ combinations
        if (shiftActive) {
            when (key) {
                "TAB"   -> { session.write(esc("[Z")); return }
                "HOME"  -> { session.write(esc("[1;2H")); return }
                "END"   -> { session.write(esc("[1;2F")); return }
                "UP"    -> { session.write(esc("[1;2A")); return }
                "DOWN"  -> { session.write(esc("[1;2B")); return }
                "LEFT"  -> { session.write(esc("[1;2D")); return }
                "RIGHT" -> { session.write(esc("[1;2C")); return }
                "PGUP"  -> { session.write(esc("[5;2~")); return }
                "PGDN"  -> { session.write(esc("[6;2~")); return }
            }
        }

        // Regular key
        val writeable: String =
            when (key) {
                "UP"    -> esc("[A")
                "DOWN"  -> esc("[B")
                "LEFT"  -> esc("[D")
                "RIGHT" -> esc("[C")
                "ENTER" -> esc("\r")
                "PGUP"  -> esc("[5~")
                "PGDN"  -> esc("[6~")
                "TAB"   -> "\t"
                "HOME"  -> esc("[H")
                "END"   -> esc("[F")
                "ESC"   -> ESC
                else    -> key
            }

        session.write(writeable)
    }

    override fun performVirtualKeyButtonHapticFeedback(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?,
    ): Boolean = false
}
