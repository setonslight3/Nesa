package com.nesa.core.alarm

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nesa.core.scheduling.DayPlanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Keeps today honest while the app is closed.
 *
 * It notices activities that went unanswered, replans what is left around the
 * anchors that remain, and re-arms the reminders that follow from the new plan.
 *
 * Deferrable work belongs on WorkManager, not on an exact alarm: being half an
 * hour late to notice a missed activity costs nothing, while an exact alarm
 * every half hour would cost battery and the user's exact-alarm allowance.
 */
@HiltWorker
class DayPlanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val planner: DayPlanner,
    private val reminders: ReminderScheduler
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        val today = LocalDate.now()
        planner.refresh(today)
        reminders.scheduleFor(today)
        Result.success()
    } catch (error: Exception) {
        // A transient database or platform failure should be retried rather
        // than leaving the day un-replanned until tomorrow.
        Log.w(TAG, "Replanning the day failed; it will be retried", error)
        Result.retry()
    }

    companion object {
        private const val TAG = "NesaDayPlanWorker"
        private const val WORK_NAME = "nesa-day-plan"
        private const val INTERVAL_MINUTES = 30L

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<DayPlanWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
