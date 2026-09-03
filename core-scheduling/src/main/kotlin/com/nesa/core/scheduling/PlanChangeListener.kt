package com.nesa.core.scheduling

import java.time.LocalDate

/**
 * Told whenever a day's plan has been recomputed and written.
 *
 * The domain cannot arm a reminder — that needs `AlarmManager`, which it must
 * not know about — but it is the only thing that knows when the plan actually
 * changed. So it reports, and the platform layer reacts.
 *
 * Without this, reminders were only ever armed by the half-hourly background
 * worker, which meant an activity added for ten minutes' time had nothing
 * scheduled for it at all.
 */
fun interface PlanChangeListener {

    suspend fun onPlanChanged(date: LocalDate)

    companion object {
        /** For tests and for callers that do not care. */
        val None: PlanChangeListener = PlanChangeListener { }
    }
}
