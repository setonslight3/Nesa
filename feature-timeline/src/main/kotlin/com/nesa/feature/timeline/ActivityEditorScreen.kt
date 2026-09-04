package com.nesa.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.Flexibility
import com.nesa.core.model.Priority
import com.nesa.core.model.Recurrence
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NesaTimePickerDialog
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.TimeField
import com.nesa.core.ui.format.label
import com.nesa.core.ui.theme.NesaSpacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Add or edit an activity.
 *
 * Flexibility is explained in plain words as the user picks it, because that
 * choice is the one that decides how NESA will treat the activity for the rest
 * of its life — and it is the one people would otherwise guess at.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingTime by remember { mutableStateOf<TimeTarget?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    NesaScaffold(
        title = stringResource(
            if (state.isEditing) R.string.editor_title_edit else R.string.editor_title_new
        ),
        modifier = modifier,
        onBack = onDone
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = NesaSpacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.lg)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                isError = state.titleError,
                supportingText = if (state.titleError) {
                    { Text(stringResource(R.string.editor_name_required)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text(stringResource(R.string.editor_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            TimeField(
                label = stringResource(R.string.editor_start),
                value = state.start,
                onClick = { editingTime = TimeTarget.START }
            )

            Column {
                Text(
                    text = stringResource(R.string.editor_duration),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.editor_duration_value, state.durationMinutes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = state.durationMinutes.toFloat(),
                    onValueChange = { viewModel.onDurationChanged(it.toInt()) },
                    valueRange = 5f..240f,
                    // One step per five minutes: fine enough to be useful,
                    // coarse enough to hit with a thumb.
                    steps = 46
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                Text(
                    text = stringResource(R.string.editor_priority),
                    style = MaterialTheme.typography.bodyLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                    Priority.entries.forEach { priority ->
                        FilterChip(
                            selected = state.priority == priority,
                            onClick = { viewModel.onPriorityChanged(priority) },
                            label = { Text(priority.label()) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                Text(
                    text = stringResource(R.string.editor_flexibility),
                    style = MaterialTheme.typography.bodyLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                    Flexibility.entries.forEach { flexibility ->
                        FilterChip(
                            selected = state.flexibility == flexibility,
                            onClick = { viewModel.onFlexibilityChanged(flexibility) },
                            label = { Text(flexibility.label()) }
                        )
                    }
                }
                NoticeCard(text = state.flexibility.help())
            }

            Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                Text(
                    text = stringResource(R.string.editor_repeat),
                    style = MaterialTheme.typography.bodyLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                    RepeatPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preset.matches(state.recurrence),
                            onClick = { viewModel.onRecurrenceChanged(preset.recurrence) },
                            label = { Text(stringResource(preset.label)) }
                        )
                    }
                    // Not a preset: the days it starts from depend on the date
                    // being edited, which a constant cannot know.
                    FilterChip(
                        selected = state.showsRecurrenceDays &&
                            RepeatPreset.entries.none { it.matches(state.recurrence) },
                        onClick = viewModel::onChooseDaysRequested,
                        label = { Text(stringResource(R.string.editor_repeat_custom)) }
                    )
                }
                if (state.showsRecurrenceDays) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in state.recurrence.daysOfWeek,
                                onClick = { viewModel.onRecurrenceDayToggled(day) },
                                label = {
                                    Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                                }
                            )
                        }
                    }
                }
            }

            if (state.showsDeadline) {
                TimeField(
                    label = stringResource(R.string.editor_deadline),
                    value = state.deadline ?: state.start,
                    onClick = { editingTime = TimeTarget.DEADLINE }
                )
            }

            Button(
                onClick = viewModel::onSave,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = NesaSpacing.xl)
            ) {
                Text(stringResource(R.string.editor_save))
            }
        }
    }

    val target = editingTime
    if (target != null) {
        NesaTimePickerDialog(
            initial = when (target) {
                TimeTarget.START -> state.start
                TimeTarget.DEADLINE -> state.deadline ?: state.start
            },
            confirmLabel = stringResource(R.string.editor_confirm),
            cancelLabel = stringResource(R.string.timeline_cancel),
            onConfirm = { time ->
                when (target) {
                    TimeTarget.START -> viewModel.onStartChanged(time)
                    TimeTarget.DEADLINE -> viewModel.onDeadlineChanged(time)
                }
                editingTime = null
            },
            onDismiss = { editingTime = null }
        )
    }
}

private enum class TimeTarget { START, DEADLINE }

@Composable
private fun Flexibility.help(): String = stringResource(
    when (this) {
        Flexibility.FIXED -> R.string.editor_flexibility_help_fixed
        Flexibility.TIME_FLEXIBLE -> R.string.editor_flexibility_help_time
        Flexibility.DAY_FLEXIBLE -> R.string.editor_flexibility_help_day
        Flexibility.OPTIONAL -> R.string.editor_flexibility_help_optional
        Flexibility.DEADLINE_BASED -> R.string.editor_flexibility_help_deadline
    }
)

/**
 * The repeat choices offered as chips.
 *
 * Presets rather than a rule builder: "every day" and "weekdays" cover almost
 * everything a person actually schedules, and picking days directly covers the
 * rest. The full Recurrence type supports intervals and end dates; nothing in
 * this screen produces them yet, and a builder for rules nobody asked for would
 * be a worse first version than four chips.
 */
private enum class RepeatPreset(val label: Int, val recurrence: Recurrence) {
    ONCE(R.string.editor_repeat_once, Recurrence.Once),
    DAILY(R.string.editor_repeat_daily, Recurrence.EveryDay),
    WEEKDAYS(R.string.editor_repeat_weekdays, Recurrence.Weekdays);

    /**
     * Compared on frequency and days only. A rule carries a start date anchored
     * to the day being edited, so comparing whole objects would leave every chip
     * unselected the moment the user picked one.
     */
    fun matches(current: Recurrence): Boolean =
        current.frequency == recurrence.frequency &&
            current.daysOfWeek == recurrence.daysOfWeek
}
