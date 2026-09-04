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
     * Whether the fitness module is in use.
     *
     * Off by default, and the product rule behind that is not decoration: users
     * are never forced to configure a module they do not use. While this is
     * false the fitness screen is not reachable and nothing about training is
     * shown, scheduled or asked about anywhere in the app.
     */
    val fitnessEnabled: Boolean = false,
    /** The alarm the timeline treats as "the" wake alarm, if one is set up. */
    val primaryAlarmId: String? = null
) {
    companion object {
        val Default: NesaSettings = NesaSettings()
    }
}
