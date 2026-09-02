package com.nesa.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The definition of something the user wants to do.
 *
 * An [Activity] is the "what". A [ScheduleBlock] is the "when". They are kept
 * apart so that Stage 2 recurrence can produce many blocks from one activity
 * without changing the scheduler.
 */
data class Activity(
    val id: String,
    val title: String,
    val notes: String? = null,
    val module: NesaModule = NesaModule.CORE,
    val duration: Duration,
    val priority: Priority = Priority.NORMAL,
    val flexibility: Flexibility = Flexibility.TIME_FLEXIBLE,
    /** Where the user would like this to sit. The scheduler honours it when it can. */
    val preferredStart: LocalTime? = null,
    /** Hard latest finish, only meaningful for [Flexibility.DEADLINE_BASED]. */
    val deadline: LocalDateTime? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH
) {
    init {
        require(title.isNotBlank()) { "Activity title must not be blank" }
        require(!duration.isNegative && !duration.isZero) { "Activity duration must be positive" }
    }

    /** A fixed anchor the scheduler must plan around rather than move. */
    val isAnchor: Boolean get() = flexibility == Flexibility.FIXED

    val durationMinutes: Int get() = duration.toMinutes().toInt()
}
