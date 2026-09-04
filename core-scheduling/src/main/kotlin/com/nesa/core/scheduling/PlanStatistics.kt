package com.nesa.core.scheduling

import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One day's outcomes.
 *
 * `planned` is the sum of the four, not a separate count, so the numbers on the
 * screen always add up. A statistics panel whose parts do not sum to its total
 * is one users stop believing, and rightly.
 */
data class DayStatistics(
    val date: LocalDate,
    val completed: Int,
    val skipped: Int,
    val missed: Int,
    val cancelled: Int
) {
    val planned: Int get() = completed + skipped + missed + cancelled

    /**
     * Completion measured against what was actually left to do.
     *
     * Skips and cancellations are excluded from the denominator on purpose. A
     * user who deliberately clears three things from a bad day should not be
     * shown a worse number for having made a decision — that is the same
     * skipped-is-not-missed rule the whole product runs on, expressed as
     * arithmetic.
     */
    val completionRate: Float
        get() {
            val attempted = completed + missed
            return if (attempted == 0) 0f else completed.toFloat() / attempted
        }

    val hasData: Boolean get() = planned > 0
}

/**
 * A week, and the one number worth putting in front of someone.
 *
 * @param bestDay the day with the most completions, for a week worth ending on.
 *   Null when the week is empty rather than an arbitrary Monday.
 */
data class WeekStatistics(
    val weekStart: LocalDate,
    val days: List<DayStatistics>,
    val currentStreakDays: Int,
    val bestDay: DayStatistics?
) {
    val completed: Int get() = days.sumOf { it.completed }
    val skipped: Int get() = days.sumOf { it.skipped }
    val missed: Int get() = days.sumOf { it.missed }

    val completionRate: Float
        get() {
            val attempted = completed + missed
            return if (attempted == 0) 0f else completed.toFloat() / attempted
        }

    val hasData: Boolean get() = days.any { it.hasData }
}

/**
 * Daily and weekly figures, from the completion history.
 *
 * Stage 2 asks for "daily/weekly basic statistics", and the operative word is
 * basic. The blueprint's own UI rule — "do not fill screens with dashboards
 * unless the data helps a decision" — is the constraint here: these are the few
 * figures that change what a person does tomorrow, and nothing else.
 *
 * Pure, like the rest of this package.
 */
object PlanStatistics {

    fun forDay(records: List<CompletionRecord>, date: LocalDate): DayStatistics {
        val onDay = records.filter { it.date == date }
        return DayStatistics(
            date = date,
            completed = onDay.count { it.result == CompletionResult.COMPLETED },
            skipped = onDay.count { it.result == CompletionResult.SKIPPED },
            missed = onDay.count { it.result == CompletionResult.MISSED },
            cancelled = onDay.count { it.result == CompletionResult.CANCELLED }
        )
    }

    /**
     * @param weekStart any date in the week; the week is taken from its Monday.
     */
    fun forWeek(records: List<CompletionRecord>, weekStart: LocalDate): WeekStatistics {
        val monday = weekStart.with(DayOfWeek.MONDAY)
        val days = (0L..6L).map { offset -> forDay(records, monday.plusDays(offset)) }
        return WeekStatistics(
            weekStart = monday,
            days = days,
            currentStreakDays = streakDays(records, monday.plusDays(6)),
            bestDay = days.filter { it.hasData }.maxByOrNull { it.completed }
        )
    }

    /**
     * Consecutive days ending at [endingOn] on which something was completed.
     *
     * [endingOn] is the day in progress and is treated differently from the days
     * behind it, which is the only subtle part:
     *
     * - **Today counts if something was completed, and is otherwise neutral.**
     *   It is skipped over rather than ending the streak, because the day is not
     *   over. A streak that read zero at breakfast and mended itself by lunch
     *   would be measuring the clock rather than the person.
     * - **Every earlier day must have a completion.** A past day with nothing
     *   logged breaks the streak, and so does a past day where everything was
     *   deliberately skipped — those days are finished and nothing was done. A
     *   streak is a record of doing things, not of opening the app.
     *
     * Those two rules together are why skipping everything *so far today* leaves
     * the streak standing, while having skipped everything *yesterday* ends it.
     */
    fun streakDays(records: List<CompletionRecord>, endingOn: LocalDate): Int {
        val completedDays = records
            .filter { it.result == CompletionResult.COMPLETED }
            .map { it.date }
            .toSet()

        // Today counts if it has already happened, and is skipped over if not,
        // rather than ending the streak before the day is done.
        var cursor = if (endingOn in completedDays) endingOn else endingOn.minusDays(1)
        var streak = 0
        while (cursor in completedDays && streak < MAX_STREAK_DAYS) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Bounds the streak walk. Longer than any honest streak, short of a hang. */
    private const val MAX_STREAK_DAYS = 3650

    /** Days between two dates, never negative. Used for "x days ago" copy. */
    fun daysBetween(from: LocalDate, to: LocalDate): Long =
        ChronoUnit.DAYS.between(from, to).coerceAtLeast(0L)
}
