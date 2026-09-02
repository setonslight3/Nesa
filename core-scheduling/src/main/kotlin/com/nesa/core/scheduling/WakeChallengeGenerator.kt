package com.nesa.core.scheduling

import com.nesa.core.model.ChallengeDifficulty
import com.nesa.core.model.WakeChallenge
import com.nesa.core.model.WakeChallengePolicy
import com.nesa.core.model.WakeChallengeResult
import com.nesa.core.model.WakeChallengeType
import kotlin.random.Random

/**
 * Builds a concrete wake challenge from a policy.
 *
 * Every challenge here confirms engagement rather than testing ability, and
 * none of them involves arithmetic. The generator is seedable so its output can
 * be asserted in tests.
 */
object WakeChallengeGenerator {

    fun generate(policy: WakeChallengePolicy, random: Random = Random.Default): WakeChallenge =
        when (policy.type) {
            WakeChallengeType.TAP_SEQUENCE -> tapSequence(policy.difficulty, random)
            WakeChallengeType.PATTERN_RECALL -> patternRecall(policy.difficulty, random)
            WakeChallengeType.MEMORY_MATCH -> memoryMatch(policy.difficulty, random)
            WakeChallengeType.REACTION -> reaction(policy.difficulty, random)
        }

    private fun tapSequence(difficulty: ChallengeDifficulty, random: Random): WakeChallenge.TapSequence {
        val (grid, steps) = when (difficulty) {
            ChallengeDifficulty.EASY -> 3 to 3
            ChallengeDifficulty.MEDIUM -> 4 to 4
            ChallengeDifficulty.HARD -> 4 to 6
        }
        val cells = (0 until grid * grid).shuffled(random)
        return WakeChallenge.TapSequence(
            difficulty = difficulty,
            gridSize = grid,
            targets = cells.take(steps)
        )
    }

    private fun patternRecall(difficulty: ChallengeDifficulty, random: Random): WakeChallenge.PatternRecall {
        val (pads, steps) = when (difficulty) {
            ChallengeDifficulty.EASY -> 4 to 3
            ChallengeDifficulty.MEDIUM -> 4 to 5
            ChallengeDifficulty.HARD -> 6 to 6
        }
        return WakeChallenge.PatternRecall(
            difficulty = difficulty,
            optionCount = pads,
            pattern = List(steps) { random.nextInt(pads) }
        )
    }

    private fun memoryMatch(difficulty: ChallengeDifficulty, random: Random): WakeChallenge.MemoryMatch {
        val (count, choiceCount, reveal) = when (difficulty) {
            ChallengeDifficulty.EASY -> Triple(3, 6, 4_000L)
            ChallengeDifficulty.MEDIUM -> Triple(4, 9, 3_000L)
            ChallengeDifficulty.HARD -> Triple(5, 12, 2_500L)
        }
        val choices = (0 until choiceCount).shuffled(random)
        return WakeChallenge.MemoryMatch(
            difficulty = difficulty,
            symbols = choices.take(count).sorted(),
            choices = choices,
            revealMillis = reveal
        )
    }

    private fun reaction(difficulty: ChallengeDifficulty, random: Random): WakeChallenge.Reaction {
        val rounds = when (difficulty) {
            ChallengeDifficulty.EASY -> 3
            ChallengeDifficulty.MEDIUM -> 4
            ChallengeDifficulty.HARD -> 5
        }
        val timeout = when (difficulty) {
            ChallengeDifficulty.EASY -> 3_000L
            ChallengeDifficulty.MEDIUM -> 2_200L
            ChallengeDifficulty.HARD -> 1_600L
        }
        // The jitter is generated up front so the whole challenge is a value and
        // the UI never has to invent timing of its own.
        val minDelay = 800L + random.nextInt(400)
        return WakeChallenge.Reaction(
            difficulty = difficulty,
            rounds = rounds,
            minDelayMillis = minDelay,
            maxDelayMillis = minDelay + 2_000L,
            timeoutMillis = timeout
        )
    }
}

/**
 * Moves the challenge difficulty up or down based on how recent mornings went.
 *
 * A user who breezes through gets a slightly firmer nudge; a user who struggles
 * is not punished for being tired. Difficulty never changes on a single result.
 */
object ChallengeDifficultyPolicy {

    private const val WINDOW = 5
    private const val PROMOTE_STREAK = 3
    private const val CLEAN_ELAPSED_MILLIS = 20_000L

    fun nextDifficulty(
        current: ChallengeDifficulty,
        history: List<WakeChallengeResult>
    ): ChallengeDifficulty {
        val recent = history.takeLast(WINDOW)
        if (recent.size < PROMOTE_STREAK) return current

        val cleanStreak = recent.reversed()
            .takeWhile { it.succeeded && it.mistakes == 0 && it.elapsedMillis <= CLEAN_ELAPSED_MILLIS }
            .size
        if (cleanStreak >= PROMOTE_STREAK) return current.harder()

        val struggles = recent.takeLast(PROMOTE_STREAK).count { !it.succeeded || it.mistakes >= 2 }
        if (struggles >= 2) return current.easier()

        return current
    }
}
