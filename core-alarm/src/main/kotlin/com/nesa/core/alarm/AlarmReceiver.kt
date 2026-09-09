package com.nesa.core.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.nesa.core.notifications.NesaNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The moment an alarm is due.
 *
 * A receiver has only a few milliseconds, so it does two critical things:
 * 1. Holds a continuous CPU wake lock so the system does not sleep during service start.
 * 2. Hands the alarm to AlarmRingerService which takes over the foreground and starts audio.
 */
class AlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Fallback {
        fun notifier(): NesaNotifier
        fun screenLauncher(): AlarmScreenLauncher
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        processAlarmIntent(context, intent, source = "manifest receiver")
    }

    companion object {
        private const val TAG = "NesaAlarmReceiver"
        const val ACTION_FIRE = "com.nesa.action.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "com.nesa.extra.ALARM_ID"
        const val EXTRA_SCHEDULED_AT = "com.nesa.extra.SCHEDULED_AT"

        /** Delivery is never to the millisecond; this much is not "late". */
        private const val GRACE_SECONDS = 5L

        private val lock = Any()
        private var lastHandledAlarmId: String? = null
        private var lastHandledTimestamp: Long = 0L

        /**
         * Deduplicates deliveries if both the watch dynamic receiver and
         * manifest receiver capture the same broadcast.
         */
        private fun shouldProcess(alarmId: String): Boolean = synchronized(lock) {
            val now = System.currentTimeMillis()
            if (alarmId == lastHandledAlarmId && (now - lastHandledTimestamp) < 10_000L) {
                return false
            }
            lastHandledAlarmId = alarmId
            lastHandledTimestamp = now
            return true
        }

        fun processAlarmIntent(context: Context, intent: Intent, source: String = "receiver") {
            val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return

            // Hold CPU awake immediately so the device cannot fall asleep
            // before the foreground service is initialized and takes over.
            AlarmWakeLock.acquire(context)

            if (!shouldProcess(alarmId)) {
                Log.d(TAG, "Duplicate delivery ignored from $source for $alarmId")
                return
            }

            val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
            val lateBy = if (scheduledAt > 0L) {
                (System.currentTimeMillis() - scheduledAt).coerceAtLeast(0L) / 1000
            } else {
                null
            }
            AlarmEventLog.write(
                context,
                when {
                    lateBy == null -> "[$source] fired — alarm is due"
                    lateBy <= GRACE_SECONDS -> "[$source] fired on time"
                    else -> "[$source] fired ${lateBy}s LATE — system delayed delivery"
                }
            )

            val service = Intent(context, AlarmRingerService::class.java).apply {
                action = AlarmRingerService.ACTION_START
                putExtra(EXTRA_ALARM_ID, alarmId)
            }

            try {
                ContextCompat.startForegroundService(context, service)
                AlarmEventLog.write(context, "ringer service start requested")
            } catch (refused: IllegalStateException) {
                Log.w(TAG, "Foreground start refused; falling back to a notification", refused)
                AlarmEventLog.write(context, "service REFUSED by platform — notification only, no sound")
                AlarmEventLog.write(
                    context,
                    if (Settings.canDrawOverlays(context)) {
                        "overlay permission held"
                    } else {
                        "NO overlay permission — cannot open the alarm screen from the background"
                    }
                )
                postFallbackNotification(context, alarmId)
            }
        }

        private fun postFallbackNotification(context: Context, alarmId: String) {
            runCatching {
                val fallback = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    Fallback::class.java
                )
                val screen = PendingIntent.getActivity(
                    context,
                    alarmId.hashCode(),
                    fallback.screenLauncher().ringingIntent(context, alarmId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                fallback.notifier().postRinger(null, screen)
            }.onFailure {
                Log.w(TAG, "Could not post the fallback alarm notification", it)
                AlarmEventLog.write(context, "fallback notification failed: ${it.javaClass.simpleName}")
            }
        }
    }
}
