package com.nesa.core.model

/**
 * Why NESA placed an activity where it did.
 *
 * The product rule is that meaningful automatic changes are explained, so every
 * non-trivial move carries one of these. [explain] returns user-facing English;
 * localisation is a later concern and is deliberately not faked here.
 */
sealed interface ChangeReason {

    fun explain(): String

    /** Kept exactly where the user put it. */
    data object Unchanged : ChangeReason {
        override fun explain(): String = "Kept at your chosen time."
    }

    /** Moved because a fixed commitment owns that part of the day. */
    data class MovedForAnchor(val anchorTitle: String) : ChangeReason {
        override fun explain(): String = "Moved because \"$anchorTitle\" is a fixed commitment."
    }

    /** Moved because something more important needed that slot. */
    data class MovedForPriority(val otherTitle: String) : ChangeReason {
        override fun explain(): String = "Moved to make room for \"$otherTitle\"."
    }

    /** Its original time has already passed. */
    data object MovedOutOfPast : ChangeReason {
        override fun explain(): String = "Moved forward because its time had already passed."
    }

    /** Pushed into the evening recovery window. */
    data object MovedToEveningRecovery : ChangeReason {
        override fun explain(): String = "Moved to the evening, your recovery window."
    }

    /** Pulled earlier so the sleep target stays intact. */
    data object MovedForSleepTarget : ChangeReason {
        override fun explain(): String = "Moved earlier to protect your sleep target."
    }

    /** Pulled earlier so it still finishes before its deadline. */
    data object MovedForDeadline : ChangeReason {
        override fun explain(): String = "Moved earlier so it still meets its deadline."
    }

    /** Given a new slot after it was missed. */
    data object RecoveredFromMissed : ChangeReason {
        override fun explain(): String = "Rescheduled after it was missed."
    }

    /** Given a new slot because the user asked to do it later. */
    data object RescheduledOnRequest : ChangeReason {
        override fun explain(): String = "Rescheduled because you asked to do it later."
    }

    /** Today has no room; it is waiting for another day. */
    data object DeferredToAnotherDay : ChangeReason {
        override fun explain(): String = "Today is full — moved to another day."
    }

    /** Today has no room and the activity cannot move to another day. */
    data object NoRoomToday : ChangeReason {
        override fun explain(): String = "No free slot today. Nothing was deleted — it still needs a place."
    }

    /** Two fixed commitments overlap. NESA refuses to silently drop either one. */
    data class AnchorConflict(val otherTitle: String) : ChangeReason {
        override fun explain(): String =
            "Overlaps the fixed commitment \"$otherTitle\". NESA will not move either one for you."
    }

    /** Placed, but it will not finish before the deadline. */
    data object DeadlineAtRisk : ChangeReason {
        override fun explain(): String = "This no longer fits before its deadline."
    }
}
