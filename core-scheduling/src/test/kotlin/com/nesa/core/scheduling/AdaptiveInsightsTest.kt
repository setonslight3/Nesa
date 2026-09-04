package com.nesa.core.scheduling

import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import com.nesa.core.model.DayWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * What NESA is allowed to conclude about a person from their own history.
 *
 * The assertions that matter most here are the ones about *not* concluding
 * things: from too little evidence, from records that cannot answer, and from a
 * skip, which is a decision rather than a failure.
 */
class AdaptiveInsightsTest {

    private val window = DayWindow.Default
    private val date = LocalDate.of(2026, 9, 9)

    private fun record(
        minute: Int?,
        result: CompletionResult,
        id: String = "r${(0..1_000_000).random()}"
    ) = CompletionRecord(
        id = id,
        activityId = "a",
        blockId = "b",
        date = date,
        result = result,
        recordedAt = Instant.EPOCH,
        scheduledStartMinute = minute
    )

    private fun records(minute: Int, result: CompletionResult, count: Int) =
        (1..count).map { record(minute, result, id = "$minute-$result-$it") }

    @Test
    fun `bands follow the user's own day, not the clock`() {
        // A day whose morning runs until 14:00. 13:00 is still morning for this
        // person, whatever a generic definition would say.
        val lateMorning = window.copy(morningEnds = LocalTime.of(14, 0)).validated()
        assertEquals(DayBand.MORNING, AdaptiveInsights.bandOf(LocalTime.of(13, 0), lateMorning))
        assertEquals(DayBand.AFTERNOON, AdaptiveInsights.bandOf(LocalTime.of(13, 0), window))
    }

    @Test
    fun `records that cannot say when they were meant to happen are skipped`() {
        // Written before scheduledStartMinute existed. Guessing a slot for them
        // would teach the learner something that was never true.
        val insights = AdaptiveInsights.byTimeBand(
            (1..10).map { record(null, CompletionResult.COMPLETED, id = "old-$it") },
            window
        )
        assertTrue(insights.all { it.attempts == 0 })
    }

    @Test
    fun `a skip is not a miss`() {
        val morning = DayWindow.minuteOf(LocalTime.of(8, 0))
        val insight = AdaptiveInsights
            .byTimeBand(
                records(morning, CompletionResult.COMPLETED, 3) +
                    records(morning, CompletionResult.SKIPPED, 2) +
                    records(morning, CompletionResult.MISSED, 1),
                window
            )
            .first { it.band == DayBand.MORNING }

        assertEquals(6, insight.attempts)
        assertEquals(3, insight.completed)
        assertEquals(2, insight.skipped)
        assertEquals(1, insight.missed)
    }

    @Test
    fun `nothing is concluded from too little evidence`() {
        val morning = DayWindow.minuteOf(LocalTime.of(8, 0))
        val thin = records(morning, CompletionResult.COMPLETED, AdaptiveInsights.MINIMUM_ATTEMPTS - 1)

        val insight = AdaptiveInsights.byTimeBand(thin, window).first { it.band == DayBand.MORNING }
        // The rate is perfect and the conclusion is still refused.
        assertEquals(1f, insight.completionRate, 0.001f)
        assertFalse(insight.isTrustworthy)
        assertNull(AdaptiveInsights.strongestBand(thin, window))
    }

    @Test
    fun `the strongest band is the one actually finished most often`() {
        val morning = DayWindow.minuteOf(LocalTime.of(8, 0))
        val evening = DayWindow.minuteOf(LocalTime.of(20, 0))

        val history = records(morning, CompletionResult.COMPLETED, 8) +
            records(morning, CompletionResult.MISSED, 2) +
            records(evening, CompletionResult.COMPLETED, 2) +
            records(evening, CompletionResult.MISSED, 8)

        val strongest = AdaptiveInsights.strongestBand(history, window)
        assertEquals(DayBand.MORNING, strongest?.band)
        assertEquals(0.8f, strongest?.completionRate ?: 0f, 0.001f)
    }

    @Test
    fun `a weak band is weak relative to this person, not to an ideal`() {
        val morning = DayWindow.minuteOf(LocalTime.of(8, 0))
        val evening = DayWindow.minuteOf(LocalTime.of(20, 0))

        // Someone who finishes 40% of what they plan is over-planning, not
        // failing. The useful fact is still that mornings beat evenings.
        val history = records(morning, CompletionResult.COMPLETED, 6) +
            records(morning, CompletionResult.MISSED, 4) +
            records(evening, CompletionResult.COMPLETED, 1) +
            records(evening, CompletionResult.MISSED, 9)

        val weak = AdaptiveInsights.weakBands(history, window)
        assertEquals(listOf(DayBand.EVENING), weak.map { it.band })
    }

    @Test
    fun `one band alone is never called weak`() {
        // With nothing to compare against, "worse than average" is meaningless:
        // the single band *is* the average.
        val morning = DayWindow.minuteOf(LocalTime.of(8, 0))
        val history = records(morning, CompletionResult.MISSED, 20)
        assertTrue(AdaptiveInsights.weakBands(history, window).isEmpty())
    }

    @Test
    fun `an empty history says nothing at all`() {
        assertNull(AdaptiveInsights.strongestBand(emptyList(), window))
        assertTrue(AdaptiveInsights.weakBands(emptyList(), window).isEmpty())
        assertTrue(AdaptiveInsights.byTimeBand(emptyList(), window).all { it.attempts == 0 })
    }
}
