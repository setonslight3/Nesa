package com.nesa.core.model

import java.time.Instant

/**
 * A generated challenge instance.
 *
 * Symbols are integers rather than drawables or strings so that the domain
 * stays free of UI concerns and remains testable; rendering is the UI's job.
 */
sealed interface WakeChallenge {
    val type: WakeChallengeType
    val difficulty: ChallengeDifficulty

    /** Tap the given cells of a [gridSize] x [gridSize] grid, in order. */
    data class TapSequence(
        override val difficulty: ChallengeDifficulty,
        val gridSize: Int,
        val targets: List<Int>
    ) : WakeChallenge {
        override val type: WakeChallengeType get() = WakeChallengeType.TAP_SEQUENCE
    }

    /** Watch [pattern] light up across [optionCount] pads, then repeat it. */
    data class PatternRecall(
        override val difficulty: ChallengeDifficulty,
        val optionCount: Int,
        val pattern: List<Int>
    ) : WakeChallenge {
        override val type: WakeChallengeType get() = WakeChallengeType.PATTERN_RECALL
    }

    /** Memorise [symbols], then pick them out of [choices]. */
    data class MemoryMatch(
        override val difficulty: ChallengeDifficulty,
        val symbols: List<Int>,
        val choices: List<Int>,
        val revealMillis: Long
    ) : WakeChallenge {
        override val type: WakeChallengeType get() = WakeChallengeType.MEMORY_MATCH
    }

    /** Tap when the target turns green, [rounds] times. */
    data class Reaction(
        override val difficulty: ChallengeDifficulty,
        val rounds: Int,
        val minDelayMillis: Long,
        val maxDelayMillis: Long,
        val timeoutMillis: Long
    ) : WakeChallenge {
        override val type: WakeChallengeType get() = WakeChallengeType.REACTION
    }
}

/** The outcome of one challenge, kept so difficulty can adapt over time. */
data class WakeChallengeResult(
    val id: String,
    val alarmId: String,
    val type: WakeChallengeType,
    val difficulty: ChallengeDifficulty,
    val succeeded: Boolean,
    /** Wrong attempts before success. */
    val mistakes: Int,
    val elapsedMillis: Long,
    val recordedAt: Instant
)
