package com.rk.shellix.ui.screens.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.rk.shellix.service.SessionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalTopBar(
    sessionBinder: SessionService.SessionBinder?,
    onMenuClick: () -> Unit,
    onAddClick: () -> Unit,
    onMicClick: () -> Unit,
    color: Color
) {
    var micActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        title = {
            Column {
                Text(text = "Shellix", color = color)
                sessionBinder?.getService()?.currentSession?.value?.let { (id, mode) ->
                    Text(
                        style = MaterialTheme.typography.bodySmall,
                        text = "$id (${TerminalUtils.getNameOfWorkingMode(mode)})",
                        color = color
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, null, tint = color)
            }
        },
        actions = {
            IconButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, null, tint = color)
            }
            IconButton(
                onClick = {
                    micActive = true
                    scope.launch {
                        try {
                            onMicClick()
                        } finally {
                            delay(2000)
                            micActive = false
                        }
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (micActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice",
                    tint = if (micActive) MaterialTheme.colorScheme.primary else color
                )
            }
        }
    )
}
