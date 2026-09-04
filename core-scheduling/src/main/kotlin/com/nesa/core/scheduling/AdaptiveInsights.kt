package com.nesa.core.scheduling

import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import com.nesa.core.model.DayWindow
import java.time.LocalTime

/**
 * How reliable one part of the day has proved to be.
 *
 * @param attempts how many records fell in this band. Reported so a caller can
 *   tell "you never finish anything in the evening" from "you have tried twice".
 */
data class TimeBandInsight(
    val band: DayBand,
    val attempts: Int,
    val completed: Int,
    val missed: Int,
    val skipped: Int
) {
    /** Share of attempts that were actually finished, 0 when nothing was tried. */
    val completionRate: Float
        get() = if (attempts == 0) 0f else completed.toFloat() / attempts

    /**
     * Enough history to say anything at all.
     *
     * Below this, the difference between a band and its neighbour is noise, and
     * a product that told a user "you are bad at mornings" on the strength of
     * two data points would deserve to be uninstalled.
     */
    val isTrustworthy: Boolean get() = attempts >= AdaptiveInsights.MINIMUM_ATTEMPTS
}

/** The coarse parts of a day NESA reasons about, ordered through the day. */
enum class DayBand { MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * What NESA has learned about when this person actually gets things done.
 *
 * This is the "adaptive" in adaptive personal assistant, and it is deliberately
 * the least clever thing that could work: it counts outcomes per part of the
 * day and reports the counts. No model, no weights, no decay curve.
 *
 * Three reasons for keeping it that plain, and they are product reasons rather
 * than engineering laziness:
 *
 * - **The user has to be able to predict it.** A schedule that moves for
 *   reasons nobody can explain is a schedule people stop trusting, and a
 *   planner people do not trust is one they stop opening.
 * - **It must be able to say "I do not know".** [TimeBandInsight.isTrustworthy]
 *   is the whole point. Advice from four data points is worse than silence.
 * - **It never acts on its own.** This produces *suggestions*. Placement stays
 *   with [AdaptiveScheduler], which remains a pure function of the day it is
 *   given. Nothing here reaches into the scheduler, and nothing here changes a
 *   plan the user did not ask it to change.
 *
 * Pure, like the rest of this package: it takes the history and the day window
 * as arguments, so every figure it produces is reproducible in a test.
 */
object AdaptiveInsights {

    /** Below this many records in a band, NESA says nothing about that band. */
    const val MINIMUM_ATTEMPTS = 5

    /**
     * A completion rate this far below a person's own average marks a band as
     * one to avoid. Relative rather than absolute on purpose: someone who
     * finishes 40% of what they plan is not failing, they are over-planning, and
     * the useful advice is still "your mornings beat your evenings".
     */
    const val WEAK_BAND_MARGIN = 0.15f

    /**
     * Counts outcomes per band.
     *
     * @param records the user's completion history. Records with no
     *   [CompletionRecord.scheduledStartMinute] are skipped rather than guessed
     *   at — they predate the field, and inventing a slot for them would teach
     *   this something that was never true.
     */
    fun byTimeBand(records: List<CompletionRecord>, window: DayWindow): List<TimeBandInsight> {
        val grouped = records
            .mapNotNull { record ->
                record.scheduledStartMinute?.let { minute -> bandOf(minute, window) to record }
            }
            .groupBy({ it.first }, { it.second })

        return DayBand.entries.map { band ->
            val inBand = grouped[band].orEmpty()
            TimeBandInsight(
                band = band,
                attempts = inBand.size,
                completed = inBand.count { it.result == CompletionResult.COMPLETED },
                missed = inBand.count { it.result == CompletionResult.MISSED },
                // Counted apart from missed, always. A skip is a decision and a
                // miss is the absence of one, and a learner that conflated them
                // would punish people for choosing.
                skipped = inBand.count { it.result == CompletionResult.SKIPPED }
            )
        }
    }

    /**
     * The band this person finishes most reliably, or null when nothing is
     * yet worth saying.
     */
    fun strongestBand(records: List<CompletionRecord>, window: DayWindow): TimeBandInsight? =
        byTimeBand(records, window)
            .filter { it.isTrustworthy }
            .maxByOrNull { it.completionRate }

    /**
     * Bands doing measurably worse than this person's own average.
     *
     * Used to warn before something is scheduled into a slot history says will
     * not survive — a warning the user can ignore, never a move NESA makes.
     */
    fun weakBands(records: List<CompletionRecord>, window: DayWindow): List<TimeBandInsight> {
        val trustworthy = byTimeBand(records, window).filter { it.isTrustworthy }
        if (trustworthy.size < 2) return emptyList()

        // Weighted by attempts rather than a mean of rates, so one lightly used
        // band cannot drag the baseline around.
        val totalAttempts = trustworthy.sumOf { it.attempts }
        val totalCompleted = trustworthy.sumOf { it.completed }
        val average = totalCompleted.toFloat() / totalAttempts

        return trustworthy.filter { it.completionRate < average - WEAK_BAND_MARGIN }
    }

    /**
     * Which band a minute past midnight falls in.
     *
     * The boundaries are the user's own [DayWindow], not clock constants: a
     * person whose morning ends at 14:00 has a long morning, and NESA's advice
     * has to be about their day rather than a generic one.
     */
    fun bandOf(minuteOfDay: Int, window: DayWindow): DayBand = when {
        minuteOfDay < DayWindow.minuteOf(window.morningEnds) -> DayBand.MORNING
        minuteOfDay < DayWindow.minuteOf(window.eveningStarts) -> DayBand.AFTERNOON
        minuteOfDay < DayWindow.minuteOf(window.nightStarts) -> DayBand.EVENING
        else -> DayBand.NIGHT
    }

    fun bandOf(time: LocalTime, window: DayWindow): DayBand =
        bandOf(DayWindow.minuteOf(time), window)
}
