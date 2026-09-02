package com.nesa.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nesa.core.model.PlannedActivity
import com.nesa.core.scheduling.ActivityEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts NESA's notifications.
 *
 * Reminders always carry the three decisions the product model cares about —
 * done, later, skip — so that answering NESA never requires opening the app.
 * That is what keeps "no response" meaning genuinely no response.
 */
@Singleton
class NesaNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** False when the user has denied or disabled notifications. Never assume. */
    val enabled: Boolean
        get() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannels() = NesaChannels.ensureCreated(context)

    /**
     * Shows a reminder for one activity. Returns false when the notification
     * could not be posted, so callers can fall back rather than pretend.
     */
    fun showReminder(item: PlannedActivity): Boolean {
        if (!enabled) return false
        ensureChannels()

        val notification = NotificationCompat.Builder(context, NesaChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_nesa_notification)
            .setContentTitle(item.title)
            .setContentText(
                context.getString(
                    R.string.nesa_reminder_window,
                    item.block.start.format(timeFormat),
                    item.block.end.format(timeFormat)
                )
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(
                0,
                context.getString(R.string.nesa_action_complete),
                actionIntent(item.block.id, ActivityAction.COMPLETE)
            )
            .addAction(
                0,
                context.getString(R.string.nesa_action_later),
                actionIntent(item.block.id, ActivityAction.LATER)
            )
            .addAction(
                0,
                context.getString(R.string.nesa_action_skip),
                actionIntent(item.block.id, ActivityAction.SKIP)
            )
            .build()

        return post(item.block.id.hashCode(), notification)
    }

    /** The quiet notice that accompanies the alarm's foreground service. */
    fun buildRingerNotification(label: String, fullScreenIntent: PendingIntent?): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, NesaChannels.ALARM)
            .setSmallIcon(R.drawable.ic_nesa_notification)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.nesa_alarm_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .apply {
                // A full-screen intent is what lets the alarm take over a locked
                // screen. If the platform refuses it, the notification alone is
                // still a working, if quieter, alarm.
                if (fullScreenIntent != null) {
                    setFullScreenIntent(fullScreenIntent, true)
                    setContentIntent(fullScreenIntent)
                }
            }
            .build()
    }

    fun cancel(blockId: String) {
        NotificationManagerCompat.from(context).cancel(blockId.hashCode())
    }

    private fun post(id: Int, notification: Notification): Boolean = try {
        NotificationManagerCompat.from(context).notify(id, notification)
        true
    } catch (denied: SecurityException) {
        // POST_NOTIFICATIONS can be revoked between the check and the call.
        false
    }

    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(blockId: String, action: ActivityAction): PendingIntent {
        val intent = Intent(context, ActivityActionReceiver::class.java).apply {
            this.action = action.intentAction
            putExtra(ActivityActionReceiver.EXTRA_BLOCK_ID, blockId)
        }
        return PendingIntent.getBroadcast(
            context,
            (blockId + action.name).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** The decisions a user can take straight from a notification. */
enum class ActivityAction(val intentAction: String, val event: ActivityEvent) {
    COMPLETE("com.nesa.action.COMPLETE", ActivityEvent.COMPLETE),
    LATER("com.nesa.action.LATER", ActivityEvent.DEFER),
    SKIP("com.nesa.action.SKIP", ActivityEvent.SKIP);

    companion object {
        fun fromIntentAction(action: String?): ActivityAction? =
            entries.firstOrNull { it.intentAction == action }
    }
}
