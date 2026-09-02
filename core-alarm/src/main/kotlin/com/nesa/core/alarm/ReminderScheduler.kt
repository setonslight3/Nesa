package com.nesa.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the nudge that goes out when an activity is due.
 *
 * Reminders use inexact windows on purpose. Exact alarms are a scarce,
 * user-visible privilege and NESA spends them on waking people up, not on
 * telling them it is time to read. A reminder that arrives two minutes late is
 * still a good reminder.
 *
 * Stale reminders are not tracked or cancelled: [ReminderReceiver] re-reads the
 * activity when it fires and stays quiet if the user already answered. That
 * keeps correctness in one place instead of spread across every state change.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activities: ActivityRepository,
    private val settings: SettingsRepository
) {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService()

    /** Arms a reminder for every activity on [date] that still needs an answer. */
    suspend fun scheduleFor(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        if (!settings.current().remindersEnabled) return

        val now = LocalDateTime.now(java.time.Clock.system(zone))
        activities.plan(date)
            .filter { it.state.needsPlacement }
            .forEach { item ->
                val dueAt = LocalDateTime.of(item.block.date, item.block.start)
                if (dueAt.isAfter(now)) {
                    scheduleAt(item.block.id, dueAt.atZone(zone).toInstant().toEpochMilli())
                }
            }
    }

    fun scheduleAt(blockId: String, triggerAtMillis: Long) {
        val manager = alarmManager ?: return
        try {
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                REMINDER_WINDOW_MILLIS,
                pendingIntent(blockId)
            )
        } catch (denied: SecurityException) {
            Log.w(TAG, "Reminder for block $blockId was refused", denied)
        }
    }

    fun cancel(blockId: String) {
        alarmManager?.cancel(pendingIntent(blockId))
    }

    private fun pendingIntent(blockId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_BLOCK_ID, blockId)
        }
        return PendingIntent.getBroadcast(
            context,
            blockId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val TAG = "NesaReminderScheduler"
        const val REMINDER_WINDOW_MILLIS = 2 * 60 * 1000L
    }
}
