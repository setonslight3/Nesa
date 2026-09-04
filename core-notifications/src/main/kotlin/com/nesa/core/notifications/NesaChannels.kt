package com.nesa.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Notification channels, one per category, so the user can silence reminders
 * without silencing the alarm that gets them out of bed.
 */
object NesaChannels {

    /**
     * Version 2 of the reminders channel, and the id has to change to get here.
     *
     * A channel's importance is fixed the moment it is first created: Android
     * ignores every later attempt to raise it, on the reasoning that an app
     * should not be able to make itself louder behind the user's back. The
     * original channel was created at IMPORTANCE_DEFAULT, which makes a sound
     * but never a heads-up pop-up — so on every phone that already had NESA
     * installed, no code change to the old id could have produced one.
     *
     * A new id is the documented way through. The old channel is deleted in
     * [ensureCreated] so it does not sit in the user's notification settings as
     * a dead entry they can still toggle.
     */
    const val REMINDERS = "nesa_reminders_v2"

    /** Retired. Kept only so [ensureCreated] can remove it. */
    private const val REMINDERS_V1 = "nesa_reminders"
    const val ALARM = "nesa_alarm"
    const val REVIEW = "nesa_review"
    const val SERVICE = "nesa_service"

    /** Short double buzz: noticeable, and over quickly. A reminder is not an alarm. */
    private val REMINDER_VIBRATION = longArrayOf(0, 250, 150, 250)

    /**
     * Idempotent: creating a channel that already exists updates its name and
     * description but never re-enables one the user turned off.
     */
    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        // Retired before the replacement is created, so the two never appear
        // side by side in settings.
        manager.deleteNotificationChannel(REMINDERS_V1)

        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                context.getString(R.string.nesa_channel_reminders_name),
                // HIGH, not DEFAULT. DEFAULT makes a sound and nothing else;
                // HIGH is what produces the heads-up pop-up over whatever is on
                // screen. A reminder the user has to go looking for is not a
                // reminder — and answering NESA from the notification is the
                // whole point of the three actions it carries.
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.nesa_channel_reminders_description)
                enableVibration(true)
                vibrationPattern = REMINDER_VIBRATION
                // Sound is left at the channel default rather than set here, so
                // a user who picks their own notification tone in system
                // settings keeps it.
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ALARM,
                context.getString(R.string.nesa_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.nesa_channel_alarm_description)
                // The ringer service owns the sound so it can fade in; the
                // notification itself stays silent to avoid a double alert.
                setSound(null, null)
                enableVibration(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                REVIEW,
                context.getString(R.string.nesa_channel_review_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.nesa_channel_review_description)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE,
                context.getString(R.string.nesa_channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.nesa_channel_service_description)
            }
        )
    }
}
