package app.liftlog.ui.programs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal const val MAX_NAME_LENGTH = 40

/** Asks for a short name; used to create and rename programs and days. */
@Composable
internal fun NameDialog(
    title: String,
    initial: String = "",
    placeholder: String = "",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MAX_NAME_LENGTH) name = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ConfirmDeleteDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Up and down arrows for reordering a list item. */
@Composable
internal fun MoveButtons(canMoveUp: Boolean, canMoveDown: Boolean, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    Row {
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
    }
}

/** A labelled number with minus and plus buttons, kept within [range]. */
@Composable
internal fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange(value - step) }, enabled = value > range.first) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "${format(value)}\n$label",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.widthIn(min = 52.dp),
        )
        FilledTonalIconButton(onClick = { onChange(value + step) }, enabled = value < range.last) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}
