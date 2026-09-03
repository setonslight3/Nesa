package com.nesa.core.model

/**
 * The small set of scalar preferences NESA keeps outside the database.
 *
 * Everything here has a sane default, because onboarding is deliberately short:
 * a user who skips every optional question still ends up with a working day.
 */
data class NesaSettings(
    val displayName: String? = null,
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val guidance: GuidancePersonality = GuidancePersonality.Default,
    val dayWindow: DayWindow = DayWindow.Default,
    val remindersEnabled: Boolean = true,
    /**
     * Keeps NESA's process alive with an ongoing notification, for phones that
     * freeze background apps and drop their alarms. Off by default: it costs a
     * permanent notification and most phones do not need it.
     */
    val keepAliveEnabled: Boolean = false,
    /** The alarm the timeline treats as "the" wake alarm, if one is set up. */
    val primaryAlarmId: String? = null
) {
    companion object {
        val Default: NesaSettings = NesaSettings()
    }
}
