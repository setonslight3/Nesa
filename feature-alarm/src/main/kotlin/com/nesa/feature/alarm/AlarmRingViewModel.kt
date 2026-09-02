package com.nesa.feature.alarm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.alarm.AlarmReceiver
import com.nesa.core.model.Alarm
import com.nesa.core.model.WakeChallenge
import com.nesa.core.model.WakeChallengeResult
import com.nesa.core.model.repository.AlarmRepository
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.scheduling.ChallengeDifficultyPolicy
import com.nesa.core.scheduling.NextAlarmCalculator
import com.nesa.core.scheduling.WakeChallengeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** What the ringing screen should do once the user has answered. */
enum class RingOutcome { NONE, DISMISS, SNOOZE, SLEEP_IN }

data class AlarmRingUiState(
    val alarm: Alarm? = null,
    val challenge: WakeChallenge? = null,
    val snoozesLeft: Int = 0,
    val outcome: RingOutcome = RingOutcome.NONE,
    val loading: Boolean = true
) {
    val requiresChallenge: Boolean get() = challenge != null
    val canSnooze: Boolean get() = snoozesLeft > 0
}

/**
 * The screen that appears when the alarm rings.
 *
 * The alarm only stops after a deliberate action: solving the challenge,
 * choosing to snooze, or explicitly deciding to sleep in. Simply leaving the
 * screen does nothing, which is what makes the ringer's unanswered timeout — and
 * therefore the retry — the fallback rather than an accident.
 */
@HiltViewModel
class AlarmRingViewModel @Inject constructor(
    private val alarms: AlarmRepository,
    private val history: HistoryRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val alarmId: String = checkNotNull(savedStateHandle[ARG_ALARM_ID]) {
        "AlarmRingViewModel requires an alarm id"
    }

    private val _state = MutableStateFlow(AlarmRingUiState())
    val state: StateFlow<AlarmRingUiState> = _state.asStateFlow()

    private var startedAtMillis: Long = 0L

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val alarm = alarms.find(alarmId)
        if (alarm == null) {
            // The alarm was deleted while it was ringing. Stopping is the only
            // sensible answer; leaving the user trapped is not.
            _state.value = AlarmRingUiState(loading = false, outcome = RingOutcome.DISMISS)
            return
        }

        val difficulty = if (alarm.challenge.adaptive) {
            ChallengeDifficultyPolicy.nextDifficulty(
                alarm.challenge.difficulty,
                history.recentChallengeResults(alarm.id)
            )
        } else {
            alarm.challenge.difficulty
        }

        val policy = alarm.challenge.copy(difficulty = difficulty)
        startedAtMillis = clock.millis()

        _state.value = AlarmRingUiState(
            alarm = alarm,
            challenge = if (policy.required) WakeChallengeGenerator.generate(policy) else null,
            snoozesLeft = alarm.snooze.maxSnoozes,
            loading = false
        )
    }

    fun onChallengeSolved(mistakes: Int) {
        val alarm = _state.value.alarm ?: return
        val challenge = _state.value.challenge

        viewModelScope.launch {
            if (challenge != null) {
                val result = WakeChallengeResult(
                    id = UUID.randomUUID().toString(),
                    alarmId = alarm.id,
                    type = challenge.type,
                    difficulty = challenge.difficulty,
                    succeeded = true,
                    mistakes = mistakes,
                    elapsedMillis = clock.millis() - startedAtMillis,
                    recordedAt = Instant.now(clock)
                )
                history.recordChallengeResult(result)

                // Persist the adapted difficulty so the next morning starts
                // where this one finished.
                if (alarm.challenge.adaptive) {
                    val next = ChallengeDifficultyPolicy.nextDifficulty(
                        challenge.difficulty,
                        history.recentChallengeResults(alarm.id)
                    )
                    if (next != alarm.challenge.difficulty) {
                        alarms.save(alarm.copy(challenge = alarm.challenge.copy(difficulty = next)))
                    }
                }
            }
            _state.update { it.copy(outcome = RingOutcome.DISMISS) }
        }
    }

    /** Dismiss without a challenge, used when the alarm does not require one. */
    fun onDismiss() = _state.update { it.copy(outcome = RingOutcome.DISMISS) }

    fun onSnooze() {
        val alarm = _state.value.alarm ?: return
        val used = alarm.snooze.maxSnoozes - _state.value.snoozesLeft
        if (NextAlarmCalculator.snoozeExhausted(alarm, used)) return

        _state.update { it.copy(snoozesLeft = it.snoozesLeft - 1, outcome = RingOutcome.SNOOZE) }
    }

    /** A deliberate lie-in. Different from silence, and treated as an answer. */
    fun onSleepIn() = _state.update { it.copy(outcome = RingOutcome.SLEEP_IN) }

    fun onOutcomeHandled() = _state.update { it.copy(outcome = RingOutcome.NONE) }

    companion object {
        /**
         * The same extra the alarm layer puts on the intent. ComponentActivity
         * seeds SavedStateHandle from the launching intent's extras, so the key
         * must match exactly rather than being a route argument of its own.
         */
        const val ARG_ALARM_ID: String = AlarmReceiver.EXTRA_ALARM_ID
    }
}
