package com.rk.shellix.ui.screens.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.rk.shellix.ui.screens.terminal.virtualkeys.VirtualKeysView.IVirtualKeysView
import com.termux.terminal.TerminalSession

class VirtualKeyClient(
    val session: TerminalSession,
    private val keysView: VirtualKeysView? = null,
) : IVirtualKeysView {

    private val shiftActive: Boolean
        get() = keysView?.readSpecialButton(SpecialButton.SHIFT, false) == true

    override fun onVirtualKeyButtonClick(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?,
    ) {
        val key = buttonInfo?.key ?: run { return }
        if (key.isEmpty()) return

        // Shift+ combinations
        if (shiftActive) {
            when (key) {
                "TAB" -> { session.write("[Z"); return }
                "HOME" -> { session.write("[1;2H"); return }
                "END" -> { session.write("[1;2F"); return }
                "UP" -> { session.write("[1;2A"); return }
                "DOWN" -> { session.write("[1;2B"); return }
                "LEFT" -> { session.write("[1;2D"); return }
                "RIGHT" -> { session.write("[1;2C"); return }
                "PGUP" -> { session.write("[5;2~"); return }
                "PGDN" -> { session.write("[6;2~"); return }
            }
        }

        when (key) {
            "ESC" -> session.write("")
            "TAB" -> session.write("	")
            "HOME" -> session.write("[H")
            "UP" -> session.write("[A")
            "DOWN" -> session.write("[B")
            "LEFT" -> session.write("[D")
            "RIGHT" -> session.write("[C")
            "PGUP" -> session.write("[5~")
            "PGDN" -> session.write("[6~")
            "END" -> session.write("[F")
            else -> session.write(key)
        }
    }

    override fun performVirtualKeyButtonHapticFeedback(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?,
    ): Boolean = false
}
