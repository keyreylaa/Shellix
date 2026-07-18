package com.rk.filemanager

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

/**
 * Built-in text/code editor backed by Sora Editor (line numbers on by default).
 * Refuses binary files. Syntax highlighting is layered on in Tahap D.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val isText = remember(file) {
        file.length() <= TextFileDetect.MAX_EDIT_BYTES && TextFileDetect.isProbablyText(file)
    }

    // Hold the editor instance so Save can read its content.
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(file.name, maxLines = 1) },
                actions = {
                    if (isText) {
                        IconButton(onClick = {
                            val ed = editorRef
                            if (ed != null) {
                                val ok = runCatching { file.writeText(ed.text.toString()) }.isSuccess
                                toast(if (ok) "Saved" else "Save failed")
                            }
                        }) { Icon(Icons.Filled.Save, contentDescription = "Save") }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isText) {
                Text(
                    "This file is not text (binary) and cannot be opened in the editor.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        CodeEditor(ctx).apply {
                            setText(runCatching { file.readText() }.getOrDefault(""))
                            // line numbers are enabled by default in Sora Editor
                            editorRef = this
                        }
                    }
                )
            }
        }
    }
}
