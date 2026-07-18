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
import io.github.rosemoe.sora.lang.EmptyLanguage
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
                            // Dark editor matching the app's Soft Dark palette (Tugas 4).
                            setColorScheme(SoftDarkScheme())
                            // Cut per-line overdraw/render cost while scrolling large files (Tugas 6, F3).
                            setHighlightCurrentLine(false)
                            setCursorAnimationEnabled(false)
                            // line numbers are enabled by default in Sora Editor.
                            // Lightweight keyword/comment/string highlighter chosen by file
                            // extension — no native regex engine, so it never touches the
                            // terminal render path or bloats the APK.
                            val spec = LangSpec.forFile(file.name)
                            // Skip highlighting for very large files so the editor still opens
                            // and scrolls smoothly instead of janking on full-file tokenize (Tugas 6, F2).
                            if (file.length() <= 1_000_000) {
                                setEditorLanguage(SimpleHighlightLanguage(spec))
                            } else {
                                setEditorLanguage(EmptyLanguage())
                            }
                            editorRef = this
                        }
                    }
                )
            }
        }
    }
}
