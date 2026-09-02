package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.GuidancePersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityStateMachineTest {

    @Test
    fun `an upcoming activity can take every user decision`() {
        assertEquals(ActivityState.ACTIVE, ActivityStateMachine.apply(ActivityState.UPCOMING, ActivityEvent.START))
        assertEquals(ActivityState.COMPLETED, ActivityStateMachine.apply(ActivityState.UPCOMING, ActivityEvent.COMPLETE))
        assertEquals(ActivityState.SKIPPED, ActivityStateMachine.apply(ActivityState.UPCOMING, ActivityEvent.SKIP))
        assertEquals(ActivityState.LATER, ActivityStateMachine.apply(ActivityState.UPCOMING, ActivityEvent.DEFER))
        assertEquals(ActivityState.CANCELLED, ActivityStateMachine.apply(ActivityState.UPCOMING, ActivityEvent.CANCEL))
    }

    @Test
    fun `a skip is never reachable from silence and a miss is never a user action`() {
        // MISS may only be raised by NESA, so no screen should ever offer it.
        ActivityState.entries.forEach { state ->
            assertFalse(
                "MISS must not be offered from $state",
                ActivityEvent.MISS in ActivityStateMachine.availableEvents(state)
            )
        }
        // And a deliberate skip can never be turned into a miss afterwards.
        assertFalse(ActivityStateMachine.canApply(ActivityState.SKIPPED, ActivityEvent.MISS))
    }

    @Test
    fun `a missed activity stays recoverable`() {
        assertEquals(ActivityState.LATER, ActivityStateMachine.apply(ActivityState.MISSED, ActivityEvent.DEFER))
        assertEquals(ActivityState.COMPLETED, ActivityStateMachine.apply(ActivityState.MISSED, ActivityEvent.COMPLETE))
        assertEquals(ActivityState.ACTIVE, ActivityStateMachine.apply(ActivityState.MISSED, ActivityEvent.START))
    }

    @Test
    fun `an already missed activity is not missed twice`() {
        assertFalse(ActivityStateMachine.canApply(ActivityState.MISSED, ActivityEvent.MISS))
    }

    @Test
    fun `resolved states can only be reopened`() {
        listOf(ActivityState.COMPLETED, ActivityState.SKIPPED, ActivityState.CANCELLED).forEach { state ->
            assertEquals(setOf(ActivityEvent.REOPEN), ActivityStateMachine.availableEvents(state))
            assertEquals(ActivityState.UPCOMING, ActivityStateMachine.apply(state, ActivityEvent.REOPEN))
        }
    }

    @Test(expected = IllegalActivityTransition::class)
    fun `an invalid transition fails loudly instead of corrupting state`() {
        ActivityStateMachine.apply(ActivityState.COMPLETED, ActivityEvent.START)
    }

    @Test
    fun `state classification matches how the scheduler uses it`() {
        assertTrue(ActivityState.COMPLETED.isResolved)
        assertTrue(ActivityState.SKIPPED.isResolved)
        assertTrue(ActivityState.CANCELLED.isResolved)
        assertFalse(ActivityState.MISSED.isResolved)

        assertTrue(ActivityState.MISSED.needsPlacement)
        assertTrue(ActivityState.LATER.needsPlacement)
        assertTrue(ActivityState.UPCOMING.needsPlacement)

        assertTrue(ActivityState.ACTIVE.occupiesSlot)
        assertTrue(ActivityState.COMPLETED.occupiesSlot)
        assertFalse(ActivityState.SKIPPED.occupiesSlot)
    }

    @Test
    fun `guidance personalities differ in patience but all of them stop`() {
        GuidancePersonality.entries.forEach { personality ->
            assertTrue(personality.maxReminders in 1..6)
            assertTrue(personality.missedGraceMinutes > 0)
        }
        assertTrue(GuidancePersonality.GENTLE.missedGraceMinutes > GuidancePersonality.STRICT.missedGraceMinutes)
        assertEquals(GuidancePersonality.BALANCED, GuidancePersonality.Default)
    }
}
