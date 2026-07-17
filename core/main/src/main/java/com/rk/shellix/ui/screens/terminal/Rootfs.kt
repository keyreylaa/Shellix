package com.rk.shellix.ui.screens.terminal

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.child
import com.rk.libcommons.localDir
import com.rk.libcommons.ubuntuDir
import java.io.File

object Rootfs {
    var isInstalled = mutableStateOf(false)

    fun checkInstallation(context: Context) {
        isInstalled.value = isRootfsInstalled(context)
    }

    fun isRootfsInstalled(context: Context): Boolean {
        val ubuntuDir = context.ubuntuDir()
        val isExtracted = ubuntuDir.exists() && (ubuntuDir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("ubuntu.tar.gz").exists()
        return isExtracted || isArchivePresent
    }
}
