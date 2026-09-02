package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.GuidancePersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedActivityDetectorTest {

    @Test
    fun `an activity is not missed while it is still within its grace period`() {
        val item = planned("stretch", at(9, 0), 30)
        // Ends 09:30, balanced grace is 30 minutes, so 09:59 is still fine.
        val missed = MissedActivityDetector.detect(listOf(item), on(9, 59))
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `an unanswered activity becomes missed once the grace period elapses`() {
        val item = planned("stretch", at(9, 0), 30)
        val missed = MissedActivityDetector.detect(listOf(item), on(10, 0))
        assertEquals(listOf("stretch"), missed.map { it.block.id })
    }

    @Test
    fun `a skipped activity is never converted into a missed one`() {
        val skipped = planned("run", at(9, 0), 30, state = ActivityState.SKIPPED)
        val completed = planned("shower", at(9, 0), 30, state = ActivityState.COMPLETED)
        val cancelled = planned("call", at(9, 0), 30, state = ActivityState.CANCELLED)

        val missed = MissedActivityDetector.detect(listOf(skipped, completed, cancelled), on(23, 0))
        assertTrue("resolved activities are outside the missed model", missed.isEmpty())
    }

    @Test
    fun `guidance personality controls how quickly silence becomes a miss`() {
        val item = planned("reading", at(9, 0), 30)

        assertTrue(MissedActivityDetector.detect(listOf(item), on(9, 45), GuidancePersonality.STRICT).isNotEmpty())
        assertTrue(MissedActivityDetector.detect(listOf(item), on(9, 45), GuidancePersonality.GENTLE).isEmpty())
    }

    @Test
    fun `a deferred activity can still be missed at its new time`() {
        val item = planned("physio", at(14, 0), 30, state = ActivityState.LATER)
        assertTrue(MissedActivityDetector.detect(listOf(item), on(15, 30)).isNotEmpty())
    }

    @Test
    fun `reminders stop once the personality's limit is reached`() {
        val fresh = planned("water plants", at(9, 0), 30)
        assertTrue(MissedActivityDetector.shouldRemind(fresh, on(9, 0)))

        val exhausted = fresh.copy(block = fresh.block.copy(remindersSent = 2))
        assertFalse(
            "balanced guidance sends two reminders, not an endless stream",
            MissedActivityDetector.shouldRemind(exhausted, on(9, 20))
        )
    }

    @Test
    fun `reminders stop once the activity is already missed`() {
        val item = planned("water plants", at(9, 0), 30)
        assertFalse(MissedActivityDetector.shouldRemind(item, on(11, 0)))
    }
}
