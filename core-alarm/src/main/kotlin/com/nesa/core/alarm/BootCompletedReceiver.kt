package com.nesa.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Restores everything the platform forgets.
 *
 * Android drops all scheduled alarms on reboot, and a timezone or clock change
 * invalidates the ones that are already set. Both cases are handled the same
 * way: re-derive every alarm and reminder from persisted state. Nothing here
 * relies on anything having been kept in memory.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: NesaAlarmCoordinator
    @Inject lateinit var reminders: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                coordinator.rearmAll()
                reminders.scheduleFor(LocalDate.now())
                DayPlanWorker.enqueuePeriodic(context)
            } catch (error: Exception) {
                Log.w(TAG, "Could not restore alarms after ${intent.action}", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NesaBootReceiver"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
