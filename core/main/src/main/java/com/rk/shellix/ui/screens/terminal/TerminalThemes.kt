package com.rk.shellix.ui.screens.terminal

import android.content.Context
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localDir
import com.rk.shellix.ui.screens.terminal.TerminalColorSchemes.DEFAULT

object TerminalThemes {
    /**
     * Write [scheme] to the termux `colors.properties` file and apply it to the
     * global [com.termux.terminal.TerminalColors] scheme. `Default` removes the
     * file so termux falls back to its built-in colors.
     */
    fun applyScheme(context: Context, scheme: ColorScheme) {
        val file = context.localDir().child("colors.properties")
        if (scheme == DEFAULT) {
            if (file.exists() && file.isFile) file.delete()
        } else {
            file.createFileIfNot()
            file.writeText(scheme.toProperties())
        }
    }

    fun applyDracula(context: Context) = applyScheme(context, TerminalColorSchemes.DRACULA)
    fun applyDefault(context: Context) = applyScheme(context, DEFAULT)
}
