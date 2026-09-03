package com.nesa.core.alarm

import com.nesa.core.scheduling.PlanChangeListener
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-arms reminders whenever the plan changes.
 *
 * This is the platform half of [PlanChangeListener]: the domain says the plan
 * for a date has been rewritten, and this turns that into the `AlarmManager`
 * work the domain must not do itself.
 */
@Singleton
class ReminderPlanChangeListener @Inject constructor(
    private val reminders: ReminderScheduler
) : PlanChangeListener {

    override suspend fun onPlanChanged(date: LocalDate) {
        reminders.scheduleFor(date)
    }
}
