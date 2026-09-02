package com.nesa.core.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * A concrete placement of an [Activity] on one day.
 *
 * [start] and [end] are wall-clock times on [date]. A block never crosses
 * midnight: the day is closed by the sleep target, and anything that does not
 * fit is deferred rather than wrapped.
 */
data class ScheduleBlock(
    val id: String,
    val activityId: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val state: ActivityState = ActivityState.UPCOMING,
    /** The user pinned this placement; the scheduler treats it as immovable. */
    val locked: Boolean = false,
    /** Why it currently sits here. Null means it has never been moved. */
    val changeReason: ChangeReason? = null,
    /** How many reminders have already been delivered for this block. */
    val remindersSent: Int = 0
) {
    init {
        require(!end.isBefore(start)) { "ScheduleBlock end must not precede start" }
    }

    val startMinuteOfDay: Int get() = start.hour * 60 + start.minute
    val endMinuteOfDay: Int get() = end.hour * 60 + end.minute
    val durationMinutes: Int get() = endMinuteOfDay - startMinuteOfDay
}

/**
 * An [Activity] joined with its placement. This is the unit the scheduling
 * engine consumes and produces, so callers never have to correlate two lists.
 */
data class PlannedActivity(
    val activity: Activity,
    val block: ScheduleBlock
) {
    val id: String get() = block.id
    val title: String get() = activity.title
    val priority: Priority get() = activity.priority
    val flexibility: Flexibility get() = activity.flexibility
    val state: ActivityState get() = block.state

    /**
     * Immovable for one of three reasons: the user fixed it, the user pinned
     * this exact placement, or it is already running/finished.
     */
    val isImmovable: Boolean
        get() = activity.isAnchor || block.locked || block.state.occupiesSlot
}
