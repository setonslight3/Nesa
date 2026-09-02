package com.nesa.core.model

/**
 * How insistent NESA is when an activity needs a decision.
 *
 * Persistence means repeated meaningful attempts to get an answer, never
 * notification spam.
 */
enum class GuidancePersonality(
    /** How many reminders NESA sends for one unresolved activity. */
    val maxReminders: Int,
    /** Minutes between reminders. */
    val reminderIntervalMinutes: Int,
    /** Minutes after the planned end before an unanswered activity becomes MISSED. */
    val missedGraceMinutes: Int
) {
    GENTLE(maxReminders = 1, reminderIntervalMinutes = 0, missedGraceMinutes = 60),
    BALANCED(maxReminders = 2, reminderIntervalMinutes = 10, missedGraceMinutes = 30),
    PERSISTENT(maxReminders = 4, reminderIntervalMinutes = 5, missedGraceMinutes = 20),
    STRICT(maxReminders = 6, reminderIntervalMinutes = 3, missedGraceMinutes = 10);

    companion object {
        val Default: GuidancePersonality = BALANCED
    }
}
