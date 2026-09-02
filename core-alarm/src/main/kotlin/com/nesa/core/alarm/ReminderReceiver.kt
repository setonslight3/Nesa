package com.nesa.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.notifications.NesaNotifier
import com.nesa.core.scheduling.MissedActivityDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Delivers a reminder, if one is still warranted.
 *
 * The decision is re-made here against current state rather than trusted from
 * when the reminder was scheduled, so an activity the user already completed,
 * skipped or deferred never produces a nudge.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var activities: ActivityRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: NesaNotifier
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val blockId = intent.getStringExtra(EXTRA_BLOCK_ID) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                remind(blockId)
            } catch (error: Exception) {
                Log.w(TAG, "Could not deliver a reminder for block $blockId", error)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun remind(blockId: String) {
        val preferences = settings.current()
        if (!preferences.remindersEnabled) return

        val item = activities.findBlock(blockId) ?: return
        val now = LocalDateTime.now()
        if (!MissedActivityDetector.shouldRemind(item, now, preferences.guidance)) return

        if (!notifier.showReminder(item)) return
        activities.incrementRemindersSent(blockId)

        // Persistence means a bounded series of attempts, so the next one is
        // armed here rather than by a repeating alarm that nobody stops.
        val interval = preferences.guidance.reminderIntervalMinutes
        val remaining = preferences.guidance.maxReminders - (item.block.remindersSent + 1)
        if (interval > 0 && remaining > 0) {
            val nextAt = now.plusMinutes(interval.toLong())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            scheduler.scheduleAt(blockId, nextAt)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.nesa.action.REMIND"
        const val EXTRA_BLOCK_ID = "com.nesa.extra.BLOCK_ID"
        private const val TAG = "NesaReminderReceiver"
    }
}
