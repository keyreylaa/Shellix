package com.rk.shellix.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rk.resources.strings

/**
 * Reusable two-step confirmation dialog. Shows a short warning and requires an
 * explicit Confirm tap before [onConfirm] runs. Mirrors the project's existing
 * AlertDialog confirm/dismiss idiom.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = stringResource(strings.apply),
    dismissLabel: String = stringResource(strings.cancel),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}
