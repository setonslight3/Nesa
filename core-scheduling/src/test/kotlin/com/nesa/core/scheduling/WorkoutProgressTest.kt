package com.nesa.core.scheduling

import com.nesa.core.model.PerceivedEffort
import com.nesa.core.model.RoutineExercise
import com.nesa.core.model.SetLog
import com.nesa.core.model.SetOutcome
import com.nesa.core.model.WorkoutRoutine
import com.nesa.core.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

/**
 * The fitness module's arithmetic.
 *
 * These figures go straight onto a screen the user judges their own week by, so
 * every one of them is pinned here rather than trusted to read correctly.
 */
class WorkoutProgressTest {

    /** A Wednesday, so the week has days either side of the reference day. */
    private val wednesday = LocalDate.of(2026, 9, 9)

    private fun session(
        date: LocalDate,
        effort: PerceivedEffort = PerceivedEffort.MODERATE,
        sets: List<SetLog> = emptyList()
    ) = WorkoutSession(
        id = "s-$date-${effort.name}-${sets.size}",
        date = date,
        durationMinutes = 45,
        effort = effort,
        sets = sets
    )

    private fun set(
        reps: Int?,
        weight: Double?,
        outcome: SetOutcome = SetOutcome.COMPLETED
    ) = SetLog(
        id = "l-$reps-$weight-$outcome",
        sessionId = "s",
        exerciseId = "e",
        setNumber = 1,
        reps = reps,
        weightKg = weight,
        outcome = outcome
    )

    @Test
    fun `an empty history reads as never trained rather than a long gap`() {
        val summary = WorkoutProgress.summarise(emptyList(), wednesday)
        assertEquals(0, summary.sessionsThisWeek)
        assertEquals(0, summary.streakWeeks)
        // Null, not a large number: "you have never logged a workout" and "it
        // has been a while" are different sentences.
        assertNull(summary.daysSinceLast)
        assertFalse(summary.restRecommended)
    }

    @Test
    fun `the week runs Monday to Sunday`() {
        val monday = wednesday.minusDays(2)
        val sunday = wednesday.plusDays(4)
        val summary = WorkoutProgress.summarise(
            listOf(
                session(monday),
                session(wednesday),
                session(sunday),
                // The Sunday before: last week, so it must not be counted.
                session(monday.minusDays(1))
            ),
            wednesday
        )
        assertEquals(3, summary.sessionsThisWeek)
    }

    @Test
    fun `the streak counts completed weeks and ignores the one in progress`() {
        // Three sessions in each of the two previous weeks, none yet this week.
        val sessions = (1..2).flatMap { weeksBack ->
            (0..2).map { day ->
                session(wednesday.minusWeeks(weeksBack.toLong()).minusDays(day.toLong()))
            }
        }
        val summary = WorkoutProgress.summarise(sessions, wednesday)

        assertEquals(0, summary.sessionsThisWeek)
        // A streak that resets every Monday morning would punish the user for
        // the calendar; the current week is deliberately excluded.
        assertEquals(2, summary.streakWeeks)
    }

    @Test
    fun `a missed week ends the streak`() {
        val sessions = (0..2).map { session(wednesday.minusWeeks(1).minusDays(it.toLong())) } +
            // Week two back has only one session, short of the target of three.
            listOf(session(wednesday.minusWeeks(2)))
        assertEquals(1, WorkoutProgress.summarise(sessions, wednesday).streakWeeks)
    }

    @Test
    fun `volume counts loaded reps and nothing else`() {
        val summary = WorkoutProgress.summarise(
            listOf(
                session(
                    wednesday,
                    sets = listOf(
                        set(reps = 10, weight = 20.0),
                        // Partial sets count for what they actually were.
                        set(reps = 5, weight = 20.0, outcome = SetOutcome.PARTIAL),
                        // A skipped set moved nothing.
                        set(reps = 10, weight = 20.0, outcome = SetOutcome.SKIPPED),
                        // Bodyweight and time-based work contribute no load.
                        set(reps = 15, weight = null)
                    )
                )
            ),
            wednesday
        )
        assertEquals(300.0, summary.volumeKgThisWeek, 0.001)
    }

    @Test
    fun `two demanding days in a row earn a rest day`() {
        val sessions = listOf(
            session(wednesday.minusDays(1), PerceivedEffort.HARD),
            session(wednesday.minusDays(2), PerceivedEffort.MAXIMAL)
        )
        assertTrue(WorkoutProgress.restRecommended(sessions, wednesday))
    }

    @Test
    fun `an easy day breaks the run of demanding ones`() {
        val sessions = listOf(
            session(wednesday.minusDays(1), PerceivedEffort.HARD),
            session(wednesday.minusDays(2), PerceivedEffort.EASY)
        )
        assertFalse(WorkoutProgress.restRecommended(sessions, wednesday))
    }

    @Test
    fun `training hard today does not tell the user to rest today`() {
        // Counted backwards from yesterday. A session logged an hour ago must
        // not produce advice the user has already disproved.
        val sessions = listOf(
            session(wednesday, PerceivedEffort.MAXIMAL),
            session(wednesday.minusDays(1), PerceivedEffort.HARD)
        )
        assertFalse(WorkoutProgress.restRecommended(sessions, wednesday))
    }

    @Test
    fun `weekly progress is safe when there is no target`() {
        val summary = WorkoutProgress.summarise(emptyList(), wednesday, weeklyTarget = 0)
        assertEquals(1f, summary.weeklyProgress, 0.001f)
        assertTrue(summary.metWeeklyTarget)
        // A target of zero must not walk the streak loop to its bound.
        assertEquals(0, summary.streakWeeks)
    }

    @Test
    fun `a routine is as long as its work plus the rest between sets`() {
        val routine = WorkoutRoutine(
            id = "r",
            name = "Upper body",
            exercises = listOf(
                // 3 sets of 10 reps at 4s = 120s work, plus 2 rests of 60s.
                RoutineExercise(id = "x1", exerciseId = "e1", position = 0, sets = 3, reps = 10),
                // 2 sets of 30s = 60s work, plus 1 rest of 60s.
                RoutineExercise(
                    id = "x2",
                    exerciseId = "e2",
                    position = 1,
                    sets = 2,
                    reps = null,
                    seconds = 30
                )
            )
        )
        assertEquals(Duration.ofSeconds(120 + 120 + 60 + 60), routine.estimatedDuration)
    }

    @Test
    fun `an empty routine is still long enough to schedule`() {
        // The scheduler rejects a zero-length activity, so a routine with
        // nothing in it yet must not produce one.
        val routine = WorkoutRoutine(id = "r", name = "New routine")
        assertEquals(Duration.ZERO, routine.estimatedDuration)
        assertTrue(routine.schedulableDuration > Duration.ZERO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an exercise must count either reps or seconds`() {
        RoutineExercise(id = "x", exerciseId = "e", position = 0, reps = null, seconds = null)
    }
}
