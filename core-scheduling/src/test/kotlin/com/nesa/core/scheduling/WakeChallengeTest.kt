package com.nesa.core.scheduling

import com.nesa.core.model.ChallengeDifficulty
import com.nesa.core.model.WakeChallenge
import com.nesa.core.model.WakeChallengePolicy
import com.nesa.core.model.WakeChallengeResult
import com.nesa.core.model.WakeChallengeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

class WakeChallengeTest {

    @Test
    fun `the default challenge is short, tappable and involves no arithmetic`() {
        val policy = WakeChallengePolicy.Default
        assertEquals(WakeChallengeType.TAP_SEQUENCE, policy.type)
        assertEquals(ChallengeDifficulty.EASY, policy.difficulty)

        val challenge = WakeChallengeGenerator.generate(policy, Random(1)) as WakeChallenge.TapSequence
        assertEquals(3, challenge.gridSize)
        assertEquals(3, challenge.targets.size)
        assertEquals("targets must not repeat", challenge.targets.size, challenge.targets.toSet().size)
        assertTrue(challenge.targets.all { it in 0 until challenge.gridSize * challenge.gridSize })
    }

    @Test
    fun `generation is reproducible for a given seed`() {
        val policy = WakeChallengePolicy(type = WakeChallengeType.PATTERN_RECALL)
        val first = WakeChallengeGenerator.generate(policy, Random(42))
        val second = WakeChallengeGenerator.generate(policy, Random(42))

        assertEquals(first, second)
    }

    @Test
    fun `every challenge type produces a usable instance at every difficulty`() {
        WakeChallengeType.entries.forEach { type ->
            ChallengeDifficulty.entries.forEach { difficulty ->
                val challenge = WakeChallengeGenerator.generate(
                    WakeChallengePolicy(type = type, difficulty = difficulty),
                    Random(7)
                )
                assertEquals(type, challenge.type)
                assertEquals(difficulty, challenge.difficulty)

                when (challenge) {
                    is WakeChallenge.TapSequence -> assertTrue(challenge.targets.isNotEmpty())
                    is WakeChallenge.PatternRecall -> {
                        assertTrue(challenge.pattern.isNotEmpty())
                        assertTrue(challenge.pattern.all { it in 0 until challenge.optionCount })
                    }
                    is WakeChallenge.MemoryMatch -> {
                        assertTrue(challenge.choices.containsAll(challenge.symbols))
                        assertTrue(challenge.symbols.size < challenge.choices.size)
                        assertTrue(challenge.revealMillis >= 2_000L)
                    }
                    is WakeChallenge.Reaction -> {
                        assertTrue(challenge.rounds in 3..5)
                        assertTrue(challenge.maxDelayMillis > challenge.minDelayMillis)
                    }
                }
            }
        }
    }

    private fun result(succeeded: Boolean, mistakes: Int, elapsed: Long = 8_000L) = WakeChallengeResult(
        id = "r",
        alarmId = "alarm",
        type = WakeChallengeType.TAP_SEQUENCE,
        difficulty = ChallengeDifficulty.EASY,
        succeeded = succeeded,
        mistakes = mistakes,
        elapsedMillis = elapsed,
        recordedAt = Instant.EPOCH
    )

    @Test
    fun `difficulty does not move on a single good morning`() {
        val history = listOf(result(succeeded = true, mistakes = 0))
        assertEquals(
            ChallengeDifficulty.EASY,
            ChallengeDifficultyPolicy.nextDifficulty(ChallengeDifficulty.EASY, history)
        )
    }

    @Test
    fun `a clean streak raises the difficulty`() {
        val history = List(3) { result(succeeded = true, mistakes = 0) }
        assertEquals(
            ChallengeDifficulty.MEDIUM,
            ChallengeDifficultyPolicy.nextDifficulty(ChallengeDifficulty.EASY, history)
        )
    }

    @Test
    fun `struggling lowers the difficulty instead of punishing the user`() {
        val history = listOf(
            result(succeeded = true, mistakes = 0),
            result(succeeded = false, mistakes = 3),
            result(succeeded = true, mistakes = 4)
        )
        assertEquals(
            ChallengeDifficulty.EASY,
            ChallengeDifficultyPolicy.nextDifficulty(ChallengeDifficulty.MEDIUM, history)
        )
    }

    @Test
    fun `difficulty never runs off either end of the scale`() {
        val clean = List(5) { result(succeeded = true, mistakes = 0) }
        assertEquals(
            ChallengeDifficulty.HARD,
            ChallengeDifficultyPolicy.nextDifficulty(ChallengeDifficulty.HARD, clean)
        )

        val rough = List(5) { result(succeeded = false, mistakes = 3) }
        assertEquals(
            ChallengeDifficulty.EASY,
            ChallengeDifficultyPolicy.nextDifficulty(ChallengeDifficulty.EASY, rough)
        )
    }
}
