package com.nesa.core.scheduling

import com.nesa.core.model.Alarm
import java.time.ZonedDateTime

/**
 * Works out when an alarm should next ring.
 *
 * All arithmetic goes through [ZonedDateTime] so that daylight-saving
 * transitions are resolved by the platform's own rules rather than by adding
 * milliseconds and hoping. Times that do not exist on a spring-forward day are
 * shifted by java.time to the next valid instant, which is the behaviour a user
 * expects: the alarm still goes off that morning.
 */
object NextAlarmCalculator {

    private const val SEARCH_DAYS = 8

    /**
     * The next firing at or after [from], or null when the alarm is disabled or
     * a one-shot alarm has no future occurrence within the search horizon.
     */
    fun next(alarm: Alarm, from: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        for (dayOffset in 0 until SEARCH_DAYS) {
            val date = from.toLocalDate().plusDays(dayOffset.toLong())
            if (alarm.repeats && date.dayOfWeek !in alarm.days) continue
            val candidate = ZonedDateTime.of(date, alarm.time, from.zone)
            if (candidate.isAfter(from)) return candidate
        }
        return null
    }

    /** When a snooze taken at [from] should bring the alarm back. */
    fun afterSnooze(alarm: Alarm, from: ZonedDateTime): ZonedDateTime =
        from.plusMinutes(alarm.snooze.snoozeMinutes.toLong())

    /** When an unanswered alarm should retry, or null once retries are exhausted. */
    fun afterSilence(alarm: Alarm, from: ZonedDateTime, retriesSoFar: Int): ZonedDateTime? {
        if (retriesSoFar >= alarm.snooze.maxAutoRetries) return null
        return from.plusMinutes(alarm.snooze.autoRetryMinutes.toLong())
    }

    /** True when the user has snoozed as often as their policy allows. */
    fun snoozeExhausted(alarm: Alarm, snoozesSoFar: Int): Boolean =
        snoozesSoFar >= alarm.snooze.maxSnoozes
}
