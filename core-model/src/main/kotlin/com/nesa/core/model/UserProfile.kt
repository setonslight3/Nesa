package com.nesa.core.model

import java.time.Instant

/** A goal the user selected during onboarding or added later. */
data class Goal(
    val id: String,
    val title: String,
    val category: GoalCategory,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Instant = Instant.EPOCH
)

enum class GoalCategory {
    PRODUCTIVITY,
    FITNESS,
    LEARNING,
    SLEEP,
    TIME_MANAGEMENT,
    CONSISTENCY,
    PERSONAL_PROJECTS,
    CUSTOM
}

enum class GoalStatus { ACTIVE, ACHIEVED, ARCHIVED }

/**
 * The single local user. NESA's core requires no account, so this is not an
 * identity record — it is the small set of answers onboarding collects.
 */
data class UserProfile(
    val id: String = LOCAL_USER_ID,
    val displayName: String? = null,
    val goals: List<Goal> = emptyList(),
    val dayWindow: DayWindow = DayWindow.Default,
    val guidance: GuidancePersonality = GuidancePersonality.Default,
    val onboardingCompleted: Boolean = false
) {
    companion object {
        const val LOCAL_USER_ID: String = "local"
    }
}
