package com.nesa.feature.alarm

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.alarm.AlarmCapability
import com.nesa.core.alarm.ExactAlarmCapability
import com.nesa.core.alarm.NesaAlarmCoordinator
import com.nesa.core.model.Alarm
import com.nesa.core.model.ChallengeDifficulty
import com.nesa.core.model.WakeChallengeType
import com.nesa.core.model.repository.AlarmRepository
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.scheduling.NextAlarmCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject

data class AlarmSettingsUiState(
    val alarm: Alarm? = null,
    val nextRing: ZonedDateTime? = null,
    val capability: AlarmCapability = AlarmCapability.EXACT,
    val loading: Boolean = true
) {
    val exactAlarmsUnavailable: Boolean get() = capability == AlarmCapability.INEXACT_ONLY
}

/**
 * The wake alarm.
 *
 * Every change is written through [NesaAlarmCoordinator], which persists before
 * it touches the platform's clock. The screen therefore never has to reason
 * about ordering, and an interrupted edit cannot leave a saved alarm unarmed.
 */
@HiltViewModel
class AlarmSettingsViewModel @Inject constructor(
    private val alarms: AlarmRepository,
    private val settings: SettingsRepository,
    private val coordinator: NesaAlarmCoordinator,
    private val capability: ExactAlarmCapability,
    private val clock: Clock
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmSettingsUiState())
    val state: StateFlow<AlarmSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onEnabledChanged(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun onTimeChanged(time: LocalTime) = update { it.copy(time = time) }

    fun onDayToggled(day: DayOfWeek) = update { alarm ->
        alarm.copy(days = if (day in alarm.days) alarm.days - day else alarm.days + day)
    }

    fun onVibrateChanged(vibrate: Boolean) = update { it.copy(vibrate = vibrate) }

    fun onFadeInChanged(seconds: Int) = update { it.copy(fadeInSeconds = seconds.coerceIn(0, 120)) }

    fun onChallengeRequiredChanged(required: Boolean) =
        update { it.copy(challenge = it.challenge.copy(required = required)) }

    fun onChallengeAdaptiveChanged(adaptive: Boolean) =
        update { it.copy(challenge = it.challenge.copy(adaptive = adaptive)) }

    fun onChallengeTypeChanged(type: WakeChallengeType) =
        update { it.copy(challenge = it.challenge.copy(type = type)) }

    fun onChallengeDifficultyChanged(difficulty: ChallengeDifficulty) =
        update { it.copy(challenge = it.challenge.copy(difficulty = difficulty)) }

    fun onSnoozeMinutesChanged(minutes: Int) =
        update { it.copy(snooze = it.snooze.copy(snoozeMinutes = minutes.coerceIn(1, 60))) }

    fun onMaxSnoozesChanged(count: Int) =
        update { it.copy(snooze = it.snooze.copy(maxSnoozes = count.coerceIn(0, 10))) }

    fun onRetryMinutesChanged(minutes: Int) =
        update { it.copy(snooze = it.snooze.copy(autoRetryMinutes = minutes.coerceIn(1, 60))) }

    fun onMaxRetriesChanged(count: Int) =
        update { it.copy(snooze = it.snooze.copy(maxAutoRetries = count.coerceIn(0, 10))) }

    /** Re-checked on resume: the user may have granted exact alarms meanwhile. */
    fun refreshCapability() {
        _state.update { it.copy(capability = capability.current) }
    }

    /**
     * Where the user can grant exact alarms, or null when this Android version
     * has no such screen. NESA points at it once; it does not nag.
     */
    fun exactAlarmSettingsIntent(): Intent? = capability.settingsIntent()

    private suspend fun load() {
        val preferences = settings.current()
        val existing = preferences.primaryAlarmId?.let { alarms.find(it) }
            ?: alarms.alarms().firstOrNull()

        val alarm = existing ?: Alarm(
            id = UUID.randomUUID().toString(),
            time = preferences.dayWindow.wakeTime,
            days = WEEKDAYS,
            enabled = false
        ).also {
            // Persist immediately so the screen always edits a real row rather
            // than a draft that could be lost.
            coordinator.save(it)
            settings.setPrimaryAlarmId(it.id)
        }

        if (preferences.primaryAlarmId == null) settings.setPrimaryAlarmId(alarm.id)

        _state.value = AlarmSettingsUiState(
            alarm = alarm,
            nextRing = NextAlarmCalculator.next(alarm, ZonedDateTime.now(clock)),
            capability = capability.current,
            loading = false
        )
    }

    private fun update(transform: (Alarm) -> Alarm) {
        val current = _state.value.alarm ?: return
        val updated = transform(current)

        _state.update {
            it.copy(
                alarm = updated,
                nextRing = NextAlarmCalculator.next(updated, ZonedDateTime.now(clock))
            )
        }
        viewModelScope.launch { coordinator.save(updated) }
    }

    private companion object {
        val WEEKDAYS = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )
    }
}
