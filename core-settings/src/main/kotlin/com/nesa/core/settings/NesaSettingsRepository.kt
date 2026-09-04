package com.nesa.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nesa.core.model.DayWindow
import com.nesa.core.model.GuidancePersonality
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.ThemeMode
import com.nesa.core.model.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Singleton

private val Context.nesaPreferences: DataStore<Preferences> by preferencesDataStore(name = "nesa_settings")

/**
 * User preferences on DataStore.
 *
 * Every value has a default, and a corrupt or unreadable store degrades to
 * those defaults rather than crashing on launch — the alarm still has to ring
 * tomorrow morning even if a preference file went bad.
 */
@Singleton
class NesaSettingsRepository(
    private val context: Context
) : SettingsRepository {

    private object Keys {
        val DisplayName = stringPreferencesKey("display_name")
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val Guidance = stringPreferencesKey("guidance")
        val WakeMinute = intPreferencesKey("wake_minute")
        val SleepMinute = intPreferencesKey("sleep_minute")
        val MorningEndMinute = intPreferencesKey("morning_end_minute")
        val EveningStartMinute = intPreferencesKey("evening_start_minute")
        val NightStartMinute = intPreferencesKey("night_start_minute")
        val RemindersEnabled = booleanPreferencesKey("reminders_enabled")
        val FitnessEnabled = booleanPreferencesKey("fitness_enabled")
        val PrimaryAlarmId = stringPreferencesKey("primary_alarm_id")
    }

    override val settings: Flow<NesaSettings> = context.nesaPreferences.data
        .catch { throwable ->
            // A damaged preferences file must not take the application down.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toSettings() }

    override suspend fun current(): NesaSettings = settings.first()

    override suspend fun setDisplayName(name: String?) = edit { preferences ->
        if (name.isNullOrBlank()) preferences.remove(Keys.DisplayName)
        else preferences[Keys.DisplayName] = name
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) = edit {
        it[Keys.OnboardingCompleted] = completed
    }

    override suspend fun setThemeMode(mode: ThemeMode) = edit {
        it[Keys.ThemeMode] = mode.name
    }

    override suspend fun setGuidance(guidance: GuidancePersonality) = edit {
        it[Keys.Guidance] = guidance.name
    }

    override suspend fun setDayWindow(window: DayWindow) = edit {
        it[Keys.WakeMinute] = DayWindow.minuteOf(window.wakeTime)
        it[Keys.SleepMinute] = DayWindow.minuteOf(window.sleepTarget)
        it[Keys.MorningEndMinute] = DayWindow.minuteOf(window.morningEnds)
        it[Keys.EveningStartMinute] = DayWindow.minuteOf(window.eveningStarts)
        it[Keys.NightStartMinute] = DayWindow.minuteOf(window.nightStarts)
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) = edit {
        it[Keys.RemindersEnabled] = enabled
    }

    override suspend fun setFitnessEnabled(enabled: Boolean) = edit {
        it[Keys.FitnessEnabled] = enabled
    }

    override suspend fun setPrimaryAlarmId(alarmId: String?) = edit { preferences ->
        if (alarmId == null) preferences.remove(Keys.PrimaryAlarmId)
        else preferences[Keys.PrimaryAlarmId] = alarmId
    }

    private suspend fun edit(block: suspend (MutablePreferences) -> Unit) {
        context.nesaPreferences.edit { preferences -> block(preferences) }
    }

    private fun Preferences.toSettings(): NesaSettings {
        val defaults = DayWindow.Default
        return NesaSettings(
            displayName = this[Keys.DisplayName],
            onboardingCompleted = this[Keys.OnboardingCompleted] ?: false,
            themeMode = this[Keys.ThemeMode].toEnumOr(ThemeMode.SYSTEM),
            guidance = this[Keys.Guidance].toEnumOr(GuidancePersonality.Default),
            dayWindow = DayWindow(
                wakeTime = DayWindow.timeOf(this[Keys.WakeMinute] ?: DayWindow.minuteOf(defaults.wakeTime)),
                sleepTarget = DayWindow.timeOf(this[Keys.SleepMinute] ?: DayWindow.minuteOf(defaults.sleepTarget)),
                morningEnds = DayWindow.timeOf(this[Keys.MorningEndMinute] ?: DayWindow.minuteOf(defaults.morningEnds)),
                eveningStarts = DayWindow.timeOf(this[Keys.EveningStartMinute] ?: DayWindow.minuteOf(defaults.eveningStarts)),
                nightStarts = DayWindow.timeOf(this[Keys.NightStartMinute] ?: DayWindow.minuteOf(defaults.nightStarts))
            ),
            remindersEnabled = this[Keys.RemindersEnabled] ?: true,
            fitnessEnabled = this[Keys.FitnessEnabled] ?: false,
            primaryAlarmId = this[Keys.PrimaryAlarmId]
        )
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback
}
