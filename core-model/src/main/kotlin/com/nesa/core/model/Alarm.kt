package com.nesa.core.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * How long NESA waits, and how many times it comes back, when the alarm is not
 * answered or the user asks for more sleep.
 *
 * [autoRetryMinutes] covers silence; [snoozeMinutes] covers an explicit request.
 * The two are separate because "no response" and "let me sleep" are different
 * user intents.
 */
data class SnoozePolicy(
    val snoozeMinutes: Int = 9,
    val maxSnoozes: Int = 3,
    val autoRetryMinutes: Int = 5,
    val maxAutoRetries: Int = 3,
    /** Allows a deliberate "I am sleeping in" that stops the alarm for today. */
    val allowReturnToSleep: Boolean = true
) {
    init {
        require(snoozeMinutes in 1..60) { "Snooze must be between 1 and 60 minutes" }
        require(maxSnoozes >= 0) { "maxSnoozes must not be negative" }
        require(autoRetryMinutes in 1..60) { "Auto retry must be between 1 and 60 minutes" }
        require(maxAutoRetries >= 0) { "maxAutoRetries must not be negative" }
    }

    companion object {
        val Default: SnoozePolicy = SnoozePolicy()
    }
}

/**
 * The kinds of wake challenge NESA can present.
 *
 * All of them confirm that the user is awake and engaged. None of them tests
 * intelligence, and arithmetic is deliberately absent: a challenge that is hard
 * when you are half asleep is a challenge people learn to defeat by giving up.
 */
enum class WakeChallengeType {
    /** Tap highlighted targets in the order they are shown. */
    TAP_SEQUENCE,

    /** Watch a short pattern, then repeat it. */
    PATTERN_RECALL,

    /** Remember a few symbols, then pick them out of a larger set. */
    MEMORY_MATCH,

    /** Tap as soon as the target turns green, a few times in a row. */
    REACTION;

    companion object {
        val Default: WakeChallengeType = TAP_SEQUENCE
    }
}

enum class ChallengeDifficulty {
    EASY, MEDIUM, HARD;

    fun harder(): ChallengeDifficulty = when (this) {
        EASY -> MEDIUM
        MEDIUM -> HARD
        HARD -> HARD
    }

    fun easier(): ChallengeDifficulty = when (this) {
        HARD -> MEDIUM
        MEDIUM -> EASY
        EASY -> EASY
    }

    companion object {
        val Default: ChallengeDifficulty = EASY
    }
}

/**
 * Configuration for the challenge that must be passed before an alarm stops.
 *
 * @param adaptive when true, NESA raises or lowers [difficulty] based on recent
 *   results rather than leaving the user permanently over- or under-challenged.
 */
data class WakeChallengePolicy(
    val type: WakeChallengeType = WakeChallengeType.Default,
    val difficulty: ChallengeDifficulty = ChallengeDifficulty.Default,
    val adaptive: Boolean = true,
    /** Challenge is required to dismiss; when false a single confirm tap is enough. */
    val required: Boolean = true
) {
    companion object {
        val Default: WakeChallengePolicy = WakeChallengePolicy()
    }
}

/**
 * A smart alarm.
 *
 * @param days the weekdays it repeats on. An empty set means it fires once, at
 *   the next occurrence of [time].
 */
data class Alarm(
    val id: String,
    val label: String = "Wake up",
    val time: LocalTime,
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val challenge: WakeChallengePolicy = WakeChallengePolicy.Default,
    val snooze: SnoozePolicy = SnoozePolicy.Default,
    val vibrate: Boolean = true,
    /** System URI of the alarm sound, or null for the device default. */
    val soundUri: String? = null,
    /** Seconds over which the volume ramps up, so waking is not a jolt. */
    val fadeInSeconds: Int = 20
) {
    val repeats: Boolean get() = days.isNotEmpty()
}

/** The live state of one alarm firing. Persisted so it survives process death. */
data class AlarmSession(
    val alarmId: String,
    val snoozeCount: Int = 0,
    val autoRetryCount: Int = 0,
    val firstFiredAtEpochMillis: Long = 0L
)
