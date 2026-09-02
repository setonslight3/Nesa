package com.nesa.core.model

/**
 * Serialises a [ChangeReason] for storage.
 *
 * The wire form is `CODE` or `CODE:argument`. Only the first colon separates
 * the two, so a title containing colons survives a round trip. Unknown codes
 * decode to null rather than throwing, so a database written by a newer build
 * degrades to "no explanation" instead of crashing an older one.
 */
object ChangeReasonCodec {

    private const val UNCHANGED = "UNCHANGED"
    private const val MOVED_FOR_ANCHOR = "MOVED_FOR_ANCHOR"
    private const val MOVED_FOR_PRIORITY = "MOVED_FOR_PRIORITY"
    private const val MOVED_OUT_OF_PAST = "MOVED_OUT_OF_PAST"
    private const val MOVED_TO_EVENING = "MOVED_TO_EVENING"
    private const val MOVED_FOR_SLEEP = "MOVED_FOR_SLEEP"
    private const val MOVED_FOR_DEADLINE = "MOVED_FOR_DEADLINE"
    private const val RECOVERED_FROM_MISSED = "RECOVERED_FROM_MISSED"
    private const val RESCHEDULED_ON_REQUEST = "RESCHEDULED_ON_REQUEST"
    private const val DEFERRED_TO_ANOTHER_DAY = "DEFERRED_TO_ANOTHER_DAY"
    private const val NO_ROOM_TODAY = "NO_ROOM_TODAY"
    private const val ANCHOR_CONFLICT = "ANCHOR_CONFLICT"
    private const val DEADLINE_AT_RISK = "DEADLINE_AT_RISK"

    fun encode(reason: ChangeReason?): String? = when (reason) {
        null -> null
        is ChangeReason.Unchanged -> UNCHANGED
        is ChangeReason.MovedForAnchor -> "$MOVED_FOR_ANCHOR:${reason.anchorTitle}"
        is ChangeReason.MovedForPriority -> "$MOVED_FOR_PRIORITY:${reason.otherTitle}"
        is ChangeReason.MovedOutOfPast -> MOVED_OUT_OF_PAST
        is ChangeReason.MovedToEveningRecovery -> MOVED_TO_EVENING
        is ChangeReason.MovedForSleepTarget -> MOVED_FOR_SLEEP
        is ChangeReason.MovedForDeadline -> MOVED_FOR_DEADLINE
        is ChangeReason.RecoveredFromMissed -> RECOVERED_FROM_MISSED
        is ChangeReason.RescheduledOnRequest -> RESCHEDULED_ON_REQUEST
        is ChangeReason.DeferredToAnotherDay -> DEFERRED_TO_ANOTHER_DAY
        is ChangeReason.NoRoomToday -> NO_ROOM_TODAY
        is ChangeReason.AnchorConflict -> "$ANCHOR_CONFLICT:${reason.otherTitle}"
        is ChangeReason.DeadlineAtRisk -> DEADLINE_AT_RISK
    }

    fun decode(encoded: String?): ChangeReason? {
        if (encoded.isNullOrBlank()) return null
        val separator = encoded.indexOf(':')
        val code = if (separator == -1) encoded else encoded.substring(0, separator)
        val argument = if (separator == -1) "" else encoded.substring(separator + 1)
        return when (code) {
            UNCHANGED -> ChangeReason.Unchanged
            MOVED_FOR_ANCHOR -> ChangeReason.MovedForAnchor(argument)
            MOVED_FOR_PRIORITY -> ChangeReason.MovedForPriority(argument)
            MOVED_OUT_OF_PAST -> ChangeReason.MovedOutOfPast
            MOVED_TO_EVENING -> ChangeReason.MovedToEveningRecovery
            MOVED_FOR_SLEEP -> ChangeReason.MovedForSleepTarget
            MOVED_FOR_DEADLINE -> ChangeReason.MovedForDeadline
            RECOVERED_FROM_MISSED -> ChangeReason.RecoveredFromMissed
            RESCHEDULED_ON_REQUEST -> ChangeReason.RescheduledOnRequest
            DEFERRED_TO_ANOTHER_DAY -> ChangeReason.DeferredToAnotherDay
            NO_ROOM_TODAY -> ChangeReason.NoRoomToday
            ANCHOR_CONFLICT -> ChangeReason.AnchorConflict(argument)
            DEADLINE_AT_RISK -> ChangeReason.DeadlineAtRisk
            else -> null
        }
    }
}
