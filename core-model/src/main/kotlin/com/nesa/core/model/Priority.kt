package com.nesa.core.model

/**
 * How important an activity is when NESA has to choose what survives a
 * crowded day. Declaration order is significant: earlier entries win.
 */
enum class Priority {
    /** Protected commitments: work, school, appointments, deadlines, sleep. */
    CRITICAL,

    /** Important personal goals: a planned workout, a major project. */
    HIGH,

    /** Useful but movable: learning, reading. */
    NORMAL,

    /** Optional: entertainment and nice-to-haves. */
    LOW;

    /** Lower rank means scheduled first. */
    val rank: Int get() = ordinal
}
