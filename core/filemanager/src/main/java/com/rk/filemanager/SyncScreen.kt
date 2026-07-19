package com.rk.filemanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Sync configuration screen: pick Ubuntu <-> Phone folder pairs, toggle periodic
 * sync, and trigger an immediate sync. Two-way merge itself runs in [SyncWorker].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    ubuntuRoot: File,
    phoneRoot: File,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pairs by remember { mutableStateOf(SyncConfigStore.getPairs(context)) }
    var pickerSide by remember { mutableStateOf<PickerSide?>(null) }
    var pendingUbuntu by remember { mutableStateOf<String?>(null) }

    fun persist(new: List<SyncConfigStore.Pair>) {
        pairs = new
        SyncConfigStore.savePairs(context, new)
        if (new.any { it.enabled }) SyncWorker.schedule(context) else SyncWorker.cancel(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Folder Sync") },
                actions = {
                    IconButton(onClick = { SyncWorker.runNow(context) }) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync now")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Two-way sync (last-write-wins). Polls every 15 min in the background; " +
                        "conflicting files are kept as .conflict-<timestamp> on both sides.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
            Button(
                onClick = { pickerSide = PickerSide.UBUNTU },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) { Text("Add sync pair") }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(pairs) { p ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.ubuntuPath, style = MaterialTheme.typography.bodySmall)
                                Text("↔ ${p.phonePath}", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = p.enabled, onCheckedChange = { checked ->
                                persist(pairs.map { if (it == p) it.copy(enabled = checked) else it })
                            })
                            IconButton(onClick = { persist(pairs - p) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }

    // Simple folder picker: reuse listDir navigation, pick current folder.
    pickerSide?.let { side ->
        var current by remember(side) {
            mutableStateOf(if (side == PickerSide.UBUNTU) ubuntuRoot else phoneRoot)
        }
        AlertDialog(
            onDismissRequest = { pickerSide = null },
            confirmButton = {
                TextButton(onClick = {
                    when (side) {
                        PickerSide.UBUNTU -> pendingUbuntu = current.absolutePath.also { pickerSide = PickerSide.PHONE }
                        PickerSide.PHONE -> {
                            pendingUbuntu?.let { u ->
                                persist(pairs + SyncConfigStore.Pair(u, current.absolutePath, true))
                            }
                            pendingUbuntu = null
                            pickerSide = null
                        }
                    }
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { pickerSide = null }) { Text("Cancel") } },
            title = { Text(if (side == PickerSide.UBUNTU) "Pick Ubuntu folder" else "Pick Phone folder") },
            text = {
                val entries = listDir(current)
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    items(entries, key = { it.file.absolutePath }) { e ->
                        ListItem(
                            modifier = Modifier.clickable {
                                if (e.isDirectory) current = e.file else Unit
                            },
                            headlineContent = { Text(e.name) },
                            leadingContent = { Icon(if (e.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile, null) }
                        )
                    }
                }
            }
        )
    }
}

private enum class PickerSide { UBUNTU, PHONE }
