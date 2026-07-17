package com.rk.shellix.ui.screens.packages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rk.libcommons.toast
import com.rk.shellix.service.SessionService
import com.rk.shellix.ui.activities.terminal.MainActivity
import com.rk.shellix.ui.activities.terminal.MainViewModel
import com.rk.shellix.ui.screens.terminal.Rootfs
import kotlinx.coroutines.launch

private data class Pkg(
    val name: String,
    val version: String,
    val desc: String,
    val section: String = ""
)

private enum class SearchMode { INSTALLED, APT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagesScreen(mainActivity: MainActivity, navController: NavController) {
    val scope = rememberCoroutineScope()
    val mainViewModel: MainViewModel = viewModel(mainActivity)
    val sessionBinder = remember { mainViewModel.sessionBinder }
    var query by remember { mutableStateOf("") }
    var packages by remember { mutableStateOf<List<Pkg>>(emptyList()) }
    var aptResults by remember { mutableStateOf<List<Pkg>>(emptyList()) }
    var searchMode by remember { mutableStateOf(SearchMode.INSTALLED) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Pkg?>(null) }
    var confirmText by remember { mutableStateOf<String?>(null) }
    var confirmInstall by remember { mutableStateOf(false) }
    var sourcesText by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (Rootfs.isInstalled.value.not()) return
        scope.launch {
            busy = true
            val out = UbuntuCommand.run(
                sessionBinder,
                "dpkg-query -W -f='\${Package}\\t\${Version}\\t\${Status}\\t\${Section}\\t\${Description}\\n'"
            )
            busy = false
            out.onSuccess { text ->
                packages = text.lines().mapNotNull { line ->
                    val p = line.split("\t")
                    if (p.size >= 4 && p[2].contains("install ok installed"))
                        Pkg(p[0], p[1], if (p.size >= 5) p[4] else "", if (p.size >= 4) p[3] else "")
                    else null
                }
            }.onFailure { toast(it.message ?: "list failed") }
        }
    }

    fun searchApt() {
        val name = query.trim()
        if (name.isEmpty()) {
            aptResults = emptyList()
            return
        }
        scope.launch {
            busy = true
            val out = UbuntuCommand.run(sessionBinder, "apt-cache search $name")
            busy = false
            out.onSuccess { text ->
                aptResults = text.lines().mapNotNull { line ->
                    val idx = line.indexOf(" - ")
                    if (idx <= 0) null
                    else Pkg(line.substring(0, idx).trim(), "", line.substring(idx + 3).trim())
                }
            }.onFailure { toast(it.message ?: "apt search failed") }
        }
    }

    fun showSources() {
        scope.launch {
            busy = true
            val out = UbuntuCommand.run(
                sessionBinder,
                "cat /etc/apt/sources.list; cat /etc/apt/sources.list.d/*.list 2>/dev/null"
            )
            busy = false
            out.onSuccess { sourcesText = it.ifBlank { "(empty)" } }
                .onFailure { toast(it.message ?: "sources read failed") }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (Rootfs.isInstalled.value.not()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Set up Ubuntu first")
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query, onValueChange = {
                query = it
                if (searchMode == SearchMode.APT) searchApt()
            },
            label = { Text("Search packages") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val name = query.trim()
                if (name.isEmpty()) return@Button
                scope.launch {
                    busy = true
                    val sim = UbuntuCommand.run(sessionBinder, "apt-get install -y --simulate $name")
                    busy = false
                    sim.onSuccess { confirmText = it }.onFailure { toast(it.message ?: "sim failed") }
                    confirmInstall = true
                }
            }, modifier = Modifier.weight(1f)) { Text("Install") }
            Button(onClick = {
                scope.launch {
                    busy = true
                    UbuntuCommand.run(sessionBinder, "apt-get update && apt-get upgrade -y")
                        .onFailure { toast(it.message ?: "update failed") }
                    busy = false
                    refresh()
                }
            }, modifier = Modifier.weight(1f)) { Text("Update") }
            Button(onClick = { showSources() }) { Text("Sources") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = searchMode == SearchMode.INSTALLED,
                onClick = { searchMode = SearchMode.INSTALLED },
                label = { Text("Installed") }
            )
            FilterChip(
                selected = searchMode == SearchMode.APT,
                onClick = {
                    searchMode = SearchMode.APT
                    searchApt()
                },
                label = { Text("Search apt") }
            )
        }
        Spacer(Modifier.height(8.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val filtered = if (searchMode == SearchMode.APT) {
            aptResults
        } else {
            packages
        }.let { list ->
            val q = query.trim()
            if (q.isEmpty()) list else list.filter { it.name.contains(q, ignoreCase = true) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered) { pkg ->
                Card(
                    Modifier.fillMaxWidth().clickable { selected = pkg },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pkg.name, style = MaterialTheme.typography.titleSmall)
                            if (pkg.version.isNotEmpty())
                                Text("v${pkg.version}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (pkg.section.isNotEmpty())
                            Text(
                                pkg.section,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        if (pkg.desc.isNotEmpty())
                            Text(
                                pkg.desc,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    }
                }
            }
        }
    }

    // Detail bottom sheet — like a video playlist item
    selected?.let { pkg ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pkg.name, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (pkg.section.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(pkg.section, style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text("Description", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    pkg.desc.ifEmpty { "No description available" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (pkg.version.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text("Version: ", style = MaterialTheme.typography.labelMedium)
                        Text(pkg.version, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (searchMode == SearchMode.APT) {
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    val sim = UbuntuCommand.run(
                                        sessionBinder,
                                        "apt-get install -y --simulate ${pkg.name}"
                                    )
                                    busy = false
                                    sim.onSuccess { confirmText = it }
                                        .onFailure { toast(it.message ?: "sim failed") }
                                    confirmInstall = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Install")
                        }
                    }
                    if (searchMode == SearchMode.INSTALLED) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    val sim = UbuntuCommand.run(
                                        sessionBinder,
                                        "apt-get remove -y --simulate ${pkg.name}"
                                    )
                                    busy = false
                                    sim.onSuccess { confirmText = it; confirmInstall = false }
                                        .onFailure { toast(it.message ?: "sim failed") }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Uninstall")
                        }
                    }
                }
            }
        }
    }

    confirmText?.let { txt ->
        AlertDialog(
            onDismissRequest = { confirmText = null },
            title = { Text(if (confirmInstall) "Install these?" else "Remove these?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(txt.take(2000))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cmd = if (confirmInstall)
                        "apt-get install -y ${query.trim()}"
                    else
                        "apt-get remove -y ${selected?.name ?: ""}"
                    scope.launch {
                        busy = true
                        UbuntuCommand.run(sessionBinder, cmd)
                            .onFailure { toast(it.message ?: "failed") }
                        busy = false
                        confirmText = null; selected = null; confirmInstall = false
                        refresh()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmText = null; confirmInstall = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    sourcesText?.let { txt ->
        AlertDialog(
            onDismissRequest = { sourcesText = null },
            title = { Text("sources.list") },
            text = {
                Text(
                    txt,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { sourcesText = null }) { Text("Close") }
            }
        )
    }
}
