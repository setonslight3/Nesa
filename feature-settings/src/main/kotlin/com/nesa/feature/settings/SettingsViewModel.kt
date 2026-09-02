package com.nesa.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.DayWindow
import com.nesa.core.model.GuidancePersonality
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.ThemeMode
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.notifications.NesaNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class SettingsUiState(
    val settings: NesaSettings = NesaSettings.Default,
    val notificationsAllowed: Boolean = true
)

/**
 * Settings.
 *
 * Changing the shape of the day changes how the scheduler behaves, so every
 * edit is validated against [DayWindow.validated] before it is stored — an
 * incoherent day (an evening before the morning ends) would produce a plan
 * nobody could make sense of.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val notifier: NesaNotifier
) : ViewModel() {

    private val notificationsAllowed = MutableStateFlow(notifier.enabled)

    val state: StateFlow<SettingsUiState> =
        combine(settings.settings, notificationsAllowed) { preferences, allowed ->
            SettingsUiState(settings = preferences, notificationsAllowed = allowed)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState()
        )

    /** Re-checked on resume: the user may have changed the grant in Android. */
    fun refreshNotificationPermission() {
        notificationsAllowed.value = notifier.enabled
    }

    fun onThemeModeChanged(mode: ThemeMode) = viewModelScope.launch {
        settings.setThemeMode(mode)
    }

    fun onGuidanceChanged(guidance: GuidancePersonality) = viewModelScope.launch {
        settings.setGuidance(guidance)
    }

    fun onRemindersEnabledChanged(enabled: Boolean) = viewModelScope.launch {
        settings.setRemindersEnabled(enabled)
    }

    fun onDisplayNameChanged(name: String) = viewModelScope.launch {
        settings.setDisplayName(name.trim().takeIf { it.isNotBlank() })
    }

    fun onDayWindowFieldChanged(field: DayWindowField, time: LocalTime) = viewModelScope.launch {
        val current = settings.current().dayWindow
        val candidate = when (field) {
            DayWindowField.WAKE -> current.copy(wakeTime = time)
            DayWindowField.SLEEP -> current.copy(sleepTarget = time)
            DayWindowField.MORNING_ENDS -> current.copy(morningEnds = time)
            DayWindowField.EVENING_STARTS -> current.copy(eveningStarts = time)
            DayWindowField.NIGHT_STARTS -> current.copy(nightStarts = time)
        }
        // A window that does not make sense is rejected rather than stored and
        // then quietly mis-scheduled.
        runCatching { candidate.validated() }.onSuccess { settings.setDayWindow(it) }
    }

    fun notificationSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

enum class DayWindowField { WAKE, SLEEP, MORNING_ENDS, EVENING_STARTS, NIGHT_STARTS }
