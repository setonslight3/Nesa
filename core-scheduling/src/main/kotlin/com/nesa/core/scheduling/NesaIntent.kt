package com.nesa.core.scheduling

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything an assistant is allowed to ask NESA to do.
 *
 * This is the safety boundary for Stage 4, and it is a closed set on purpose.
 * The build instructions carry two non-negotiables that meet here:
 *
 * > Do not give AI direct database or arbitrary code execution access.
 *
 * A model never touches Room, never calls a repository, and never runs code. It
 * emits one of these, a validator checks it, and the existing use-case layer
 * carries it out — the same layer a notification tap and a timeline tap already
 * go through. Anything that cannot be expressed as one of these cannot be done
 * at all, and that is the point: the set of dangerous actions is empty because
 * the set of possible actions is enumerated.
 *
 * A sealed interface rather than a string command, so adding a capability is a
 * compile error everywhere that handles intents rather than a silent gap.
 */
sealed interface NesaIntent {

    /** Add something to the plan. The scheduler still decides where it lands. */
    data class AddActivity(
        val title: String,
        val duration: Duration,
        val preferredStart: LocalTime? = null,
        val date: LocalDate? = null
    ) : NesaIntent

    /**
     * Apply a decision to an existing block — done, skip, later, cancel.
     *
     * Carries an [ActivityEvent] rather than a free-form verb, so an intent can
     * only ever express a transition the state machine already knows about.
     */
    data class ApplyEvent(
        val blockId: String,
        val event: ActivityEvent,
        val note: String? = null
    ) : NesaIntent

    /** Move a block to a different time today. */
    data class RescheduleActivity(
        val blockId: String,
        val start: LocalTime
    ) : NesaIntent

    /** Change the wake alarm's time. */
    data class SetAlarmTime(val time: LocalTime) : NesaIntent

    /** Turn the wake alarm on or off. */
    data class SetAlarmEnabled(val enabled: Boolean) : NesaIntent

    /** Re-run planning for a day. Safe, idempotent, changes no user data. */
    data class ReplanDay(val date: LocalDate? = null) : NesaIntent

    /**
     * A question, answered from local state. Reads nothing the user cannot
     * already see and writes nothing at all.
     */
    data class Query(val subject: QuerySubject) : NesaIntent
}

enum class QuerySubject {
    NEXT_ACTIVITY,
    TODAYS_PLAN,
    ALARM_TIME,
    HOW_THE_WEEK_IS_GOING
}

/**
 * Why an intent was refused.
 *
 * Refusals are values rather than exceptions because refusing is the expected
 * case, not the exceptional one: an assistant that misheard should produce a
 * sentence the user can read, not a crash and not a silent no-op.
 */
sealed interface IntentRefusal {

    /** Nothing in the input mapped to a capability NESA has. */
    data object NotUnderstood : IntentRefusal

    /** Understood, but the values do not make sense. */
    data class Invalid(val reason: String) : IntentRefusal

    /**
     * Understood and valid, but it would destroy something.
     *
     * Deletions and cancellations are never carried out on an assistant's say-so
     * alone. "Nothing important is ever silently deleted" is a product rule, and
     * a misheard word is exactly the kind of thing that would delete a week.
     */
    data class NeedsConfirmation(val intent: NesaIntent, val summary: String) : IntentRefusal
}

/** The result of putting a proposed intent through the validator. */
sealed interface IntentOutcome {
    data class Allowed(val intent: NesaIntent) : IntentOutcome
    data class Refused(val refusal: IntentRefusal) : IntentOutcome
}
