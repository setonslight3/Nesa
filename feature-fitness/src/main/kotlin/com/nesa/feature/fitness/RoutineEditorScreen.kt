package com.nesa.feature.fitness

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.ExerciseKind
import com.nesa.core.model.RoutineExercise
import com.nesa.core.ui.component.NesaScaffold
import com.nesa.core.ui.component.NoticeCard
import com.nesa.core.ui.component.SectionHeader
import com.nesa.core.ui.theme.NesaSpacing

/**
 * Building a routine.
 *
 * Each exercise is a card with a name, a kind, and the two or three numbers
 * that kind actually needs — reps for strength, seconds for anything timed. The
 * form never shows both, because the domain will not store both and a field
 * that silently does nothing is worse than no field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    NesaScaffold(
        title = stringResource(
            if (state.isEditing) R.string.routine_title_edit else R.string.routine_title_new
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
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text(stringResource(R.string.routine_name)) },
                isError = state.nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.nameError) {
                NoticeCard(text = stringResource(R.string.routine_name_error))
            }

            OutlinedTextField(
                value = state.focus,
                onValueChange = viewModel::onFocusChanged,
                label = { Text(stringResource(R.string.routine_focus)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(title = stringResource(R.string.routine_exercises))

            if (state.exercises.isEmpty()) {
                NoticeCard(text = stringResource(R.string.routine_no_exercises))
            }

            state.exercises.forEachIndexed { index, item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(NesaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
                    ) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { viewModel.onExerciseNameChanged(index, it) },
                            label = { Text(stringResource(R.string.routine_exercise_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                            ExerciseKind.entries.forEach { kind ->
                                FilterChip(
                                    selected = item.kind == kind,
                                    onClick = { viewModel.onExerciseKindChanged(index, kind) },
                                    label = { Text(stringResource(kind.labelRes())) }
                                )
                            }
                        }

                        NumberRow(
                            label = stringResource(R.string.routine_sets, item.planned.sets),
                            value = item.planned.sets.toFloat(),
                            range = 1f..10f,
                            steps = 8,
                            onChange = { viewModel.onSetsChanged(index, it.toInt()) }
                        )

                        // Reps or seconds, never both: which one is meaningful
                        // is decided by the kind, and the domain rejects a row
                        // that carries both.
                        if (item.kind.isRepBased) {
                            NumberRow(
                                label = stringResource(
                                    R.string.routine_reps,
                                    item.planned.reps ?: RoutineExercise.DEFAULT_REPS
                                ),
                                value = (item.planned.reps ?: RoutineExercise.DEFAULT_REPS).toFloat(),
                                range = 1f..30f,
                                steps = 28,
                                onChange = { viewModel.onRepsChanged(index, it.toInt()) }
                            )
                            OutlinedTextField(
                                value = item.planned.weightKg?.toString().orEmpty(),
                                onValueChange = { text ->
                                    viewModel.onWeightChanged(index, text.toDoubleOrNull())
                                },
                                label = { Text(stringResource(R.string.routine_weight)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            NumberRow(
                                label = stringResource(
                                    R.string.routine_seconds,
                                    item.planned.seconds ?: 30
                                ),
                                value = (item.planned.seconds ?: 30).toFloat(),
                                range = 10f..300f,
                                steps = 28,
                                onChange = { viewModel.onSecondsChanged(index, it.toInt()) }
                            )
                        }

                        TextButton(onClick = { viewModel.onRemoveExercise(index) }) {
                            Text(stringResource(R.string.routine_remove))
                        }
                    }
                }
            }

            FilledTonalButton(onClick = viewModel::onAddExercise) {
                Text(stringResource(R.string.routine_add_exercise))
            }

            Text(
                text = stringResource(
                    R.string.routine_estimated,
                    state.estimatedDuration.toMinutes().toInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = viewModel::onSave,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.routine_save))
            }

            Spacer(Modifier.height(NesaSpacing.xl))
        }
    }
}

/**
 * A labelled slider that commits once.
 *
 * The same shape the alarm screen uses, and for the same reason: writing on
 * every pixel of a drag would be dozens of state updates for one decision.
 */
@Composable
private fun NumberRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    // Local while dragging, committed on release. The key on `value` is what
    // makes the thumb follow a change that came from elsewhere — switching an
    // exercise from reps to seconds rewrites this number underneath the slider.
    var dragged by remember(value) { mutableFloatStateOf(value) }

    Column(Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onChange(dragged) },
            valueRange = range,
            steps = steps
        )
    }
}

internal fun ExerciseKind.labelRes(): Int = when (this) {
    ExerciseKind.STRENGTH -> R.string.kind_strength
    ExerciseKind.CARDIO -> R.string.kind_cardio
    ExerciseKind.MOBILITY -> R.string.kind_mobility
}
