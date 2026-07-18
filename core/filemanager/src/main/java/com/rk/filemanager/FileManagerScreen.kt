package com.rk.filemanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import java.io.File

private enum class ClipMode { COPY, CUT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    ubuntuRoot: File,
    phoneRoot: File,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    var realm by remember { mutableStateOf(StorageRealm.UBUNTU) }
    val root = if (realm == StorageRealm.UBUNTU) ubuntuRoot else phoneRoot
    var current by remember(realm) { mutableStateOf(root) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val entries by remember(current, refreshTick, realm) { mutableStateOf(listDir(current)) }
    fun refresh() { refreshTick++ }

    // selection + clipboard
    val selected = remember(current, realm) { mutableStateListOf<File>() }
    var clipboard by remember { mutableStateOf<List<File>>(emptyList()) }
    var clipMode by remember { mutableStateOf(ClipMode.COPY) }
    val selecting = selected.isNotEmpty()

    // dialogs
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var overwriteConfirm by remember { mutableStateOf<List<String>?>(null) }
    var openFile by remember { mutableStateOf<File?>(null) }

    fun clearSelection() = selected.clear()

    fun doPaste() {
        val dest = current
        val srcs = clipboard
        for (s in srcs) {
            val res = if (clipMode == ClipMode.CUT) FileOps.moveInto(s, dest) else FileOps.copyInto(s, dest)
            if (res is FileOps.Result.Error) { toast(res.message); }
        }
        if (clipMode == ClipMode.CUT) clipboard = emptyList()
        refresh()
    }

    val fileToEdit = openFile
    if (fileToEdit != null) {
        CodeEditorScreen(file = fileToEdit, onBack = { openFile = null })
        return
    }

    BackHandler(enabled = true) {
        when {
            selecting -> clearSelection()
            current.absolutePath != root.absolutePath && current.parentFile != null -> current = current.parentFile!!
            else -> onBack()
        }
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    title = { Text("${selected.size} selected") },
                    actions = {
                        IconButton(onClick = {
                            clipboard = selected.toList(); clipMode = ClipMode.COPY; clearSelection(); toast("Copied")
                        }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
                        IconButton(onClick = {
                            clipboard = selected.toList(); clipMode = ClipMode.CUT; clearSelection(); toast("Cut")
                        }) { Icon(Icons.Filled.ContentCut, contentDescription = "Cut") }
                        if (selected.size == 1) {
                            IconButton(onClick = { renameTarget = selected.first() }) {
                                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("Files") },
                    actions = {
                        if (clipboard.isNotEmpty()) {
                            IconButton(onClick = {
                                val cols = FileOps.collisions(clipboard, current)
                                if (cols.isNotEmpty()) overwriteConfirm = cols else doPaste()
                            }) { Icon(Icons.Filled.ContentPaste, contentDescription = "Paste") }
                        }
                        IconButton(onClick = { showNewFolder = true }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                        }
                        IconButton(onClick = { showNewFile = true }) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New file")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                StorageRealm.entries.forEachIndexed { index, r ->
                    SegmentedButton(
                        selected = realm == r,
                        onClick = { if (realm != r) { realm = r } },
                        shape = SegmentedButtonDefaults.itemShape(index, StorageRealm.entries.size)
                    ) { Text(r.label) }
                }
            }

            if (realm == StorageRealm.PHONE) {
                AssistChip(
                    onClick = {},
                    modifier = Modifier.padding(horizontal = 12.dp),
                    leadingIcon = { Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Phone storage (noexec) — files here can't run directly in Ubuntu; move them into Ubuntu Files first") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }

            val crumbs = breadcrumbs(root, current, if (realm == StorageRealm.UBUNTU) "Ubuntu" else "Phone")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                crumbs.forEachIndexed { i, (label, dir) ->
                    if (i > 0) Text(" / ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (i == crumbs.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { current = dir }
                    )
                }
            }
            HorizontalDivider()

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Empty folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.file.absolutePath }) { entry ->
                        FileRow(
                            entry = entry,
                            selected = selected.contains(entry.file),
                            onClick = {
                                if (selecting) {
                                    if (!selected.remove(entry.file)) selected.add(entry.file)
                                } else if (entry.isDirectory) {
                                    current = entry.file
                                } else {
                                    openFile = entry.file
                                }
                            },
                            onLongClick = {
                                if (!selected.remove(entry.file)) selected.add(entry.file)
                            }
                        )
                    }
                }
            }
        }
    }

    // ---- dialogs ----
    renameTarget?.let { target ->
        var name by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val res = FileOps.rename(target, name)
                    if (res is FileOps.Result.Error) toast(res.message) else { clearSelection(); refresh() }
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        val victims = selected.toList()
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${victims.size} item(s)?") },
            text = { Text("This permanently deletes the selected files/folders. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    victims.forEach { val r = FileOps.delete(it); if (r is FileOps.Result.Error) toast(r.message) }
                    clearSelection(); refresh(); showDeleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    overwriteConfirm?.let { cols ->
        AlertDialog(
            onDismissRequest = { overwriteConfirm = null },
            title = { Text("Overwrite?") },
            text = { Text("${cols.size} item(s) already exist here (${cols.take(5).joinToString()}). Paste and overwrite them?") },
            confirmButton = {
                TextButton(onClick = { doPaste(); overwriteConfirm = null }) { Text("Overwrite") }
            },
            dismissButton = { TextButton(onClick = { overwriteConfirm = null }) { Text("Cancel") } }
        )
    }

    if (showNewFolder) {
        NameDialog("New folder", "Folder name", onDismiss = { showNewFolder = false }) { name ->
            val r = FileOps.createFolder(current, name)
            if (r is FileOps.Result.Error) toast(r.message) else refresh()
            showNewFolder = false
        }
    }
    if (showNewFile) {
        NameDialog("New file", "File name", onDismiss = { showNewFile = false }) { name ->
            val r = FileOps.createFile(current, name)
            if (r is FileOps.Result.Error) toast(r.message) else refresh()
            showNewFile = false
        }
    }
}

@Composable
private fun NameDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(label) }) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(entry: FileEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val icon: ImageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                 else ListItemDefaults.colors(),
        leadingContent = {
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected")
            else Icon(icon, contentDescription = null)
        },
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { if (!entry.isDirectory) Text(formatSize(entry.sizeBytes)) }
    )
}
