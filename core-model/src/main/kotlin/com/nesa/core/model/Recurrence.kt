package com.nesa.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RecurrenceFrequency {
    /** Happens once. The activity has exactly the one block it was created with. */
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    companion object {
        val Default: RecurrenceFrequency = NONE
    }
}

/**
 * How often an activity comes back.
 *
 * Deliberately a flat data class rather than a sealed hierarchy of rule types.
 * Room stores primitives only — no type converters, that rule is in CLAUDE.md —
 * and a sealed hierarchy would need a codec and a schema migration every time a
 * new shape of rule appeared. Five nullable-free columns cover daily, weekly and
 * monthly, and the fields that do not apply to a frequency are simply unused.
 *
 * This is why `Activity` (the what) and `ScheduleBlock` (the when) were split in
 * Stage 1: one activity carrying one of these produces a block on every day it
 * matches, and the scheduler never has to know recurrence exists.
 *
 * @param interval every *n*th day, week or month. 1 is every one.
 * @param daysOfWeek which days a [WEEKLY] rule lands on. Ignored otherwise.
 * @param startDate anchors an interval greater than 1, and supplies the day of
 *   the month for [MONTHLY]. Null means unanchored, which only a plain
 *   every-day or every-week rule can be.
 * @param endDate the last day it may occur, inclusive. Null means indefinitely.
 */
data class Recurrence(
    val frequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
) {
    init {
        require(interval in 1..MAX_INTERVAL) { "Recurrence interval must be between 1 and $MAX_INTERVAL" }
        require(frequency != RecurrenceFrequency.WEEKLY || daysOfWeek.isNotEmpty()) {
            "A weekly recurrence must name at least one day"
        }
        // An interval of more than one, or a monthly rule, has nothing to count
        // from without an anchor — and a rule that cannot say whether it matches
        // a date is worse than no rule at all.
        require(!needsAnchor() || startDate != null) {
            "This recurrence needs a start date to count from"
        }
        require(endDate == null || startDate == null || !endDate.isBefore(startDate)) {
            "A recurrence cannot end before it starts"
        }
    }

    val repeats: Boolean get() = frequency != RecurrenceFrequency.NONE

    private fun needsAnchor(): Boolean =
        frequency == RecurrenceFrequency.MONTHLY ||
            (repeats && interval > 1)

    /**
     * Whether this rule puts the activity on [date].
     *
     * Pure and total: every frequency answers for every date, so a caller never
     * has to special-case one. A [RecurrenceFrequency.NONE] activity always
     * answers false — its single block already exists and nothing should
     * generate another.
     */
    fun occursOn(date: LocalDate): Boolean {
        if (!repeats) return false
        if (startDate != null && date.isBefore(startDate)) return false
        if (endDate != null && date.isAfter(endDate)) return false

        return when (frequency) {
            RecurrenceFrequency.NONE -> false
            RecurrenceFrequency.DAILY -> matchesInterval(ChronoUnit.DAYS, date)
            RecurrenceFrequency.WEEKLY ->
                date.dayOfWeek in daysOfWeek && matchesWeekInterval(date)
            RecurrenceFrequency.MONTHLY -> matchesMonthly(date)
        }
    }

    private fun matchesInterval(unit: ChronoUnit, date: LocalDate): Boolean {
        val anchor = startDate ?: return interval == 1
        return unit.between(anchor, date) % interval == 0L
    }

    /**
     * Counted from the start of the anchor's week, not from the anchor itself.
     *
     * "Every other Monday and Thursday" has to mean both days in the same week
     * and then a week off. Counting raw days from the anchor would put Monday
     * and Thursday in different intervals and drop one of them.
     */
    private fun matchesWeekInterval(date: LocalDate): Boolean {
        val anchor = startDate ?: return interval == 1
        val anchorWeek = anchor.with(DayOfWeek.MONDAY)
        val dateWeek = date.with(DayOfWeek.MONDAY)
        return ChronoUnit.WEEKS.between(anchorWeek, dateWeek) % interval == 0L
    }

    /**
     * The same day of the month, clamped to the month's last day.
     *
     * A rule anchored on the 31st has to happen in February. Skipping the short
     * months instead would silently drop a third of a monthly commitment, which
     * is exactly the kind of quiet loss this product does not do.
     */
    private fun matchesMonthly(date: LocalDate): Boolean {
        val anchor = startDate ?: return false
        if (ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1)) % interval != 0L) {
            return false
        }
        val wanted = minOf(anchor.dayOfMonth, date.lengthOfMonth())
        return date.dayOfMonth == wanted
    }

    companion object {
        /** Happens once, on the day it was created for. */
        val Once: Recurrence = Recurrence()

        val EveryDay: Recurrence = Recurrence(frequency = RecurrenceFrequency.DAILY)

        val Weekdays: Recurrence = Recurrence(
            frequency = RecurrenceFrequency.WEEKLY,
            daysOfWeek = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        )

        /** Far beyond anything a person means, and short of arithmetic trouble. */
        const val MAX_INTERVAL: Int = 52
    }
}
