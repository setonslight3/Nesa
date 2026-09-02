package com.nesa.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.Alarm
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Goal
import com.nesa.core.model.GoalCategory
import com.nesa.core.model.repository.GoalRepository
import com.nesa.core.model.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/** The three questions onboarding is allowed to ask. */
enum class OnboardingStep { WELCOME, GOALS, RHYTHM }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val displayName: String = "",
    val selectedGoals: Set<GoalCategory> = emptySet(),
    val wakeTime: LocalTime = DayWindow.Default.wakeTime,
    val sleepTarget: LocalTime = DayWindow.Default.sleepTarget,
    val createWakeAlarm: Boolean = true,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val errorMessage: String? = null
) {
    val canGoBack: Boolean get() = step != OnboardingStep.WELCOME
    val isLastStep: Boolean get() = step == OnboardingStep.RHYTHM
}

/**
 * Onboarding asks for a name, some goals, and when the day starts and ends.
 *
 * That is the whole of it. Every module NESA will ever have is configured when
 * the user activates it, not before — a person who only wants a timeline should
 * never have to answer questions about fitness to get one. Every field here has
 * a working default, so skipping straight to the end produces a usable day.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val goals: GoalRepository,
    private val alarmSetup: OnboardingAlarmSetup
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onNameChanged(name: String) = _state.update { it.copy(displayName = name) }

    fun onGoalToggled(category: GoalCategory) = _state.update { current ->
        val selected = if (category in current.selectedGoals) {
            current.selectedGoals - category
        } else {
            current.selectedGoals + category
        }
        current.copy(selectedGoals = selected)
    }

    fun onWakeTimeChanged(time: LocalTime) = _state.update { it.copy(wakeTime = time) }

    fun onSleepTargetChanged(time: LocalTime) = _state.update { it.copy(sleepTarget = time) }

    fun onCreateWakeAlarmChanged(create: Boolean) = _state.update { it.copy(createWakeAlarm = create) }

    fun onBack() = _state.update { current ->
        val previous = when (current.step) {
            OnboardingStep.WELCOME -> OnboardingStep.WELCOME
            OnboardingStep.GOALS -> OnboardingStep.WELCOME
            OnboardingStep.RHYTHM -> OnboardingStep.GOALS
        }
        current.copy(step = previous, errorMessage = null)
    }

    fun onNext() {
        val current = _state.value
        if (current.isLastStep) {
            finish()
            return
        }
        val next = when (current.step) {
            OnboardingStep.WELCOME -> OnboardingStep.GOALS
            OnboardingStep.GOALS -> OnboardingStep.RHYTHM
            OnboardingStep.RHYTHM -> OnboardingStep.RHYTHM
        }
        _state.update { it.copy(step = next, errorMessage = null) }
    }

    /** Accepts every default and goes straight in. */
    fun onSkip() = finish()

    fun onErrorShown() = _state.update { it.copy(errorMessage = null) }

    private fun finish() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, errorMessage = null) }

        viewModelScope.launch {
            val current = _state.value
            try {
                val window = DayWindow.Default.copy(
                    wakeTime = current.wakeTime,
                    sleepTarget = current.sleepTarget
                )
                settings.setDisplayName(current.displayName.trim().takeIf { it.isNotBlank() })
                settings.setDayWindow(window)
                goals.replaceAll(current.selectedGoals.map(::goalFor))

                if (current.createWakeAlarm) {
                    alarmSetup.createDailyWakeAlarm(current.wakeTime)
                }

                // Written last: until this flips, an interrupted onboarding
                // simply starts again rather than dropping the user into a
                // half-configured application.
                settings.setOnboardingCompleted(true)
                _state.update { it.copy(saving = false, finished = true) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(saving = false, errorMessage = error.message ?: "Could not save your setup.")
                }
            }
        }
    }

    private fun goalFor(category: GoalCategory) = Goal(
        id = UUID.randomUUID().toString(),
        title = category.name,
        category = category,
        createdAt = Instant.now()
    )
}

/**
 * Creating the wake alarm needs the alarm platform, which onboarding has no
 * business knowing about. The application module supplies this.
 */
interface OnboardingAlarmSetup {
    suspend fun createDailyWakeAlarm(time: LocalTime): Alarm
}
