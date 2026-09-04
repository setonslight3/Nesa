package com.nesa.feature.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.Flexibility
import com.nesa.core.model.Priority
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NesaTimePickerDialog
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.component.SwitchRow
import com.nesa.core.ui.component.TimeField
import com.nesa.core.ui.format.label
import com.nesa.core.ui.theme.NesaSpacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Editing a life schedule: a name, a switch, and a list of recurring entries.
 *
 * Each entry gets days, a start, a length, and the two fields that decide how
 * the scheduler treats it. Exposing priority and flexibility here rather than
 * hiding them behind the kind is deliberate: a user whose training is genuinely
 * immovable should be able to say so, and the kind only ever supplies defaults.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingEntryTime by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    NesaScaffold(
        title = stringResource(R.string.schedule_title),
        modifier = modifier,
        onBack = onDone
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = NesaSpacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text(stringResource(R.string.schedule_name)) },
                isError = state.nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.nameError) NoticeCard(text = stringResource(R.string.schedule_name_error))

            SwitchRow(
                title = stringResource(R.string.schedule_enabled),
                supportingText = stringResource(R.string.schedule_enabled_support),
                checked = state.enabled,
                onCheckedChange = viewModel::onEnabledChanged
            )

            SectionHeader(title = stringResource(R.string.schedule_entries))
            if (state.entries.isEmpty()) {
                NoticeCard(text = stringResource(R.string.schedule_no_entries))
            }

            state.entries.forEachIndexed { index, entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(NesaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
                    ) {
                        OutlinedTextField(
                            value = entry.title.trim(),
                            onValueChange = { viewModel.onEntryTitleChanged(index, it) },
                            label = { Text(stringResource(R.string.schedule_entry_title)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        TimeField(
                            label = stringResource(R.string.schedule_entry_start),
                            value = entry.start,
                            onClick = { editingEntryTime = index }
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                            DayOfWeek.entries.forEach { day ->
                                FilterChip(
                                    selected = day in entry.days,
                                    onClick = { viewModel.onEntryDayToggled(index, day) },
                                    label = {
                                        Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                                    }
                                )
                            }
                        }

                        MinutesRow(
                            label = stringResource(
                                R.string.schedule_entry_length,
                                entry.durationMinutes
                            ),
                            value = entry.durationMinutes.toFloat(),
                            onChange = { viewModel.onEntryDurationChanged(index, it.toInt()) }
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                            Priority.entries.forEach { priority ->
                                FilterChip(
                                    selected = entry.priority == priority,
                                    onClick = { viewModel.onEntryPriorityChanged(index, priority) },
                                    label = { Text(priority.label()) }
                                )
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                            Flexibility.entries.forEach { flexibility ->
                                FilterChip(
                                    selected = entry.flexibility == flexibility,
                                    onClick = {
                                        viewModel.onEntryFlexibilityChanged(index, flexibility)
                                    },
                                    label = { Text(flexibility.label()) }
                                )
                            }
                        }

                        TextButton(onClick = { viewModel.onRemoveEntry(index) }) {
                            Text(stringResource(R.string.schedule_remove_entry))
                        }
                    }
                }
            }

            FilledTonalButton(onClick = viewModel::onAddEntry) {
                Text(stringResource(R.string.schedule_add_entry))
            }

            Button(
                onClick = viewModel::onSave,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.schedule_save))
            }

            Spacer(Modifier.height(NesaSpacing.xl))
        }
    }

    editingEntryTime?.let { index ->
        val entry = state.entries.getOrNull(index)
        if (entry == null) {
            editingEntryTime = null
        } else {
            NesaTimePickerDialog(
                initial = entry.start,
                confirmLabel = stringResource(R.string.schedule_confirm),
                cancelLabel = stringResource(R.string.schedule_cancel),
                onConfirm = {
                    viewModel.onEntryStartChanged(index, it)
                    editingEntryTime = null
                },
                onDismiss = { editingEntryTime = null }
            )
        }
    }
}

/** A length slider that commits on release, like the ones elsewhere. */
@Composable
private fun MinutesRow(label: String, value: Float, onChange: (Float) -> Unit) {
    var dragged by remember(value) { mutableFloatStateOf(value) }

    Column(Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onChange(dragged) },
            // Five minutes to eight hours, in five-minute steps: enough for a
            // prayer and enough for a working day.
            valueRange = 5f..480f,
            steps = 94
        )
    }
}
