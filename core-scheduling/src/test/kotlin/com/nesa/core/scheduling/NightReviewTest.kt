package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.ChangeReason
import com.nesa.core.model.Flexibility
import com.nesa.core.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Closing the day, and recovering what did not happen.
 *
 * The product's own success criterion is that "a missed activity can be
 * recovered without rebuilding the day manually". These are the rules that have
 * to hold for that to be true, and the ones about *refusing* to suggest matter
 * as much as the ones about suggesting.
 */
class NightReviewTest {

    private val tomorrow = TestDate.plusDays(1)
    private val evening = at(19, 0)

    private fun review(
        today: List<com.nesa.core.model.PlannedActivity>,
        next: List<com.nesa.core.model.PlannedActivity> = emptyList(),
        now: LocalTime = evening
    ) = NightReview.of(TestDate, today, next, TestWindow, now)

    @Test
    fun `a settled day has nothing to recover`() {
        val result = review(
            listOf(
                planned("done", at(9, 0), 60, state = ActivityState.COMPLETED),
                planned("chose-not-to", at(10, 0), 60, state = ActivityState.SKIPPED)
            )
        )
        assertTrue(result.isSettled)
        assertEquals(1, result.completed.size)
        assertEquals(1, result.skipped.size)
        // A skip is a decision. It is never offered a new home, because nothing
        // about it needs recovering.
        assertTrue(result.missed.isEmpty())
    }

    @Test
    fun `something missed early is offered the rest of today`() {
        val result = review(
            listOf(planned("run", at(8, 0), 30, state = ActivityState.MISSED)),
            now = at(17, 0)
        )
        val suggestion = result.missed.single().suggestion
        // 17:00 is still inside the day; the night does not start until 21:00.
        assertEquals(RescheduleSuggestion.LaterToday(at(17, 0)), suggestion)
    }

    @Test
    fun `today's remaining fixed commitments are worked around, not through`() {
        val result = review(
            listOf(
                planned("run", at(8, 0), 60, state = ActivityState.MISSED),
                planned(
                    "call",
                    at(17, 0),
                    60,
                    flexibility = Flexibility.FIXED
                )
            ),
            now = at(17, 0)
        )
        // The gap at 17:00 is taken, so the suggestion is after the call.
        assertEquals(RescheduleSuggestion.LaterToday(at(18, 0)), result.missed.single().suggestion)
    }

    @Test
    fun `once the night has started the answer is tomorrow`() {
        val result = review(
            listOf(planned("read", at(9, 0), 60, state = ActivityState.MISSED)),
            now = at(22, 0)
        )
        assertEquals(
            RescheduleSuggestion.Tomorrow(tomorrow, at(7, 0)),
            result.missed.single().suggestion
        )
    }

    @Test
    fun `tomorrow's anchors are respected when finding room`() {
        val result = review(
            today = listOf(planned("study", at(9, 0), 60, state = ActivityState.MISSED)),
            next = listOf(
                planned(
                    "work",
                    at(7, 0),
                    5 * 60,
                    flexibility = Flexibility.FIXED,
                    date = tomorrow
                )
            ),
            now = at(22, 0)
        )
        assertEquals(
            RescheduleSuggestion.Tomorrow(tomorrow, at(12, 0)),
            result.missed.single().suggestion
        )
    }

    @Test
    fun `a day-flexible activity goes to tomorrow even when today has room`() {
        // By definition it is happier on another day than crammed into this one.
        val result = review(
            listOf(
                planned(
                    "deep-work",
                    at(9, 0),
                    60,
                    flexibility = Flexibility.DAY_FLEXIBLE,
                    state = ActivityState.MISSED
                )
            ),
            now = at(15, 0)
        )
        assertTrue(result.missed.single().suggestion is RescheduleSuggestion.Tomorrow)
    }

    @Test
    fun `a fixed commitment is not moved on the user's behalf`() {
        val result = review(
            listOf(
                planned(
                    "appointment",
                    at(9, 0),
                    60,
                    flexibility = Flexibility.FIXED,
                    state = ActivityState.MISSED
                )
            )
        )
        val suggestion = result.missed.single().suggestion
        assertTrue(suggestion is RescheduleSuggestion.NoRoom)
        // The refusal has to say something a person can read.
        assertTrue((suggestion as RescheduleSuggestion.NoRoom).reason.isNotBlank())
    }

    @Test
    fun `only optional and unimportant work is ever offered up`() {
        val droppable = review(
            listOf(
                planned(
                    "scroll",
                    at(9, 0),
                    30,
                    priority = Priority.LOW,
                    flexibility = Flexibility.OPTIONAL,
                    state = ActivityState.MISSED
                )
            )
        )
        assertEquals(RescheduleSuggestion.LetItGo, droppable.missed.single().suggestion)

        // The bug this guards: enum ordering puts LOW last, so `<= Priority.LOW`
        // is true for every priority and would have offered to throw away
        // something critical purely because it was marked optional.
        val critical = review(
            listOf(
                planned(
                    "backup",
                    at(9, 0),
                    30,
                    priority = Priority.CRITICAL,
                    flexibility = Flexibility.OPTIONAL,
                    state = ActivityState.MISSED
                )
            ),
            now = at(15, 0)
        )
        assertFalse(critical.missed.single().suggestion == RescheduleSuggestion.LetItGo)
    }

    @Test
    fun `work whose deadline was today is refused, not quietly pushed to tomorrow`() {
        val result = review(
            listOf(
                planned(
                    "submit",
                    at(9, 0),
                    60,
                    flexibility = Flexibility.DEADLINE_BASED,
                    deadline = on(12, 0),
                    state = ActivityState.MISSED
                )
            ),
            now = at(19, 0)
        )
        // Proposing tomorrow would be a lie the user only discovers too late.
        assertTrue(result.missed.single().suggestion is RescheduleSuggestion.NoRoom)
    }

    @Test
    fun `a full tomorrow is admitted rather than papered over`() {
        val result = review(
            today = listOf(planned("project", at(9, 0), 4 * 60, state = ActivityState.MISSED)),
            next = listOf(
                planned(
                    "all-day",
                    at(7, 0),
                    14 * 60,
                    flexibility = Flexibility.FIXED,
                    date = tomorrow
                )
            ),
            now = at(22, 0)
        )
        assertTrue(result.missed.single().suggestion is RescheduleSuggestion.NoRoom)
    }

    @Test
    fun `deferred and missed are reviewed separately`() {
        val result = review(
            listOf(
                planned("later", at(9, 0), 30, state = ActivityState.LATER),
                planned("missed", at(10, 0), 30, state = ActivityState.MISSED)
            ),
            now = at(15, 0)
        )
        assertEquals(1, result.deferred.size)
        assertEquals(1, result.missed.size)
        assertFalse(result.isSettled)
    }

    @Test
    fun `automatic moves are surfaced with their reason still attached`() {
        val moved = planned("gym", at(19, 0), 60).let {
            it.copy(block = it.block.copy(changeReason = ChangeReason.AnchorConflict("work")))
        }
        val result = review(listOf(moved))
        assertEquals(1, result.moved.size)
    }
}
