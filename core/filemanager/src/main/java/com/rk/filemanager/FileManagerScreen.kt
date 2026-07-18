package com.rk.filemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.io.File

/**
 * Self-contained File Manager entry point. Kept free of any :core:main dependency so
 * the heavy editor stack stays isolated in this module and there is no circular
 * dependency (:core:main wires this in via navigation).
 *
 * @param ubuntuRoot the PRoot rootfs "home" directory (exec-capable, app-private).
 * @param phoneRoot the shared Android storage root (FUSE, noexec).
 * @param onBack invoked when the user navigates back out of the file manager.
 */
@Composable
fun FileManagerScreen(
    ubuntuRoot: File,
    phoneRoot: File,
    onBack: () -> Unit
) {
    // Tahap 0: placeholder. Browse UI lands in Tahap A.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("File Manager", style = MaterialTheme.typography.titleLarge)
    }
}
