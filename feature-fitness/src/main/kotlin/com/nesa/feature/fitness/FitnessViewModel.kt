package com.nesa.feature.fitness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.Exercise
import com.nesa.core.model.PerceivedEffort
import com.nesa.core.model.SetLog
import com.nesa.core.model.SetOutcome
import com.nesa.core.model.WorkoutRoutine
import com.nesa.core.model.WorkoutSession
import com.nesa.core.model.repository.FitnessRepository
import com.nesa.core.scheduling.FitnessSummary
import com.nesa.core.scheduling.WorkoutProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class FitnessUiState(
    val routines: List<WorkoutRoutine> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val recentSessions: List<WorkoutSession> = emptyList(),
    val summary: FitnessSummary = EMPTY_SUMMARY
) {
    val hasRoutines: Boolean get() = routines.isNotEmpty()

    /** Names by id, so a session can name what was trained without a second query. */
    val exerciseNames: Map<String, String> get() = exercises.associate { it.id to it.name }

    companion object {
        /**
         * What an empty history looks like while the first read is in flight.
         *
         * A null `daysSinceLast` rather than zero: "never trained" and "trained
         * today" must not look the same for the instant before the data arrives.
         */
        val EMPTY_SUMMARY = FitnessSummary(
            sessionsThisWeek = 0,
            weeklyTarget = WorkoutProgress.DEFAULT_WEEKLY_TARGET,
            streakWeeks = 0,
            daysSinceLast = null,
            volumeKgThisWeek = 0.0,
            restRecommended = false
        )
    }
}

/**
 * The fitness overview: routines, recent sessions, and how the week is going.
 *
 * All the arithmetic is [WorkoutProgress]'s, not this class's. A view model that
 * computed a streak itself would be a second place the number is decided, and
 * the figure on the screen would eventually disagree with the one under test.
 */
@HiltViewModel
class FitnessViewModel @Inject constructor(
    private val fitness: FitnessRepository,
    private val clock: Clock
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<FitnessUiState> = combine(
        fitness.observeRoutines(),
        fitness.observeExercises(),
        // A history window rather than everything ever logged: the streak looks
        // back a bounded number of weeks, so reading years of sessions to render
        // one screen would be work nobody sees.
        today,
        // The range is fixed when the flow is built, so the upper bound reaches
        // past today: an app left open across midnight would otherwise stop
        // showing a session the moment it was logged.
        fitness.observeSessions(
            LocalDate.now(clock).minusWeeks(HISTORY_WEEKS),
            LocalDate.now(clock).plusDays(FUTURE_MARGIN_DAYS)
        )
    ) { routines, exercises, day, sessions ->
        FitnessUiState(
            routines = routines,
            exercises = exercises,
            recentSessions = sessions.take(RECENT_SESSIONS),
            summary = WorkoutProgress.summarise(sessions, day)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FitnessUiState()
    )

    /** Re-read on resume: the app can sit open across midnight. */
    fun refresh() {
        today.value = LocalDate.now(clock)
    }

    /**
     * Logs a whole routine as performed, exactly as planned.
     *
     * The quick path, for the common case of "I did the workout". Anything more
     * detailed than that belongs in a session editor; recording something
     * approximate is better than recording nothing, and the sets written here
     * are marked as what they are.
     */
    fun onLogRoutine(routine: WorkoutRoutine, effort: PerceivedEffort) {
        viewModelScope.launch {
            val sessionId = UUID.randomUUID().toString()
            val logs = routine.ordered.flatMap { planned ->
                (1..planned.sets).map { setNumber ->
                    SetLog(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        exerciseId = planned.exerciseId,
                        setNumber = setNumber,
                        reps = planned.reps,
                        seconds = planned.seconds,
                        weightKg = planned.weightKg,
                        outcome = SetOutcome.COMPLETED
                    )
                }
            }
            fitness.logSession(
                WorkoutSession(
                    id = sessionId,
                    routineId = routine.id,
                    date = LocalDate.now(clock),
                    durationMinutes = routine.schedulableDuration.toMinutes().toInt(),
                    effort = effort,
                    sets = logs,
                    recordedAt = Instant.now(clock)
                )
            )
            refresh()
        }
    }

    /** Removes a session. Nothing else in the app depends on it having existed. */
    fun onDeleteSession(sessionId: String) {
        viewModelScope.launch {
            fitness.deleteSession(sessionId)
            refresh()
        }
    }

    fun onDeleteRoutine(routineId: String) {
        viewModelScope.launch {
            // Sessions survive on purpose: deleting a plan must not erase the
            // history of having trained with it. See WorkoutSessionEntity.
            fitness.deleteRoutine(routineId)
            refresh()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        /** Enough history for the streak, and no more. */
        const val HISTORY_WEEKS = 12L
        const val RECENT_SESSIONS = 10

        /** Enough that an app left open across midnight keeps working. */
        const val FUTURE_MARGIN_DAYS = 2L
    }
}
