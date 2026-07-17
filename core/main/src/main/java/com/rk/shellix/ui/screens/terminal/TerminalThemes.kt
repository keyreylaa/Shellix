package com.rk.shellix.ui.screens.terminal

import android.content.Context
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localDir
import com.rk.shellix.ui.screens.terminal.TerminalColorSchemes.DEFAULT
import java.io.File
import java.io.IOException

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

    /**
     * Decode [file] to an [androidx.compose.ui.graphics.ImageBitmap] using
     * ImageDecoder so every Android-native format (HEIC/WebP/AVIF on supported OS
     * versions) loads. Falls back to BitmapFactory below API 28.
     */
    fun decodeBitmap(context: android.content.Context, file: File): androidx.compose.ui.graphics.ImageBitmap {
        val src = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context, file)) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IOException("Failed to decode ${file.name}")
        }
        return src.asImageBitmap()
    }
}
