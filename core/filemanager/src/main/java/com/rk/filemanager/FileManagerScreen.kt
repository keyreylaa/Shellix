package com.rk.filemanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    ubuntuRoot: File,
    phoneRoot: File,
    onBack: () -> Unit
) {
    var realm by remember { mutableStateOf(StorageRealm.UBUNTU) }
    val root = if (realm == StorageRealm.UBUNTU) ubuntuRoot else phoneRoot
    var current by remember(realm) { mutableStateOf(root) }
    // Recompute listing when the directory or realm changes.
    var refreshTick by remember { mutableIntStateOf(0) }
    val entries by remember(current, refreshTick) { mutableStateOf(listDir(current)) }

    // System back: go up one level until at the realm root, then leave the screen.
    BackHandler(enabled = true) {
        if (current.absolutePath != root.absolutePath && current.parentFile != null) {
            current = current.parentFile!!
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Files") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Realm switcher
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                StorageRealm.entries.forEachIndexed { index, r ->
                    SegmentedButton(
                        selected = realm == r,
                        onClick = { realm = r },
                        shape = SegmentedButtonDefaults.itemShape(index, StorageRealm.entries.size)
                    ) { Text(r.label) }
                }
            }

            // noexec warning for the FUSE phone-storage realm
            if (realm == StorageRealm.PHONE) {
                AssistChip(
                    onClick = {},
                    modifier = Modifier.padding(horizontal = 12.dp),
                    leadingIcon = {
                        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    label = { Text("Phone storage (noexec) — files here can't run directly in Ubuntu; move them into Ubuntu Files first") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }

            // Breadcrumbs
            val crumbs = breadcrumbs(root, current, if (realm == StorageRealm.UBUNTU) "Ubuntu" else "Phone")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                crumbs.forEachIndexed { i, (label, dir) ->
                    if (i > 0) Text(" / ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (i == crumbs.lastIndex) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { current = dir }
                    )
                }
            }
            HorizontalDivider()

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Empty folder",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.file.absolutePath }) { entry ->
                        FileRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) {
                                    current = entry.file
                                }
                                // file tap opens editor in Tahap C
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    val icon: ImageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile
    ListItem(
        modifier = Modifier.clickable { onClick() },
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (!entry.isDirectory) Text(formatSize(entry.sizeBytes))
        }
    )
}
