package com.rk.filemanager

import java.io.File

/**
 * Heuristic binary detection: reads up to [sampleSize] bytes and treats the file
 * as binary if it contains a NUL byte or too high a ratio of non-text control
 * bytes. Cheap and good enough to avoid opening binaries as garbage text.
 */
object TextFileDetect {
    const val MAX_EDIT_BYTES = 5 * 1024 * 1024 // 5 MB safety cap for the editor

    fun isProbablyText(file: File, sampleSize: Int = 8000): Boolean {
        if (!file.isFile) return false
        if (file.length() == 0L) return true // empty file is editable
        return runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(sampleSize)
                val n = input.read(buf)
                if (n <= 0) return true
                isProbablyText(buf, n)
            }
        }.getOrDefault(false)
    }

    /** Byte-array overload (testable without touching the filesystem). */
    fun isProbablyText(bytes: ByteArray, length: Int = bytes.size): Boolean {
        if (length <= 0) return true // empty content is editable
        var suspicious = 0
        for (i in 0 until length) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false // NUL => binary
            // control chars except tab(9), LF(10), CR(13), FF(12), ESC(27)
            if (b < 0x20 && b != 9 && b != 10 && b != 13 && b != 12 && b != 27) {
                suspicious++
            }
        }
        return suspicious.toDouble() / length < 0.30
    }
}
