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
    val note: String? = null,
    /**
     * Minutes past midnight of the slot this record is about.
     *
     * Recorded separately from [recordedAt] because they answer different
     * questions. `recordedAt` is when NESA found out; this is when the activity
     * was *meant* to happen — and "things I schedule for 21:00 never get done"
     * is a fact about the slot, not about the moment the app noticed.
     *
     * Nullable because records written before this existed have no honest
     * answer, and inventing one would poison the very history the adaptive
     * layer reads. [com.nesa.core.model.NesaModule] aside, this is the only
     * field in the product that exists purely to be learned from.
     */
    val scheduledStartMinute: Int? = null
)
