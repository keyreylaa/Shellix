package com.rk.shellix.ui.screens.terminal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.shellix.service.SessionService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionTabBar(
    sessionBinder: SessionService.SessionBinder?,
    onSessionSelected: (String) -> Unit,
    onCreateSession: () -> Unit,
    onCloseSession: (String) -> Unit,
    onRenameSession: (String) -> Unit
) {
    val service = sessionBinder?.getService()
    val sessions = service?.sessionList?.entries?.map { it.key to it.value }?.toList() ?: emptyList()
    val currentSessionId = service?.currentSession?.value?.first
    val canClose = sessions.size > 1

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = onCreateSession,
                label = { Text("New") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            sessions.forEach { (sessionId, meta) ->
                val selected = sessionId == currentSessionId
                InputChip(
                    selected = selected,
                    onClick = { onSessionSelected(sessionId) },
                    modifier = Modifier.combinedClickable(
                        onClick = { onSessionSelected(sessionId) },
                        onLongClick = { onRenameSession(sessionId) }
                    ),
                    label = { Text(meta.name) },
                    trailingIcon = if (canClose) {
                        {
                            IconButton(
                                onClick = { onCloseSession(sessionId) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close session",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
