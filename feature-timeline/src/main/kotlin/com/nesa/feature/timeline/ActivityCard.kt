package com.nesa.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import com.nesa.core.model.ActivityState
import com.nesa.core.scheduling.ActivityEvent
import com.nesa.core.ui.component.NesaCard
import com.nesa.core.ui.component.StateChip
import com.nesa.core.ui.format.color
import com.nesa.core.ui.format.formatTimeRange
import com.nesa.core.ui.format.label
import com.nesa.core.ui.theme.LocalNesaSemanticColors
import com.nesa.core.ui.theme.NesaSpacing

/**
 * One activity on the timeline.
 *
 * The two decisions a person takes most often — done, and do later — are always
 * one tap away. Everything rarer lives behind the overflow menu, and skipping
 * asks for confirmation, because a skip is a decision NESA records rather than
 * something to trigger by accident.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityCard(
    entry: TimelineEntry,
    onEvent: (ActivityEvent) -> Unit,
    onSkipRequested: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planned = entry.planned
    val state = planned.state
    var menuOpen by remember { mutableStateOf(false) }

    NesaCard(modifier = modifier, selected = entry.isNext) {
        Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {

            if (entry.isNext) {
                Text(
                    text = stringResource(
                        if (state == ActivityState.ACTIVE) R.string.timeline_now
                        else R.string.timeline_next_up
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = planned.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (state == ActivityState.COMPLETED) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                    )
                    Text(
                        text = formatTimeRange(planned.block.start, planned.block.end),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(
                                R.string.timeline_more_actions,
                                planned.title
                            )
                        )
                    }
                    OverflowMenu(
                        expanded = menuOpen,
                        entry = entry,
                        onDismiss = { menuOpen = false },
                        onEvent = { event ->
                            menuOpen = false
                            onEvent(event)
                        },
                        onSkipRequested = {
                            menuOpen = false
                            onSkipRequested()
                        },
                        onEdit = {
                            menuOpen = false
                            onEdit()
                        },
                        onDelete = {
                            menuOpen = false
                            onDeleteRequested()
                        }
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.xs)) {
                StateChip(text = state.label(), color = state.color())
                if (planned.activity.isAnchor) {
                    StateChip(
                        text = stringResource(com.nesa.core.ui.R.string.nesa_anchor),
                        color = LocalNesaSemanticColors.current.anchor
                    )
                }
            }

            // Every meaningful automatic change explains itself, in the place
            // the user is already looking.
            entry.explanation?.let { explanation ->
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!state.isResolved) {
                Row(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                    if (ActivityEvent.COMPLETE in entry.availableEvents) {
                        FilledTonalButton(onClick = { onEvent(ActivityEvent.COMPLETE) }) {
                            Text(stringResource(R.string.timeline_action_complete))
                        }
                    }
                    if (ActivityEvent.DEFER in entry.availableEvents) {
                        OutlinedButton(onClick = { onEvent(ActivityEvent.DEFER) }) {
                            Text(stringResource(R.string.timeline_action_later))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    expanded: Boolean,
    entry: TimelineEntry,
    onDismiss: () -> Unit,
    onEvent: (ActivityEvent) -> Unit,
    onSkipRequested: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (ActivityEvent.START in entry.availableEvents) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timeline_action_start)) },
                onClick = { onEvent(ActivityEvent.START) }
            )
        }
        if (ActivityEvent.SKIP in entry.availableEvents) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timeline_action_skip)) },
                onClick = onSkipRequested
            )
        }
        if (ActivityEvent.CANCEL in entry.availableEvents) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timeline_action_cancel)) },
                onClick = { onEvent(ActivityEvent.CANCEL) }
            )
        }
        if (ActivityEvent.REOPEN in entry.availableEvents) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timeline_action_reopen)) },
                onClick = { onEvent(ActivityEvent.REOPEN) }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.timeline_action_edit)) },
            onClick = onEdit
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.timeline_action_delete)) },
            onClick = onDelete
        )
    }
}

/**
 * Skipping asks once, and offers a reason.
 *
 * The confirmation is not friction for its own sake: a skip is the user telling
 * NESA something, and it is stored as history.
 */
@Composable
fun SkipDialog(
    title: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timeline_skip_title, title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.md)) {
                Text(stringResource(R.string.timeline_skip_body))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.timeline_skip_reason)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.timeline_skip_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.timeline_cancel)) }
        }
    )
}

@Composable
fun DeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timeline_delete_title, title)) },
        text = { Text(stringResource(R.string.timeline_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.timeline_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.timeline_cancel)) }
        }
    )
}
