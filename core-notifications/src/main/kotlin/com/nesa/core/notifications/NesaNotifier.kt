package com.nesa.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
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

    /**
     * Whether NESA may show a full-screen intent — the thing that turns a
     * notification into an alarm taking over the screen.
     *
     * Android 14 made this a separate, revocable grant. It is given by default to
     * apps whose purpose is alarms or calls, but it can be withdrawn, and a
     * withdrawn grant silently downgrades the alarm to an ordinary heads-up
     * notification. Before API 34 the capability always existed.
     */
    val canUseFullScreenIntent: Boolean
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            true
        } else {
            context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() ?: false
        }

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
            // On API 26+ the channel's importance decides whether this pops up
            // and makes a sound, not this line — see NesaChannels.REMINDERS,
            // which is why the channel id had to change. This is kept so the
            // notification's own intent matches the channel's rather than
            // quietly contradicting it.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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

    /**
     * The alarm notification.
     *
     * It is both the foreground-service notice and, when the platform refuses to
     * let NESA start that service from the background, the alarm itself — which
     * is why it carries the full-screen intent rather than merely announcing a
     * service that does.
     */
    fun buildRingerNotification(label: String?, fullScreenIntent: PendingIntent?): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, NesaChannels.ALARM)
            .setSmallIcon(R.drawable.ic_nesa_notification)
            .setContentTitle(label ?: context.getString(R.string.nesa_alarm_label_default))
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

    /**
     * The quiet notice shown while an alarm is waiting to ring.
     *
     * This is the foreground-service notification for [com.nesa.core.alarm]'s
     * alarm watch, and it is deliberately the *only* thing that service does.
     * Low importance, silent, no full-screen intent: it is a statement that an
     * alarm is set, not an alert. It carries the alarm's time so the user can
     * see at a glance that NESA has the morning covered.
     */
    fun buildWatchNotification(nextLabel: String?): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, NesaChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_nesa_notification)
            .setContentTitle(context.getString(R.string.nesa_keep_alive_title))
            .setContentText(
                if (nextLabel != null) {
                    context.getString(R.string.nesa_keep_alive_next, nextLabel)
                } else {
                    context.getString(R.string.nesa_keep_alive_text)
                }
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    /**
     * Posts the alarm notification directly, without a foreground service.
     *
     * This is the fallback path for Android 12 and later, which forbids starting
     * a foreground service from a background broadcast unless the alarm that
     * triggered it was an exact one. A full-screen notification is always
     * permitted, so the alarm still reaches the user — quieter, but present.
     */
    fun postRinger(label: String?, fullScreenIntent: PendingIntent?): Boolean =
        post(RINGER_NOTIFICATION_ID, buildRingerNotification(label, fullScreenIntent))

    fun cancelRinger() {
        NotificationManagerCompat.from(context).cancel(RINGER_NOTIFICATION_ID)
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

    companion object {
        /**
         * Shared so the ringer service's foreground notification and the
         * background fallback are the same notification, not two competing ones.
         */
        const val RINGER_NOTIFICATION_ID: Int = 1001

        /** Distinct from the ringer, so the watch is not cancelled when an
         *  alarm finishes ringing and the ringer notification is removed. */
        const val WATCH_NOTIFICATION_ID: Int = 1002
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
