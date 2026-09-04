package com.nesa.core.scheduling

import com.nesa.core.model.Activity
import com.nesa.core.model.Recurrence
import com.nesa.core.model.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Recurrence rules, and the blocks they produce.
 *
 * These are the Stage 2 equivalent of the scheduler tests: pure, deterministic,
 * and the regression net for behaviour a user only notices weeks later, when the
 * activity that should have come back did not.
 */
class RecurrenceTest {

    private val monday = LocalDate.of(2026, 9, 7)

    private fun activity(
        id: String = "a",
        recurrence: Recurrence = Recurrence.Once,
        preferredStart: LocalTime? = LocalTime.of(9, 0)
    ) = Activity(
        id = id,
        title = "Run",
        duration = Duration.ofMinutes(30),
        preferredStart = preferredStart,
        recurrence = recurrence
    )

    @Test
    fun `a one-off never recurs`() {
        val once = Recurrence.Once
        assertFalse(once.repeats)
        // Every day, not just today: a NONE rule must never generate a block, or
        // the activity it belongs to would be duplicated onto every date.
        (0L..40L).forEach { offset -> assertFalse(once.occursOn(monday.plusDays(offset))) }
    }

    @Test
    fun `every day means every day`() {
        (0L..30L).forEach { offset ->
            assertTrue(Recurrence.EveryDay.occursOn(monday.plusDays(offset)))
        }
    }

    @Test
    fun `every third day counts from the anchor`() {
        val rule = Recurrence(
            frequency = RecurrenceFrequency.DAILY,
            interval = 3,
            startDate = monday
        )
        assertTrue(rule.occursOn(monday))
        assertFalse(rule.occursOn(monday.plusDays(1)))
        assertFalse(rule.occursOn(monday.plusDays(2)))
        assertTrue(rule.occursOn(monday.plusDays(3)))
        assertTrue(rule.occursOn(monday.plusDays(30)))
    }

    @Test
    fun `nothing occurs before the rule starts or after it ends`() {
        val rule = Recurrence(
            frequency = RecurrenceFrequency.DAILY,
            startDate = monday,
            endDate = monday.plusDays(2)
        )
        assertFalse(rule.occursOn(monday.minusDays(1)))
        assertTrue(rule.occursOn(monday))
        assertTrue(rule.occursOn(monday.plusDays(2)))
        assertFalse(rule.occursOn(monday.plusDays(3)))
    }

    @Test
    fun `weekdays skip the weekend`() {
        val rule = Recurrence.Weekdays
        (0L..4L).forEach { offset -> assertTrue(rule.occursOn(monday.plusDays(offset))) }
        assertFalse(rule.occursOn(monday.plusDays(5)))
        assertFalse(rule.occursOn(monday.plusDays(6)))
        assertTrue(rule.occursOn(monday.plusDays(7)))
    }

    @Test
    fun `every other week keeps both of its days in the same week`() {
        // The bug this guards: counting raw days from the anchor puts Monday and
        // Thursday in different intervals, and one of the two silently vanishes.
        val rule = Recurrence(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            startDate = monday
        )
        assertTrue(rule.occursOn(monday))
        assertTrue(rule.occursOn(monday.plusDays(3)))

        // The week off.
        assertFalse(rule.occursOn(monday.plusWeeks(1)))
        assertFalse(rule.occursOn(monday.plusWeeks(1).plusDays(3)))

        assertTrue(rule.occursOn(monday.plusWeeks(2)))
        assertTrue(rule.occursOn(monday.plusWeeks(2).plusDays(3)))
    }

    @Test
    fun `a monthly rule on the 31st still happens in February`() {
        val rule = Recurrence(
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 31)
        )
        assertTrue(rule.occursOn(LocalDate.of(2026, 1, 31)))
        // Clamped to the last day rather than skipped: dropping a third of a
        // monthly commitment would be exactly the silent loss this app avoids.
        assertTrue(rule.occursOn(LocalDate.of(2026, 2, 28)))
        assertFalse(rule.occursOn(LocalDate.of(2026, 2, 27)))
        assertTrue(rule.occursOn(LocalDate.of(2026, 3, 31)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a weekly rule must name a day`() {
        Recurrence(frequency = RecurrenceFrequency.WEEKLY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an interval greater than one needs something to count from`() {
        Recurrence(frequency = RecurrenceFrequency.DAILY, interval = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a rule cannot end before it starts`() {
        Recurrence(
            frequency = RecurrenceFrequency.DAILY,
            startDate = monday,
            endDate = monday.minusDays(1)
        )
    }

    @Test
    fun `the materialiser creates one block on a matching day`() {
        val blocks = RecurrenceMaterialiser.blocksFor(
            date = monday,
            activities = listOf(activity(recurrence = Recurrence.Weekdays)),
            existingActivityIds = emptySet(),
            idFactory = { "block-1" }
        )
        assertEquals(1, blocks.size)
        assertEquals(monday, blocks.first().date)
        assertEquals(LocalTime.of(9, 0), blocks.first().start)
        assertEquals(LocalTime.of(9, 30), blocks.first().end)
    }

    @Test
    fun `the materialiser is idempotent`() {
        // It runs on every refresh of the day. A second block would be a second
        // reminder and a second line on the timeline the user cannot tell apart.
        val repeating = activity(recurrence = Recurrence.Weekdays)
        val blocks = RecurrenceMaterialiser.blocksFor(
            date = monday,
            activities = listOf(repeating),
            existingActivityIds = setOf(repeating.id),
            idFactory = { "block-1" }
        )
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `the materialiser leaves one-off activities alone`() {
        val blocks = RecurrenceMaterialiser.blocksFor(
            date = monday,
            activities = listOf(activity(recurrence = Recurrence.Once)),
            existingActivityIds = emptySet(),
            idFactory = { "block-1" }
        )
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `an activity with no preferred time still gets a block`() {
        // The scheduler decides where it actually goes; the materialiser only
        // has to produce something constructible for it to place.
        val blocks = RecurrenceMaterialiser.blocksFor(
            date = monday,
            activities = listOf(
                activity(recurrence = Recurrence.EveryDay, preferredStart = null)
            ),
            existingActivityIds = emptySet(),
            idFactory = { "block-1" }
        )
        assertEquals(1, blocks.size)
        assertEquals(30, blocks.first().durationMinutes)
    }
}
