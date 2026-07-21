package com.rk.shellix.ui.screens.terminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.shellix.service.SessionService
import com.rk.shellix.ui.components.ConfirmDialog
import com.rk.shellix.ui.routes.MainActivityRoutes

@Composable
fun TerminalDrawer(
    drawerWidth: androidx.compose.ui.unit.Dp,
    sessionBinder: SessionService.SessionBinder?,
    navController: NavController,
    onAddSession: () -> Unit,
    onClearClick: () -> Unit,
    onSessionSelected: (String) -> Unit
) {
    // Two-step verification gate for destructive actions.
    var confirmClear by remember { mutableStateOf(false) }
    var confirmTerminateId by remember { mutableStateOf<String?>(null) }
    val twoStep = Settings.two_step_verify
    ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(strings.session),
                    style = MaterialTheme.typography.titleLarge
                )

                Row {
                    val keyboardController = LocalSoftwareKeyboardController.current
                    IconButton(onClick = {
                        navController.navigate(MainActivityRoutes.Settings.route)
                        keyboardController?.hide()
                    }) {
                        Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                    }

                    IconButton(onClick = onAddSession) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    }

                    IconButton(onClick = {
                        navController.navigate(MainActivityRoutes.Packages.route)
                    }) {
                        Icon(Icons.Default.List, contentDescription = "Packages")
                    }

                    IconButton(onClick = {
                        navController.navigate(MainActivityRoutes.FileManager.route)
                    }) {
                        Icon(Icons.Outlined.Folder, contentDescription = "File Manager")
                    }

                    IconButton(onClick = {
                        if (twoStep) confirmClear = true else onClearClick()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                }
            }

            val sessions by remember(sessionBinder) {
                derivedStateOf {
                    sessionBinder?.getService()?.sessionList?.entries?.map { it.key to it.value }.orEmpty()
                }
            }
            LazyColumn {
                items(sessions, key = { it.first }) { (sessionId, meta) ->
                    val isSelected = sessionId == sessionBinder.getService().currentSession.value.first
                    SelectableCard(
                        selected = isSelected,
                        onSelect = { onSessionSelected(sessionId) },
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = meta.name, style = MaterialTheme.typography.bodyLarge)
                            if (!isSelected) {
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        if (twoStep) confirmTerminateId = sessionId
                                        else sessionBinder.terminateSession(sessionId)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (confirmClear) {
                ConfirmDialog(
                    title = "Clear terminal?",
                    text = "This erases the visible terminal output for the current session.",
                    onDismiss = { confirmClear = false },
                    onConfirm = {
                        confirmClear = false
                        onClearClick()
                    }
                )
            }

            confirmTerminateId?.let { id ->
                ConfirmDialog(
                    title = "Close session?",
                    text = "Closing this session discards its scrollback and any running process.",
                    onDismiss = { confirmTerminateId = null },
                    onConfirm = {
                        confirmTerminateId = null
                        sessionBinder?.terminateSession(id)
                    }
                )
            }
        }
    }
}

@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 8.dp else 2.dp
        ),
        enabled = enabled,
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}
