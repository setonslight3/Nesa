package com.nesa.core.model

import java.time.LocalTime

/** The four phases of the NESA day. */
enum class DayCycle {
    /** Start and activate the day. */
    MORNING,

    /** Execute commitments and priorities. */
    DAY,

    /** Recover and absorb overflow. */
    EVENING,

    /** Close today and prepare tomorrow. */
    NIGHT
}

/**
 * The shape of the user's day: when it opens, when it closes, and where the
 * cycle boundaries sit.
 *
 * Times are wall-clock on a single date. A planning day never crosses midnight:
 * if the sleep target is after midnight the planning day still ends at 23:59, so
 * that a [ScheduleBlock] always belongs unambiguously to one date. The lost
 * sliver after midnight is a deliberate Stage 1 simplification that removes an
 * entire class of date-rollover bugs from the scheduler.
 */
data class DayWindow(
    val wakeTime: LocalTime = LocalTime.of(7, 0),
    val sleepTarget: LocalTime = LocalTime.of(23, 0),
    val morningEnds: LocalTime = LocalTime.of(12, 0),
    val eveningStarts: LocalTime = LocalTime.of(18, 0),
    val nightStarts: LocalTime = LocalTime.of(21, 0)
) {
    companion object {
        const val MINUTES_PER_DAY: Int = 24 * 60

        /** Last plannable minute of a date. */
        const val END_OF_DAY_MINUTE: Int = MINUTES_PER_DAY - 1

        val Default: DayWindow = DayWindow()

        fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute

        fun timeOf(minuteOfDay: Int): LocalTime {
            val clamped = minuteOfDay.coerceIn(0, END_OF_DAY_MINUTE)
            return LocalTime.of(clamped / 60, clamped % 60)
        }
    }

    val wakeMinute: Int get() = minuteOf(wakeTime)

    /**
     * The minute the plannable day closes. When the sleep target is at or before
     * the wake time it belongs to the following calendar day, so today's plan
     * simply runs to the end of the date.
     */
    val sleepMinute: Int
        get() {
            val raw = minuteOf(sleepTarget)
            return if (raw <= wakeMinute) END_OF_DAY_MINUTE else raw
        }

    val eveningStartMinute: Int get() = minuteOf(eveningStarts).coerceIn(wakeMinute, sleepMinute)
    val nightStartMinute: Int get() = minuteOf(nightStarts).coerceIn(eveningStartMinute, sleepMinute)

    /** True when the sleep target falls after midnight and today's plan is truncated. */
    val sleepTargetIsAfterMidnight: Boolean get() = minuteOf(sleepTarget) <= wakeMinute

    val plannableMinutes: Int get() = (sleepMinute - wakeMinute).coerceAtLeast(0)

    fun cycleAtMinute(minuteOfDay: Int): DayCycle = when {
        minuteOfDay < wakeMinute -> DayCycle.NIGHT
        minuteOfDay < minuteOf(morningEnds) -> DayCycle.MORNING
        minuteOfDay < eveningStartMinute -> DayCycle.DAY
        minuteOfDay < nightStartMinute -> DayCycle.EVENING
        else -> DayCycle.NIGHT
    }

    fun cycleAt(time: LocalTime): DayCycle = cycleAtMinute(minuteOf(time))

    /** The evening recovery/overflow window, as a half-open minute range. */
    val recoveryWindow: IntRange get() = eveningStartMinute until nightStartMinute

    fun validated(): DayWindow {
        require(morningEnds.isAfter(wakeTime)) { "Morning must end after wake time" }
        require(eveningStarts.isAfter(morningEnds)) { "Evening must start after the morning ends" }
        require(!nightStarts.isBefore(eveningStarts)) { "Night must start at or after the evening" }
        return this
    }
}
