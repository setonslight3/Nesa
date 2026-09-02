package com.nesa.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.ui.component.EmptyState
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.format.label
import com.nesa.core.ui.theme.NesaSpacing
import com.nesa.core.scheduling.ActivityEvent
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * NESA's main surface.
 *
 * The design goal is that a user can tell what to do next within seconds: one
 * highlighted card at the top, the rest of the day beneath it grouped by phase,
 * and anything NESA could not place kept visible instead of quietly dropped.
 */
@Composable
fun TimelineScreen(
    onAddActivity: () -> Unit,
    onEditActivity: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var skipTarget by remember { mutableStateOf<TimelineEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<TimelineEntry?>(null) }

    NesaScaffold(
        title = stringResource(R.string.timeline_title),
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.timeline_settings)
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddActivity,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.timeline_add)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = NesaSpacing.screen,
                end = NesaSpacing.screen,
                bottom = NesaSpacing.xxl * 2
            ),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
        ) {
            item {
                TimelineHeader(
                    state = state,
                    onPreviousDay = viewModel::onPreviousDay,
                    onNextDay = viewModel::onNextDay,
                    onToday = viewModel::onToday
                )
            }

            if (state.isEmpty && !state.loading) {
                item {
                    EmptyState(
                        title = stringResource(R.string.timeline_empty_title),
                        message = stringResource(R.string.timeline_empty_body),
                        action = {
                            TextButton(onClick = onAddActivity) {
                                Text(stringResource(R.string.timeline_empty_action))
                            }
                        }
                    )
                }
            }

            state.sections.forEach { section ->
                item(key = "section-${section.cycle.name}") {
                    SectionHeader(title = section.cycle.label())
                }
                items(section.entries, key = { it.id }) { entry ->
                    ActivityCard(
                        entry = entry,
                        onEvent = { event -> viewModel.onEvent(entry.id, event) },
                        onSkipRequested = { skipTarget = entry },
                        onEdit = { onEditActivity(entry.planned.activity.id) },
                        onDeleteRequested = { deleteTarget = entry }
                    )
                }
            }

            if (state.needingAttention.isNotEmpty()) {
                item(key = "needs-slot-header") {
                    SectionHeader(title = stringResource(R.string.timeline_needs_slot_title))
                }
                item(key = "needs-slot-notice") {
                    NoticeCard(text = stringResource(R.string.timeline_needs_slot_body))
                }
                items(state.needingAttention, key = { it.id }) { entry ->
                    ActivityCard(
                        entry = entry,
                        onEvent = { event -> viewModel.onEvent(entry.id, event) },
                        onSkipRequested = { skipTarget = entry },
                        onEdit = { onEditActivity(entry.planned.activity.id) },
                        onDeleteRequested = { deleteTarget = entry }
                    )
                }
            }
        }
    }

    skipTarget?.let { entry ->
        SkipDialog(
            title = entry.planned.title,
            onConfirm = { reason ->
                viewModel.onEvent(entry.id, ActivityEvent.SKIP, reason)
                skipTarget = null
            },
            onDismiss = { skipTarget = null }
        )
    }

    deleteTarget?.let { entry ->
        DeleteDialog(
            title = entry.planned.title,
            onConfirm = {
                viewModel.onDelete(entry.planned.activity.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun TimelineHeader(
    state: TimelineUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = NesaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(NesaSpacing.xs)
    ) {
        if (state.displayName != null && state.isToday) {
            Text(
                text = stringResource(R.string.timeline_greeting, state.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousDay) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.timeline_previous_day)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.date.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.totalCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.timeline_progress,
                            state.completedCount,
                            state.totalCount
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onNextDay) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.timeline_next_day)
                )
            }
        }

        if (!state.isToday) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.timeline_today))
            }
        }
    }
}
