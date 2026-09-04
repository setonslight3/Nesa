package com.nesa.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The fitness module's domain.
 *
 * Three ideas, kept separate on purpose:
 *
 * - an [Exercise] is a movement that exists independently of any plan,
 * - a [WorkoutRoutine] is a plan made of [RoutineExercise] entries,
 * - a [WorkoutSession] is what actually happened, which is frequently not the
 *   plan and must never be overwritten to look like it was.
 *
 * That last split is the same one Stage 1 made between an activity and its
 * placement, and for the same reason: NESA's whole value is in handling the gap
 * between intention and reality honestly. A session that recorded "the routine,
 * as written" every time would make the progress figures a fiction.
 *
 * Nothing here reaches the scheduler directly. A workout is scheduled as an
 * ordinary [Activity] with `module = NesaModule.FITNESS`, so `AdaptiveScheduler`
 * places it by exactly the same rules as everything else — anchors protected,
 * evening overflow, the lot.
 */
enum class ExerciseKind {
    /** Counted in sets and repetitions, optionally loaded. */
    STRENGTH,

    /** Counted in time or distance. */
    CARDIO,

    /** Stretching, warm-ups, cool-downs. Time-based, never loaded. */
    MOBILITY;

    val isRepBased: Boolean get() = this == STRENGTH

    companion object {
        val Default: ExerciseKind = STRENGTH
    }
}

/** A movement, independent of any routine that uses it. */
data class Exercise(
    val id: String,
    val name: String,
    val kind: ExerciseKind = ExerciseKind.Default,
    val notes: String? = null
) {
    init {
        require(name.isNotBlank()) { "Exercise name must not be blank" }
    }
}

/**
 * One exercise as planned inside a routine.
 *
 * [reps] and [seconds] are alternatives, not both: a rep-based exercise counts
 * repetitions and a time-based one counts seconds. Storing both and letting
 * callers guess which is meaningful is how a "3 sets of 0 reps for 0 seconds"
 * ends up on a screen.
 */
data class RoutineExercise(
    val id: String,
    val exerciseId: String,
    /** Position in the routine, ascending. Ties are broken by id, stably. */
    val position: Int,
    val sets: Int = 3,
    val reps: Int? = DEFAULT_REPS,
    val seconds: Int? = null,
    val weightKg: Double? = null,
    val restSeconds: Int = 60
) {
    init {
        require(sets in 1..MAX_SETS) { "A routine exercise must have between 1 and $MAX_SETS sets" }
        require(reps == null || reps in 1..MAX_REPS) { "Reps must be between 1 and $MAX_REPS" }
        require(seconds == null || seconds in 1..MAX_SECONDS) {
            "Duration must be between 1 and $MAX_SECONDS seconds"
        }
        require(reps != null || seconds != null) {
            "A routine exercise must count either repetitions or seconds"
        }
        require(weightKg == null || weightKg > 0.0) { "Weight must be positive when given" }
        require(restSeconds in 0..MAX_SECONDS) { "Rest must be between 0 and $MAX_SECONDS seconds" }
    }

    /**
     * How long this exercise takes, work and rest together.
     *
     * Rep-based work is estimated at [SECONDS_PER_REP], which is deliberately a
     * rough constant rather than a per-exercise tuning: the number feeds the
     * scheduler, and a schedule built on a false precision is worse than one
     * built on an honest approximation. Rest after the final set is not counted
     * — it belongs to whatever comes next, or to nothing.
     */
    val estimatedDuration: Duration
        get() {
            val perSet = seconds ?: ((reps ?: 0) * SECONDS_PER_REP)
            val work = perSet.toLong() * sets
            val rest = restSeconds.toLong() * (sets - 1).coerceAtLeast(0)
            return Duration.ofSeconds(work + rest)
        }

    companion object {
        const val DEFAULT_REPS = 10
        const val MAX_SETS = 20
        const val MAX_REPS = 500
        const val MAX_SECONDS = 4 * 60 * 60
        /** A plain, unhurried repetition. Rough on purpose; see [estimatedDuration]. */
        const val SECONDS_PER_REP = 4
    }
}

