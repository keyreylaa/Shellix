package com.rk.shellix.ui.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.shellix.ui.screens.terminal.TerminalViewModel

@Composable
fun DiagnosticsSection(viewModel: TerminalViewModel) {
    var showLogs by remember { mutableStateOf(false) }
    var showCrash by remember { mutableStateOf(false) }

    PreferenceGroup(heading = "Diagnostics") {
        PreferenceSwitch(
            checked = Diagnostics.notifyOnPluginErrors,
            onCheckedChange = { Diagnostics.notifyOnPluginErrors = it },
            label = "Notify on plugin errors",
            description = "Show a notification when a plugin/extension fails to load"
        )

        PreferenceTemplate(
            modifier = Modifier.clickable { showLogs = true },
            title = { Text("App logs") },
            description = { Text("${Diagnostics.snapshot().size} lines buffered") }
        )

        PreferenceTemplate(
            modifier = Modifier.clickable { showCrash = true },
            title = { Text("Last crash report") },
            description = { Text(Diagnostics.lastCrashReport?.lineSequence()?.firstOrNull() ?: "No crashes recorded") }
        )
    }

    if (showLogs) {
        LogViewerDialog(onDismiss = { showLogs = false })
    }
    if (showCrash) {
        CrashReportDialog(onDismiss = { showCrash = false })
    }
}

@Composable
private fun LogViewerDialog(onDismiss: () -> Unit) {
    val lines = remember { Diagnostics.snapshot() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                Diagnostics.clear()
                onDismiss()
            }) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("App logs") },
        text = {
            if (lines.isEmpty()) {
                Text("No logs captured yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(lines) { line ->
                        Text(line, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
    )
}

@Composable
private fun CrashReportDialog(onDismiss: () -> Unit) {
    val report = Diagnostics.lastCrashReport
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Last crash report") },
        text = {
            if (report == null) {
                Text("No crash report available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    report,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    )
}
