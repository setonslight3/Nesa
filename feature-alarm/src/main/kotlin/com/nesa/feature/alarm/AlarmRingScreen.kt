package com.nesa.feature.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesa.core.model.Alarm
import com.nesa.core.ui.format.formatted
import com.nesa.core.ui.theme.NesaSpacing
import java.time.LocalTime

/**
 * The alarm, ringing.
 *
 * It shows one thing at a time and gives three honest choices: wake up, take a
 * few more minutes, or decide to sleep in. There is no way to make the alarm go
 * away without picking one — and no way to be trapped by one either.
 */
@Composable
fun AlarmRingScreen(
    onAlarmLoaded: (Alarm) -> Unit,
    onOutcome: (RingOutcome) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmRingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reported as soon as the alarm is known so the screen can start the sound
    // when the ringer service could not.
    LaunchedEffect(state.alarm) {
        state.alarm?.let(onAlarmLoaded)
    }

    LaunchedEffect(state.outcome) {
        if (state.outcome != RingOutcome.NONE) {
            onOutcome(state.outcome)
            viewModel.onOutcomeHandled()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(NesaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NesaSpacing.lg, Alignment.CenterVertically)
        ) {
            val alarm = state.alarm

            Text(
                text = remember { LocalTime.now() }.formatted(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = alarm?.label.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            val challenge = state.challenge
            if (challenge != null) {
                WakeChallengeHost(
                    challenge = challenge,
                    onSolved = viewModel::onChallengeSolved
                )
            } else if (!state.loading) {
                Button(
                    onClick = viewModel::onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.alarm_ring_dismiss))
                }
            }

            if (alarm != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NesaSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = viewModel::onSnooze,
                        enabled = state.canSnooze,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                R.string.alarm_ring_snooze,
                                alarm.snooze.snoozeMinutes
                            )
                        )
                    }
                    Text(
                        text = if (state.canSnooze) {
                            stringResource(R.string.alarm_ring_snoozes_left, state.snoozesLeft)
                        } else {
                            stringResource(R.string.alarm_ring_no_snoozes)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (alarm.snooze.allowReturnToSleep) {
                        TextButton(onClick = viewModel::onSleepIn) {
                            Text(stringResource(R.string.alarm_ring_sleep_in))
                        }
                    }
                }
            }
        }
    }
}
