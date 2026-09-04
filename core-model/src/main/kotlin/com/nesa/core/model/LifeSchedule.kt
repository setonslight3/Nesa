package com.nesa.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/**
 * What a life schedule is for.
 *
 * The kind is not decoration: it sets sensible defaults for a new entry and it
 * is what lets the product rule "users are never forced to configure a module
 * they do not use" be enforced per kind. Someone who wants a work schedule and
 * no prayer times gets exactly that.
 */
enum class LifeScheduleKind {
    /** Employment. Critical and fixed by default: it is the day's spine. */
    WORK,

    /** School, university, classes. Same shape as work. */
    SCHOOL,

    /** Recurring training or practice. Important, but movable. */
    TRAINING,

    /**
     * Prayer or observance. Fixed times the day is arranged around, never
     * moved and never rescheduled by NESA.
     */
    PRAYER,

    /** Meals. Short, regular, and flexible enough to slide around a meeting. */
    MEAL,

    /** Anything else with a weekly rhythm. */
    CUSTOM;

    /** What a new entry of this kind starts as, before the user edits it. */
    val defaultPriority: Priority
        get() = when (this) {
            WORK, SCHOOL, PRAYER -> Priority.CRITICAL
            TRAINING -> Priority.HIGH
            MEAL -> Priority.NORMAL
            CUSTOM -> Priority.NORMAL
        }

    val defaultFlexibility: Flexibility
        get() = when (this) {
            // Work, school and prayer are anchors. The scheduler plans the rest
            // of the day around them and never moves them itself.
            WORK, SCHOOL, PRAYER -> Flexibility.FIXED
            TRAINING, MEAL, CUSTOM -> Flexibility.TIME_FLEXIBLE
        }

    val defaultDuration: Duration
        get() = when (this) {
            WORK, SCHOOL -> Duration.ofHours(8)
            TRAINING -> Duration.ofMinutes(60)
            PRAYER -> Duration.ofMinutes(15)
            MEAL -> Duration.ofMinutes(30)
            CUSTOM -> Duration.ofMinutes(60)
        }
}

/**
 * One recurring commitment inside a schedule: a title, some days, and a time.
 *
 * Kept separate from [Activity] rather than being one, because a schedule is a
 * *description* the user maintains and an activity is what actually appears on
 * a day. Editing "Work" from five days to four should change the description
 * once; the activities follow from it.
 */
data class ScheduleEntry(
    val id: String,
    val title: String,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val duration: Duration,
    val priority: Priority,
    val flexibility: Flexibility
) {
    init {
        require(title.isNotBlank()) { "A schedule entry needs a title" }
        require(days.isNotEmpty()) { "A schedule entry must happen on at least one day" }
        require(!duration.isNegative && !duration.isZero) { "A schedule entry must last some time" }
    }

    val durationMinutes: Int get() = duration.toMinutes().toInt()
}

/**
 * A named, optional bundle of recurring commitments.
 *
 * This is the Stage 2 "Life" module: work, school, training, prayer, meals. The
 * specification is explicit that none of them is mandatory and each can be
 * enabled independently — hence [enabled] on the schedule itself rather than one
 * global switch, so a user can keep a work schedule and drop a meal one without
 * losing either.
 *
 * A schedule owns no scheduling logic. `LifeScheduleApplier` turns it into
 * ordinary activities carrying a `Recurrence`, and from there `AdaptiveScheduler`
 * treats them like anything else — anchors protected, flexible work moved around
 * them. That is the whole reason the Life module needed no scheduler changes.
 */
data class LifeSchedule(
    val id: String,
    val name: String,
    val kind: LifeScheduleKind,
    val enabled: Boolean = true,
    val entries: List<ScheduleEntry> = emptyList()
) {
    init {
        require(name.isNotBlank()) { "A schedule needs a name" }
    }

    val ordered: List<ScheduleEntry>
        get() = entries.sortedWith(compareBy({ it.start }, { it.title }))
}
