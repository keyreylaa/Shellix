package com.rk.shellix.ui.screens.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.rk.shellix.ui.screens.terminal.virtualkeys.VirtualKeysView.IVirtualKeysView
import com.termux.terminal.TerminalSession

class VirtualKeyClient(
    val session: TerminalSession,
    private val keysView: VirtualKeysView? = null,
) : IVirtualKeysView {

    companion object {
        private const val ESC = "\u001B"
        private fun esc(suffix: String) = "$ESC$suffix"
    }

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
                "TAB" -> { session.write(esc("[Z")); return }
                "HOME" -> { session.write(esc("[1;2H")); return }
                "END" -> { session.write(esc("[1;2F")); return }
                "UP" -> { session.write(esc("[1;2A")); return }
                "DOWN" -> { session.write(esc("[1;2B")); return }
                "LEFT" -> { session.write(esc("[1;2D")); return }
                "RIGHT" -> { session.write(esc("[1;2C")); return }
                "PGUP" -> { session.write(esc("[5;2~")); return }
                "PGDN" -> { session.write(esc("[6;2~")); return }
            }
        }

        when (key) {
            "ESC" -> session.write(ESC)
            "TAB" -> session.write("\t")
            "HOME" -> session.write(esc("[H"))
            "UP" -> session.write(esc("[A"))
            "DOWN" -> session.write(esc("[B"))
            "LEFT" -> session.write(esc("[D"))
            "RIGHT" -> session.write(esc("[C"))
            "PGUP" -> session.write(esc("[5~"))
            "PGDN" -> session.write(esc("[6~"))
            "END" -> session.write(esc("[F"))
            else -> session.write(key)
        }
    }

    override fun performVirtualKeyButtonHapticFeedback(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?,
    ): Boolean = false
}
