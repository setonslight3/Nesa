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

    const val REMINDERS = "nesa_reminders"
    const val ALARM = "nesa_alarm"
    const val REVIEW = "nesa_review"
    const val SERVICE = "nesa_service"

    /**
     * Idempotent: creating a channel that already exists updates its name and
     * description but never re-enables one the user turned off.
     */
    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                context.getString(R.string.nesa_channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.nesa_channel_reminders_description)
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
                setBypassDnd(true)
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
