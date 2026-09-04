package com.nesa.core.scheduling

import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The figures a user judges their own week by.
 *
 * The assertion that matters most is the one about skips: a person who
 * deliberately clears three things off a bad day must not be shown a worse
 * number for having made a decision.
 */
class PlanStatisticsTest {

    /** A Monday, so the week boundaries read clearly. */
    private val monday = LocalDate.of(2026, 9, 7)

    private var counter = 0

    private fun records(date: LocalDate, result: CompletionResult, count: Int) =
        (1..count).map {
            CompletionRecord(
                id = "r${counter++}",
                activityId = "a",
                blockId = "b",
                date = date,
                result = result,
                recordedAt = Instant.EPOCH
            )
        }

    @Test
    fun `the parts always add up to the total`() {
        val day = PlanStatistics.forDay(
            records(monday, CompletionResult.COMPLETED, 2) +
                records(monday, CompletionResult.SKIPPED, 1) +
                records(monday, CompletionResult.MISSED, 3) +
                records(monday, CompletionResult.CANCELLED, 1),
            monday
        )
        assertEquals(7, day.planned)
        assertEquals(2, day.completed)
        assertEquals(3, day.missed)
    }

    @Test
    fun `deciding to skip does not count against you`() {
        // Two done, one missed, and five deliberately cleared. The rate is
        // measured against what was actually left to do: 2 of 3.
        val day = PlanStatistics.forDay(
            records(monday, CompletionResult.COMPLETED, 2) +
                records(monday, CompletionResult.MISSED, 1) +
                records(monday, CompletionResult.SKIPPED, 5),
            monday
        )
        assertEquals(2f / 3f, day.completionRate, 0.001f)
    }

    @Test
    fun `an empty day is empty rather than a failure`() {
        val day = PlanStatistics.forDay(emptyList(), monday)
        assertFalse(day.hasData)
        assertEquals(0f, day.completionRate, 0.001f)
    }

    @Test
    fun `a week runs Monday to Sunday and takes its start from any day in it`() {
        val history = records(monday, CompletionResult.COMPLETED, 1) +
            records(monday.plusDays(6), CompletionResult.COMPLETED, 1) +
            // The Sunday before: a different week, and must not be counted.
            records(monday.minusDays(1), CompletionResult.COMPLETED, 5)

        val week = PlanStatistics.forWeek(history, monday.plusDays(3))
        assertEquals(monday, week.weekStart)
        assertEquals(7, week.days.size)
        assertEquals(2, week.completed)
    }

    @Test
    fun `the best day is the one with the most done, or none at all`() {
        val history = records(monday, CompletionResult.COMPLETED, 1) +
            records(monday.plusDays(2), CompletionResult.COMPLETED, 4)

        assertEquals(monday.plusDays(2), PlanStatistics.forWeek(history, monday).bestDay?.date)
        // Null rather than an arbitrary Monday when nothing happened at all.
        assertNull(PlanStatistics.forWeek(emptyList(), monday).bestDay)
    }

    @Test
    fun `a past day of nothing but skips ends the streak`() {
        // Monday was done, Tuesday was entirely skipped, and it is now Wednesday.
        // Tuesday is over and nothing was done on it: a streak is a record of
        // doing things, not of opening the app.
        val history = records(monday, CompletionResult.COMPLETED, 1) +
            records(monday.plusDays(1), CompletionResult.SKIPPED, 3)

        assertEquals(0, PlanStatistics.streakDays(history, monday.plusDays(2)))
    }

    @Test
    fun `skipping everything so far today does not end the streak yet`() {
        // The counterpart to the test above, and the pair of them is the whole
        // rule. Today is still in progress, so it is neutral rather than a
        // failure — the user may yet finish something this afternoon.
        val history = records(monday, CompletionResult.COMPLETED, 1) +
            records(monday.plusDays(1), CompletionResult.COMPLETED, 1) +
            records(monday.plusDays(2), CompletionResult.SKIPPED, 3)

        assertEquals(2, PlanStatistics.streakDays(history, monday.plusDays(2)))
        assertEquals(2, PlanStatistics.streakDays(history, monday.plusDays(1)))
    }

    @Test
    fun `a streak does not look broken before the day is over`() {
        // Nothing logged today at all. Yesterday and the day before were fine,
        // and the streak should still read two rather than zero at breakfast.
        val history = records(monday, CompletionResult.COMPLETED, 1) +
            records(monday.plusDays(1), CompletionResult.COMPLETED, 1)

        assertEquals(2, PlanStatistics.streakDays(history, monday.plusDays(2)))
    }

    @Test
    fun `a gap ends the streak`() {
        val history = records(monday, CompletionResult.COMPLETED, 1) +
            records(monday.plusDays(2), CompletionResult.COMPLETED, 1)
        assertEquals(1, PlanStatistics.streakDays(history, monday.plusDays(2)))
    }

    @Test
    fun `an empty history has no week worth showing`() {
        assertFalse(PlanStatistics.forWeek(emptyList(), monday).hasData)
        assertTrue(PlanStatistics.forWeek(emptyList(), monday).days.all { !it.hasData })
        assertEquals(0, PlanStatistics.streakDays(emptyList(), monday))
    }
}
