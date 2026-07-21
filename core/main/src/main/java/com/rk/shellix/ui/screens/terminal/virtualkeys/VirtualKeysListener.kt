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
                "TAB"   -> { session.write("[Z"); return }
                "HOME"  -> { session.write("[1;2H"); return }
                "END"   -> { session.write("[1;2F"); return }
                "UP"    -> { session.write("[1;2A"); return }
                "DOWN"  -> { session.write("[1;2B"); return }
                "LEFT"  -> { session.write("[1;2D"); return }
                "RIGHT" -> { session.write("[1;2C"); return }
                "PGUP"  -> { session.write("[5;2~"); return }
                "PGDN"  -> { session.write("[6;2~"); return }
            }
        }

        // Regular key
        val writeable: String =
            when (key) {
                "UP"    -> "[A"
                "DOWN"  -> "[B"
                "LEFT"  -> "[D"
                "RIGHT" -> "[C"
                "ENTER" -> ""
                "PGUP"  -> "[5~"
                "PGDN"  -> "[6~"
                "TAB"   -> "	"
                "HOME"  -> "[H"
                "END"   -> "[F"
                "ESC"   -> ""
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
