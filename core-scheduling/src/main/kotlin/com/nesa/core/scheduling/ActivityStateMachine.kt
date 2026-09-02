package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState

/**
 * The things that can happen to an activity.
 *
 * [SKIP] and [MISS] are separate on purpose. A skip is a decision the user
 * makes; a miss is what happens when no decision arrives. Only the missed
 * detector may raise [MISS], and no user action ever produces it.
 */
enum class ActivityEvent {
    /** The user started it. */
    START,

    /** The user reported it done. */
    COMPLETE,

    /** The user deliberately dropped it for today. */
    SKIP,

    /** The user asked for it later; it needs a new slot. */
    DEFER,

    /** The user removed it from the plan. */
    CANCEL,

    /** Its window elapsed with no response. Raised by NESA, never by a tap. */
    MISS,

    /** Undo a resolved state and put it back in play. */
    REOPEN
}

/** Raised when a caller asks for a transition the model does not allow. */
class IllegalActivityTransition(
    val from: ActivityState,
    val event: ActivityEvent
) : IllegalStateException("Cannot apply $event to an activity in state $from")

/**
 * The single source of truth for how an activity moves between states.
 *
 * Keeping this in one deterministic, exhaustively tested table is what stops
 * state handling from drifting apart across screens, notification actions and
 * background workers.
 */
object ActivityStateMachine {

    private val transitions: Map<ActivityState, Map<ActivityEvent, ActivityState>> = mapOf(
        ActivityState.UPCOMING to mapOf(
            ActivityEvent.START to ActivityState.ACTIVE,
            ActivityEvent.COMPLETE to ActivityState.COMPLETED,
            ActivityEvent.SKIP to ActivityState.SKIPPED,
            ActivityEvent.DEFER to ActivityState.LATER,
            ActivityEvent.CANCEL to ActivityState.CANCELLED,
            ActivityEvent.MISS to ActivityState.MISSED
        ),
        ActivityState.ACTIVE to mapOf(
            ActivityEvent.COMPLETE to ActivityState.COMPLETED,
            ActivityEvent.SKIP to ActivityState.SKIPPED,
            ActivityEvent.DEFER to ActivityState.LATER,
            ActivityEvent.CANCEL to ActivityState.CANCELLED,
            ActivityEvent.MISS to ActivityState.MISSED
        ),
        ActivityState.LATER to mapOf(
            ActivityEvent.START to ActivityState.ACTIVE,
            ActivityEvent.COMPLETE to ActivityState.COMPLETED,
            ActivityEvent.SKIP to ActivityState.SKIPPED,
            ActivityEvent.DEFER to ActivityState.LATER,
            ActivityEvent.CANCEL to ActivityState.CANCELLED,
            ActivityEvent.MISS to ActivityState.MISSED
        ),
        // A missed activity is recoverable: that is the whole point of keeping
        // it distinct from a skip.
        ActivityState.MISSED to mapOf(
            ActivityEvent.START to ActivityState.ACTIVE,
            ActivityEvent.COMPLETE to ActivityState.COMPLETED,
            ActivityEvent.SKIP to ActivityState.SKIPPED,
            ActivityEvent.DEFER to ActivityState.LATER,
            ActivityEvent.CANCEL to ActivityState.CANCELLED,
            ActivityEvent.REOPEN to ActivityState.UPCOMING
        ),
        ActivityState.COMPLETED to mapOf(
            ActivityEvent.REOPEN to ActivityState.UPCOMING
        ),
        ActivityState.SKIPPED to mapOf(
            ActivityEvent.REOPEN to ActivityState.UPCOMING
        ),
        ActivityState.CANCELLED to mapOf(
            ActivityEvent.REOPEN to ActivityState.UPCOMING
        )
    )

    fun canApply(from: ActivityState, event: ActivityEvent): Boolean =
        transitions[from]?.containsKey(event) == true

    fun tryApply(from: ActivityState, event: ActivityEvent): ActivityState? =
        transitions[from]?.get(event)

    fun apply(from: ActivityState, event: ActivityEvent): ActivityState =
        tryApply(from, event) ?: throw IllegalActivityTransition(from, event)

    /** The events a user interface should offer for [state]. */
    fun availableEvents(state: ActivityState): Set<ActivityEvent> =
        (transitions[state]?.keys.orEmpty() - ActivityEvent.MISS)
}
