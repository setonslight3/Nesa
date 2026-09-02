package com.nesa.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nesa.core.ui.format.formatted
import com.nesa.core.ui.theme.NesaSpacing
import java.time.LocalTime

/**
 * A tappable row showing a time, and the dialog behind it.
 *
 * NESA asks for times often — wake, sleep, an activity's start — so this is one
 * component rather than a dialog re-assembled on each screen.
 */
@Composable
fun TimeField(
    label: String,
    value: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NesaSpacing.touchTarget)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label, ${value.hour}:${value.minute}" }
            .padding(vertical = NesaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = value.formatted(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NesaTimePickerDialog(
    initial: LocalTime,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        // Follow the device's clock preference rather than forcing a format.
        is24Hour = android.text.format.DateFormat.is24HourFormat(
            androidx.compose.ui.platform.LocalContext.current
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        },
        text = { TimePicker(state = state) }
    )
}
