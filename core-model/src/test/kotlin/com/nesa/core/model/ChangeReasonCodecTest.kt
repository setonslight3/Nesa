package com.nesa.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangeReasonCodecTest {

    private val everyReason = listOf(
        ChangeReason.Unchanged,
        ChangeReason.MovedForAnchor("Morning lecture"),
        ChangeReason.MovedForPriority("Deep work"),
        ChangeReason.MovedOutOfPast,
        ChangeReason.MovedToEveningRecovery,
        ChangeReason.MovedForSleepTarget,
        ChangeReason.MovedForDeadline,
        ChangeReason.RecoveredFromMissed,
        ChangeReason.RescheduledOnRequest,
        ChangeReason.DeferredToAnotherDay,
        ChangeReason.NoRoomToday,
        ChangeReason.AnchorConflict("Dentist"),
        ChangeReason.DeadlineAtRisk
    )

    @Test
    fun `every reason survives a round trip`() {
        everyReason.forEach { reason ->
            assertEquals(reason, ChangeReasonCodec.decode(ChangeReasonCodec.encode(reason)))
        }
    }

    @Test
    fun `a title containing a colon is not mangled`() {
        val reason = ChangeReason.MovedForAnchor("Standup: team sync")
        assertEquals(reason, ChangeReasonCodec.decode(ChangeReasonCodec.encode(reason)))
    }

    @Test
    fun `nothing encodes and decodes as nothing`() {
        assertNull(ChangeReasonCodec.encode(null))
        assertNull(ChangeReasonCodec.decode(null))
        assertNull(ChangeReasonCodec.decode(""))
        assertNull(ChangeReasonCodec.decode("   "))
    }

    @Test
    fun `a reason written by a newer build degrades instead of crashing`() {
        assertNull(ChangeReasonCodec.decode("MOVED_FOR_SOMETHING_NEW:whatever"))
    }

    @Test
    fun `every reason explains itself in words`() {
        everyReason.forEach { reason ->
            val explanation = reason.explain()
            assert(explanation.isNotBlank()) { "$reason produced no explanation" }
            assert(explanation.endsWith(".")) { "$reason should read as a sentence" }
        }
    }
}
