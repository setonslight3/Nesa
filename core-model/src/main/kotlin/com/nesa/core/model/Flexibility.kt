package com.nesa.core.model

/**
 * How much freedom the scheduler has to move an activity.
 */
enum class Flexibility {
    /** A fixed anchor. NESA never moves it automatically. */
    FIXED,

    /** May be moved to another time on the same day. */
    TIME_FLEXIBLE,

    /** May be moved to another day when today cannot reasonably contain it. */
    DAY_FLEXIBLE,

    /** May be dropped from the plan without breaking the day. */
    OPTIONAL,

    /** May move freely, but must finish before its deadline. */
    DEADLINE_BASED;

    /** True when the scheduler is allowed to choose a different start time. */
    val movableWithinDay: Boolean get() = this != FIXED

    /** True when the scheduler may push the activity to a later day. */
    val movableAcrossDays: Boolean get() = this == DAY_FLEXIBLE || this == DEADLINE_BASED

    /** True when the activity may be left out of today's plan without harm. */
    val droppable: Boolean get() = this == OPTIONAL
}
