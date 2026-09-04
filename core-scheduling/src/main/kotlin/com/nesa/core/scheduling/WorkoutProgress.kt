package com.nesa.core.scheduling

import com.nesa.core.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What the fitness module can say about how training is going.
 *
 * @param sessionsThisWeek sessions logged in the week containing the reference
 *   day, counting from Monday.
 * @param streakWeeks consecutive **completed** weeks that met the target,
 *   ending with the week before the current one. The current week is excluded
 *   deliberately: a streak that drops to zero every Monday morning and climbs
 *   back on Tuesday is a number that punishes people for the calendar.
 * @param daysSinceLast null when nothing has ever been logged, which is a
 *   different thing from a long gap and should read differently on screen.
 * @param volumeKgThisWeek load moved, in kilogram-repetitions. Only completed
 *   and partial loaded sets contribute; see [com.nesa.core.model.SetLog].
 * @param restRecommended whether the last few days have been demanding enough
 *   that another hard session is a poor idea.
 */
data class FitnessSummary(
    val sessionsThisWeek: Int,
    val weeklyTarget: Int,
    val streakWeeks: Int,
    val daysSinceLast: Long?,
    val volumeKgThisWeek: Double,
    val restRecommended: Boolean
) {
    val metWeeklyTarget: Boolean get() = sessionsThisWeek >= weeklyTarget

    /** How much of the week's target is done, capped at 1. Safe when the target is 0. */
    val weeklyProgress: Float
        get() = if (weeklyTarget <= 0) 1f else (sessionsThisWeek.toFloat() / weeklyTarget).coerceAtMost(1f)
}

/**
 * Turns logged sessions into the few figures worth showing.
 *
 * A pure object, like [AdaptiveScheduler] and [MissedActivityDetector]: it takes
 * everything it needs as arguments so the whole of it runs on a JVM with a fixed
 * date, and so the numbers on the fitness screen are the same numbers a test can
 * assert.
 *
 * The rules here are deliberately fixed rather than learned. Adapting a training
 * load to the individual is Stage 3's business; this is Stage 2, and a rule the
 * user can predict beats a model they cannot.
 */
object WorkoutProgress {

    /** Sessions a week that count as keeping it up. */
    const val DEFAULT_WEEKLY_TARGET = 3

    /**
     * Consecutive demanding days after which another hard session is discouraged.
     *
     * Two, because a third consecutive hard day is where injury risk and
     * adherence both start to suffer for an ordinary person. Not a personalised
     * figure and not presented as one.
     */
    const val DEMANDING_DAYS_BEFORE_REST = 2

    /** How far back [streakWeeks] will look before giving up. Bounds the loop. */
    private const val MAX_STREAK_WEEKS = 520

    fun summarise(
        sessions: List<WorkoutSession>,
        today: LocalDate,
        weeklyTarget: Int = DEFAULT_WEEKLY_TARGET
    ): FitnessSummary {
        val thisWeek = weekStart(today)
        val countsByWeek = sessions.groupingBy { weekStart(it.date) }.eachCount()
        val lastDate = sessions.maxByOrNull { it.date }?.date

        return FitnessSummary(
            sessionsThisWeek = countsByWeek[thisWeek] ?: 0,
            weeklyTarget = weeklyTarget,
            streakWeeks = streakWeeks(countsByWeek, thisWeek, weeklyTarget),
            daysSinceLast = lastDate?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0L) },
            volumeKgThisWeek = sessions.filter { weekStart(it.date) == thisWeek }.sumOf { it.volumeKg },
            restRecommended = restRecommended(sessions, today)
        )
    }

    /**
     * Whether the recent run of demanding days has earned a rest.
     *
     * Counts backwards from yesterday: today is still in progress, and a hard
     * session logged an hour ago should not make the app tell the user to rest
     * today, which they plainly did not.
     */
    fun restRecommended(sessions: List<WorkoutSession>, today: LocalDate): Boolean {
        val demandingDays = sessions
            .filter { it.effort.isDemanding }
            .map { it.date }
            .toSet()

        return (1..DEMANDING_DAYS_BEFORE_REST).all { back ->
            today.minusDays(back.toLong()) in demandingDays
        }
    }

    /**
     * Completed weeks that met the target, walking backwards from last week.
     *
     * @param countsByWeek sessions per week, keyed by the Monday of each week.
     */
    private fun streakWeeks(
        countsByWeek: Map<LocalDate, Int>,
        thisWeek: LocalDate,
        weeklyTarget: Int
    ): Int {
        // A target of zero would otherwise make every week in history a hit and
        // run the loop to its bound for no useful answer.
        if (weeklyTarget <= 0) return 0

        var streak = 0
        var week = thisWeek.minusWeeks(1)
        while (streak < MAX_STREAK_WEEKS && (countsByWeek[week] ?: 0) >= weeklyTarget) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    /** Monday of the week containing [date]. One definition, used everywhere. */
    private fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)
}
