package com.nesa.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.DayWindow
import com.nesa.core.model.GuidancePersonality
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.ThemeMode
import com.nesa.core.model.repository.AlarmRepository
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.alarm.BackgroundReliability
import com.nesa.core.alarm.KeepAliveController
import com.nesa.core.alarm.SystemAlarmHandoff
import com.nesa.core.alarm.ReliabilityStatus
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
    val notificationsAllowed: Boolean = true,
    val alarmEvents: List<String> = emptyList(),
    val reliability: ReliabilityStatus = ReliabilityStatus(
        exactAlarmsAllowed = true,
        ignoringBatteryOptimisations = true,
        notificationsAllowed = true
    )
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
    private val notifier: NesaNotifier,
    private val reliability: BackgroundReliability,
    private val keepAlive: KeepAliveController,
    private val systemAlarm: SystemAlarmHandoff,
    private val alarms: AlarmRepository
) : ViewModel() {

    private val notificationsAllowed = MutableStateFlow(notifier.enabled)
    private val reliabilityStatus = MutableStateFlow(
        ReliabilityStatus(
            exactAlarmsAllowed = true,
            ignoringBatteryOptimisations = true,
            notificationsAllowed = true,
            canAppearOverOtherApps = true
        )
    )

    val state: StateFlow<SettingsUiState> =
        combine(settings.settings, notificationsAllowed, reliabilityStatus) { preferences, allowed, status ->
            SettingsUiState(
                settings = preferences,
                notificationsAllowed = allowed,
                alarmEvents = reliability.recentEvents(),
                reliability = status
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState()
        )

    /**
     * Re-checked on resume, because every one of these is granted in a system
     * screen the user leaves NESA to visit — so the answer is routinely stale by
     * the time they come back.
     */
    fun refreshPermissions() {
        notificationsAllowed.value = notifier.enabled
        viewModelScope.launch { reliabilityStatus.value = reliability.status() }
    }

    /**
     * Arms the real alarm a minute from now.
     *
     * The user then locks the phone and waits. If it rings, the whole path
     * works; if it does not, the armed/not-armed line above says which half
     * failed. That is a question no amount of reading the code can settle.
     */
    fun onTestAlarm(onArmed: (Long?) -> Unit) {
        viewModelScope.launch {
            val at = reliability.runAlarmTest()
            reliabilityStatus.value = reliability.status()
            onArmed(at)
        }
    }

    val systemAlarmAvailable: Boolean get() = systemAlarm.isAvailable

    /**
     * Builds the handoff intent for the current alarm and hands it back for the
     * screen to launch. The intent is returned rather than started here so this
     * class never needs a Context, and null means there was no alarm to hand
     * over rather than a silent failure.
     */
    fun onHandOffToSystemAlarm(onReady: (Intent?) -> Unit) {
        viewModelScope.launch {
            val alarm = alarms.alarms().firstOrNull()
            onReady(alarm?.let(systemAlarm::createIntent))
        }
    }

    fun systemAlarmListIntent(): Intent = systemAlarm.showAlarmsIntent()

    fun onClearAlarmEvents() {
        reliability.clearEvents()
        refreshPermissions()
    }

    fun batteryOptimisationRequest(): Intent = reliability.batteryOptimisationRequest()

    fun exactAlarmSettings(): Intent? = reliability.exactAlarmSettings()

    fun overlaySettings(): Intent = reliability.overlaySettings()

    fun appDetailsSettings(): Intent = reliability.appDetailsSettings()

    fun onThemeModeChanged(mode: ThemeMode) = viewModelScope.launch {
        settings.setThemeMode(mode)
    }

    fun onGuidanceChanged(guidance: GuidancePersonality) = viewModelScope.launch {
        settings.setGuidance(guidance)
    }

    fun onRemindersEnabledChanged(enabled: Boolean) = viewModelScope.launch {
        settings.setRemindersEnabled(enabled)
    }

    /**
     * Turning this on starts the keep-alive service immediately, rather than at
     * the next launch — the user turned it on because their alarms are failing
     * now.
     */
    fun onKeepAliveChanged(enabled: Boolean) = viewModelScope.launch {
        settings.setKeepAliveEnabled(enabled)
        if (enabled) keepAlive.start() else keepAlive.stop()
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
