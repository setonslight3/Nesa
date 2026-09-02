package com.nesa.feature.alarm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nesa.core.model.WakeChallenge
import com.nesa.core.ui.theme.NesaSpacing
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Renders whichever challenge the alarm is configured for.
 *
 * All four confirm engagement rather than test ability: nothing here requires
 * arithmetic, reading speed, or precision that a person half-awake does not
 * have. Targets are large, mistakes simply restart the round, and the challenge
 * can always be finished — it never locks the user out of their own alarm.
 *
 * @param onSolved reports success along with how many mistakes were made and
 *   how long it took, which is what feeds adaptive difficulty.
 */
@Composable
fun WakeChallengeHost(
    challenge: WakeChallenge,
    onSolved: (mistakes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var mistakes by remember(challenge) { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NesaSpacing.lg)
    ) {
        when (challenge) {
            is WakeChallenge.TapSequence -> TapSequenceChallenge(
                challenge = challenge,
                onMistake = { mistakes++ },
                onSolved = { onSolved(mistakes) }
            )
            is WakeChallenge.PatternRecall -> PatternRecallChallenge(
                challenge = challenge,
                onMistake = { mistakes++ },
                onSolved = { onSolved(mistakes) }
            )
            is WakeChallenge.MemoryMatch -> MemoryMatchChallenge(
                challenge = challenge,
                onMistake = { mistakes++ },
                onSolved = { onSolved(mistakes) }
            )
            is WakeChallenge.Reaction -> ReactionChallenge(
                challenge = challenge,
                onMistake = { mistakes++ },
                onSolved = { onSolved(mistakes) }
            )
        }
    }
}

/** Distinct, quickly-readable labels. Letters beat icons at 6am. */
private val SYMBOLS = listOf("A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M")

private fun symbolFor(index: Int) = SYMBOLS[index % SYMBOLS.size]

@Composable
private fun ChallengePrompt(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TapSequenceChallenge(
    challenge: WakeChallenge.TapSequence,
    onMistake: () -> Unit,
    onSolved: () -> Unit
) {
    var progress by remember(challenge) { mutableIntStateOf(0) }

    ChallengePrompt(stringResource(R.string.alarm_challenge_tap))

    // The order is shown on the targets themselves, so this asks for attention
    // rather than memory — which is the point of a wake challenge.
    Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
        for (row in 0 until challenge.gridSize) {
            Row(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                for (column in 0 until challenge.gridSize) {
                    val cell = row * challenge.gridSize + column
                    val order = challenge.targets.indexOf(cell)
                    ChallengeTarget(
                        label = if (order >= 0) (order + 1).toString() else "",
                        active = order >= 0 && order >= progress,
                        done = order in 0 until progress,
                        onClick = {
                            when {
                                order == progress -> {
                                    progress++
                                    if (progress == challenge.targets.size) onSolved()
                                }
                                order >= 0 -> {
                                    onMistake()
                                    progress = 0
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternRecallChallenge(
    challenge: WakeChallenge.PatternRecall,
    onMistake: () -> Unit,
    onSolved: () -> Unit
) {
    var showing by remember(challenge) { mutableStateOf(true) }
    var highlighted by remember(challenge) { mutableStateOf(-1) }
    var progress by remember(challenge) { mutableIntStateOf(0) }
    var attempt by remember(challenge) { mutableIntStateOf(0) }

    LaunchedEffect(challenge, attempt) {
        showing = true
        delay(500)
        challenge.pattern.forEach { pad ->
            highlighted = pad
            delay(500)
            highlighted = -1
            delay(200)
        }
        showing = false
    }

    ChallengePrompt(
        stringResource(
            if (showing) R.string.alarm_challenge_pattern_watch
            else R.string.alarm_challenge_pattern
        )
    )

    Row(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
        for (pad in 0 until challenge.optionCount) {
            ChallengeTarget(
                label = symbolFor(pad),
                active = highlighted == pad,
                done = false,
                enabled = !showing,
                onClick = {
                    if (challenge.pattern[progress] == pad) {
                        progress++
                        if (progress == challenge.pattern.size) onSolved()
                    } else {
                        onMistake()
                        progress = 0
                        attempt++
                    }
                }
            )
        }
    }
}

@Composable
private fun MemoryMatchChallenge(
    challenge: WakeChallenge.MemoryMatch,
    onMistake: () -> Unit,
    onSolved: () -> Unit
) {
    var revealing by remember(challenge) { mutableStateOf(true) }
    var found by remember(challenge) { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(challenge) {
        revealing = true
        delay(challenge.revealMillis)
        revealing = false
    }

    ChallengePrompt(
        stringResource(
            if (revealing) R.string.alarm_challenge_memory
            else R.string.alarm_challenge_memory_pick
        )
    )

    val visible = if (revealing) challenge.symbols else challenge.choices
    Column(verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
        visible.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(NesaSpacing.sm)) {
                row.forEach { symbol ->
                    ChallengeTarget(
                        label = symbolFor(symbol),
                        active = revealing,
                        done = symbol in found,
                        enabled = !revealing,
                        onClick = {
                            if (symbol in challenge.symbols) {
                                found = found + symbol
                                if (found.size == challenge.symbols.size) onSolved()
                            } else {
                                onMistake()
                                found = emptySet()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionChallenge(
    challenge: WakeChallenge.Reaction,
    onMistake: () -> Unit,
    onSolved: () -> Unit
) {
    var round by remember(challenge) { mutableIntStateOf(0) }
    var armed by remember(challenge) { mutableStateOf(false) }
    var attempt by remember(challenge) { mutableIntStateOf(0) }

    LaunchedEffect(challenge, round, attempt) {
        armed = false
        val range = challenge.maxDelayMillis - challenge.minDelayMillis
        delay(challenge.minDelayMillis + Random.nextLong(range.coerceAtLeast(1L)))
        armed = true
    }

    ChallengePrompt(stringResource(R.string.alarm_challenge_reaction))
    Text(
        text = stringResource(R.string.alarm_challenge_round, round + 1, challenge.rounds),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val targetColor = if (armed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val alpha by animateFloatAsState(if (armed) 1f else 0.5f, label = "reaction-target")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(NesaSpacing.xl))
            .background(targetColor.copy(alpha = alpha))
            .semantics {
                contentDescription = if (armed) "Tap now" else "Wait"
            }
            .clickable {
                if (armed) {
                    round++
                    if (round == challenge.rounds) onSolved()
                } else {
                    // Tapping early is a mistake, not a failure: the round
                    // simply restarts.
                    onMistake()
                    attempt++
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(
                if (armed) R.string.alarm_challenge_reaction_now
                else R.string.alarm_challenge_reaction_wait
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = if (armed) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** A large, high-contrast tap target. 72dp is well above the 48dp minimum. */
@Composable
private fun ChallengeTarget(
    label: String,
    active: Boolean,
    done: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val background: Color = when {
        done -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground: Color = when {
        done -> MaterialTheme.colorScheme.onPrimary
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(NesaSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = foreground
        )
    }
}

private const val COLUMNS = 3
