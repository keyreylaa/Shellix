package com.rk.shellix.ui.screens.terminal

import android.content.Context
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localDir

object TerminalThemes {
    private const val DRACULA = "background=#282a36\n" +
        "foreground=#f8f8f2\n" +
        "cursor=#f8f8f2\n" +
        "color0=#21222c\n" +
        "color1=#ff5555\n" +
        "color2=#50fa7b\n" +
        "color3=#f1fa8c\n" +
        "color4=#bd93f9\n" +
        "color5=#ff79c6\n" +
        "color6=#8be9fd\n" +
        "color7=#f8f8f2\n" +
        "color8=#6272a4\n" +
        "color9=#ff6e6e\n" +
        "color10=#69ff94\n" +
        "color11=#ffffa5\n" +
        "color12=#d6acff\n" +
        "color13=#ff92df\n" +
        "color14=#a4ffff\n" +
        "color15=#ffffff\n"

    fun applyDracula(context: Context) {
        val file = context.localDir().child("colors.properties")
        file.createFileIfNot()
        file.writeText(DRACULA)
    }

    fun applyDefault(context: Context) {
        val file = context.localDir().child("colors.properties")
        if (file.exists() && file.isFile) {
            file.delete()
        }
    }
}
