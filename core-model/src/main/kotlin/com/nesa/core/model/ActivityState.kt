package com.nesa.core.model

/**
 * The lifecycle of a single planned activity.
 *
 * Product rule: [SKIPPED] and [MISSED] are deliberately different states.
 * A skip is an intentional user decision; a miss is the absence of a decision.
 * NESA must never infer a skip from silence.
 */
enum class ActivityState {
    /** Planned, in the future, no decision taken yet. */
    UPCOMING,

    /** Currently running. */
    ACTIVE,

    /** The user reported it done. */
    COMPLETED,

    /** The user explicitly asked to do it later; it is waiting for a new slot. */
    LATER,

    /** The user explicitly decided not to do it today. */
    SKIPPED,

    /** Its window elapsed without any user decision. Recoverable, not a failure. */
    MISSED,

    /** Removed from the plan entirely. */
    CANCELLED;

    /** True when no further automatic scheduling should happen for this block today. */
    val isResolved: Boolean
        get() = this == COMPLETED || this == SKIPPED || this == CANCELLED

    /** True when the block still occupies its slot and must not be moved by the engine. */
    val occupiesSlot: Boolean
        get() = this == ACTIVE || this == COMPLETED

    /** True when the activity still needs a place in the day. */
    val needsPlacement: Boolean
        get() = this == UPCOMING || this == LATER || this == MISSED
}
