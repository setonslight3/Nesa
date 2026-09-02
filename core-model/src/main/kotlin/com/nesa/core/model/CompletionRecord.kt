package com.nesa.core.model

import java.time.Instant
import java.time.LocalDate

/** How an activity ended. */
enum class CompletionResult { COMPLETED, SKIPPED, MISSED, CANCELLED }

/**
 * The immutable history of what actually happened, kept separately from the
 * plan so that rescheduling never rewrites the past.
 */
data class CompletionRecord(
    val id: String,
    val activityId: String,
    val blockId: String,
    val date: LocalDate,
    val result: CompletionResult,
    val recordedAt: Instant,
    /** Optional user-supplied reason, mainly for a deliberate skip. */
    val note: String? = null
)