/** A named plan: what to do, in what order. */
data class WorkoutRoutine(
    val id: String,
    val name: String,
    /** What it is for, in the user's words — "Upper body", "Easy run". */
    val focus: String? = null,
    val exercises: List<RoutineExercise> = emptyList(),
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH
) {
    init {
        require(name.isNotBlank()) { "Routine name must not be blank" }
    }

    val ordered: List<RoutineExercise>
        get() = exercises.sortedWith(compareBy({ it.position }, { it.id }))

    /**
     * How long the whole routine takes.
     *
     * This is what the activity is scheduled for, so it has to be the same
     * number the user sees on the routine — one calculation, used twice, rather
     * than a screen estimate and a scheduler estimate that quietly disagree.
     */
    val estimatedDuration: Duration
        get() = exercises.fold(Duration.ZERO) { total, item -> total + item.estimatedDuration }

    /** Never zero: the scheduler rejects a zero-length activity. */
    val schedulableDuration: Duration
        get() = estimatedDuration.takeIf { it > Duration.ZERO } ?: MINIMUM_DURATION

    companion object {
        val MINIMUM_DURATION: Duration = Duration.ofMinutes(10)
    }
}

/** How a single set went. */
enum class SetOutcome {
    COMPLETED,
    /** Started and not finished — fewer reps, less weight, cut short. */
    PARTIAL,
    /** Deliberately not done. Never inferred from an absent log; see below. */
    SKIPPED;

    companion object {
        val Default: SetOutcome = COMPLETED
    }
}

/**
 * One set, as performed.
 *
 * A set the user never logged is simply absent. It is **not** a [SKIPPED] set:
 * the same distinction Stage 1 draws between SKIPPED and MISSED holds here, and
 * for the same reason. A skip is a decision; silence is not. Progress figures
 * count what was logged and say nothing about the rest.
 */
data class SetLog(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setNumber: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val weightKg: Double? = null,
    val outcome: SetOutcome = SetOutcome.Default
) {
    init {
        require(setNumber >= 1) { "Set numbers start at 1" }
        require(reps == null || reps >= 0) { "Reps must not be negative" }
        require(seconds == null || seconds >= 0) { "Seconds must not be negative" }
        require(weightKg == null || weightKg >= 0.0) { "Weight must not be negative" }
    }

    /**
     * Load moved by this set, in kilogram-repetitions.
     *
     * Zero for anything that is not a completed, loaded, rep-based set — a
     * partial set contributes what it actually was, and time-based work
     * contributes nothing to a figure that means "load moved".
     */
    val volumeKg: Double
        get() = if (outcome == SetOutcome.SKIPPED) 0.0 else (reps ?: 0) * (weightKg ?: 0.0)
}

/** How hard it felt. The one subjective figure NESA records. */
enum class PerceivedEffort {
    EASY, MODERATE, HARD, MAXIMAL;

    /** Sessions at this level or above are the ones a rest day is for. */
    val isDemanding: Boolean get() = this == HARD || this == MAXIMAL

    companion object {
        val Default: PerceivedEffort = MODERATE
    }
}

/**
 * A workout that happened.
 *
 * @param routineId the plan it followed, or null for something unplanned. An
 *   unplanned session is a first-class thing: a user who went for a run NESA
 *   never scheduled has still trained, and a module that could not record that
 *   would be teaching them to distrust it.
 * @param blockId the scheduled block it fulfilled, when there was one. This is
 *   what lets a logged session mark its activity complete without the fitness
 *   module reaching into the scheduler itself.
 */
data class WorkoutSession(
    val id: String,
    val routineId: String? = null,
    val blockId: String? = null,
    val date: LocalDate,
    val durationMinutes: Int,
    val effort: PerceivedEffort = PerceivedEffort.Default,
    val sets: List<SetLog> = emptyList(),
    val notes: String? = null,
    val recordedAt: Instant = Instant.EPOCH
) {
    init {
        require(durationMinutes >= 0) { "A session cannot have a negative duration" }
    }

    val volumeKg: Double get() = sets.sumOf { it.volumeKg }

    val completedSets: Int get() = sets.count { it.outcome == SetOutcome.COMPLETED }
}
