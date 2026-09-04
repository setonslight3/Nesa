package com.nesa.feature.fitness

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.Exercise
import com.nesa.core.model.ExerciseKind
import com.nesa.core.model.RoutineExercise
import com.nesa.core.model.WorkoutRoutine
import com.nesa.core.model.repository.FitnessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * One row being edited: the plan for an exercise, plus the name to show for it.
 *
 * The name is carried alongside rather than looked up on every recomposition,
 * because an exercise the user has just typed in does not exist in the
 * repository yet — it is written when the routine is saved.
 */
data class EditableExercise(
    val planned: RoutineExercise,
    val name: String,
    val kind: ExerciseKind
)

data class RoutineEditorUiState(
    val routineId: String? = null,
    val name: String = "",
    val focus: String = "",
    val exercises: List<EditableExercise> = emptyList(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false
) {
    val isEditing: Boolean get() = routineId != null

    /** The same calculation the scheduler will use, so the two cannot disagree. */
    val estimatedDuration: Duration
        get() = exercises.fold(Duration.ZERO) { total, item -> total + item.planned.estimatedDuration }
}

/**
 * Creating and editing a workout routine.
 *
 * Exercises are written when the routine is saved, not as the user types, so
 * abandoning a half-built routine leaves no orphaned movements behind in the
 * exercise library.
 */
@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val fitness: FitnessRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editingId: String? = savedStateHandle[FitnessRoutes.ARG_ROUTINE_ID]

    private val _state = MutableStateFlow(RoutineEditorUiState())
    val state: StateFlow<RoutineEditorUiState> = _state.asStateFlow()

    init {
        if (editingId != null) viewModelScope.launch { load(editingId) }
    }

    fun onNameChanged(name: String) = _state.update { it.copy(name = name, nameError = false) }

    fun onFocusChanged(focus: String) = _state.update { it.copy(focus = focus) }

    fun onAddExercise() = _state.update { current ->
        current.copy(
            exercises = current.exercises + EditableExercise(
                planned = RoutineExercise(
                    id = UUID.randomUUID().toString(),
                    exerciseId = UUID.randomUUID().toString(),
                    position = current.exercises.size
                ),
                name = "",
                kind = ExerciseKind.Default
            )
        )
    }

    fun onExerciseNameChanged(index: Int, name: String) =
        updateExercise(index) { it.copy(name = name) }

    fun onExerciseKindChanged(index: Int, kind: ExerciseKind) = updateExercise(index) { item ->
        // Switching to a time-based movement moves the count from reps to
        // seconds rather than leaving both set, which the domain rejects.
        item.copy(
            kind = kind,
            planned = if (kind.isRepBased) {
                item.planned.copy(reps = item.planned.reps ?: RoutineExercise.DEFAULT_REPS, seconds = null)
            } else {
                item.planned.copy(reps = null, seconds = item.planned.seconds ?: DEFAULT_SECONDS)
            }
        )
    }

    fun onSetsChanged(index: Int, sets: Int) = updateExercise(index) {
        it.copy(planned = it.planned.copy(sets = sets.coerceIn(1, RoutineExercise.MAX_SETS)))
    }

    fun onRepsChanged(index: Int, reps: Int) = updateExercise(index) {
        it.copy(planned = it.planned.copy(reps = reps.coerceIn(1, RoutineExercise.MAX_REPS), seconds = null))
    }

    fun onSecondsChanged(index: Int, seconds: Int) = updateExercise(index) {
        it.copy(planned = it.planned.copy(reps = null, seconds = seconds.coerceIn(1, RoutineExercise.MAX_SECONDS)))
    }

    fun onWeightChanged(index: Int, weightKg: Double?) = updateExercise(index) {
        it.copy(planned = it.planned.copy(weightKg = weightKg?.takeIf { kg -> kg > 0.0 }))
    }

    fun onRemoveExercise(index: Int) = _state.update { current ->
        val remaining = current.exercises.filterIndexed { i, _ -> i != index }
        // Positions renumbered so a later reorder cannot leave gaps that make
        // the stored order depend on ids as a tiebreak.
        current.copy(
            exercises = remaining.mapIndexed { i, item ->
                item.copy(planned = item.planned.copy(position = i))
            }
        )
    }

    fun onSave() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        if (current.saving) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = Instant.now(clock)
            val routineId = current.routineId ?: UUID.randomUUID().toString()

            // An exercise with no name is a row the user added and never filled
            // in. Dropped rather than saved as "", which would show as a blank
            // line in the routine forever.
            val named = current.exercises.filter { it.name.isNotBlank() }

            named.forEach { item ->
                fitness.saveExercise(
                    Exercise(id = item.planned.exerciseId, name = item.name.trim(), kind = item.kind)
                )
            }

            fitness.saveRoutine(
                WorkoutRoutine(
                    id = routineId,
                    name = current.name.trim(),
                    focus = current.focus.trim().takeIf { it.isNotBlank() },
                    exercises = named.mapIndexed { index, item ->
                        item.planned.copy(position = index)
                    },
                    createdAt = if (current.isEditing) existingCreatedAt else now,
                    updatedAt = now
                )
            )

            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    private var existingCreatedAt: Instant = Instant.EPOCH

    private suspend fun load(routineId: String) {
        val routine = fitness.routine(routineId) ?: return
        val names = fitness.exercises().associateBy { it.id }
        existingCreatedAt = routine.createdAt
        _state.update {
            it.copy(
                routineId = routine.id,
                name = routine.name,
                focus = routine.focus.orEmpty(),
                exercises = routine.ordered.map { planned ->
                    val exercise = names[planned.exerciseId]
                    EditableExercise(
                        planned = planned,
                        name = exercise?.name.orEmpty(),
                        kind = exercise?.kind ?: ExerciseKind.Default
                    )
                }
            )
        }
    }

    private fun updateExercise(index: Int, block: (EditableExercise) -> EditableExercise) =
        _state.update { current ->
            if (index !in current.exercises.indices) return@update current
            current.copy(
                exercises = current.exercises.mapIndexed { i, item ->
                    if (i == index) block(item) else item
                }
            )
        }

    private companion object {
        const val DEFAULT_SECONDS = 30
    }
}
