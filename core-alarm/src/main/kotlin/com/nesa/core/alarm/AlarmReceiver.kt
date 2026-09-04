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
 * A receiver has only a few milliseconds, so it does exactly one thing: hand
 * the alarm to a foreground service that can hold a wake lock and ring for as
 * long as it needs to.
 *
 * ## Why this is not an @AndroidEntryPoint any more
 *
 * It was, and the injected fields were read in the first few lines. Hilt
 * satisfies those by building the whole singleton graph — Room, DataStore,
 * WorkManager and everything they pull in — *before* `onReceive`'s first
 * statement runs. On a cold delivery that work sits between the platform waking
 * NESA and NESA noticing, and it made the trace unable to tell "Android was
 * late" from "we were slow to look".
 *
 * A working third-party alarm app on the same device has a plain receiver with
 * none of that, and the standard Android alarm-clock shape is a receiver that
 * needs nothing but a Context. So the ordinary path here now touches no
 * injected object at all: it stamps the arrival time straight into
 * SharedPreferences and starts the service. The graph is reached only through
 * [Fallback], and only on the branch that has already failed — where the cost
 * of building it no longer matters.
 */
class AlarmReceiver : BroadcastReceiver() {

    /**
     * The two objects the failure path needs. Resolved lazily, never on the
     * path that works.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Fallback {
        fun notifier(): NesaNotifier
        fun screenLauncher(): AlarmScreenLauncher
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return

        // First statement that does anything, before the service intent is even
        // built. How late the platform was is the one number that separates
        // "NESA never asked" from "Android did not deliver on time" — and it is
        // only trustworthy if nothing of ours has run before it.
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
        val lateBy = if (scheduledAt > 0L) {
            (System.currentTimeMillis() - scheduledAt).coerceAtLeast(0L) / 1000
        } else {
            null
        }
        AlarmEventLog.write(
            context,
            when {
                lateBy == null -> "receiver fired — alarm is due"
                lateBy <= GRACE_SECONDS -> "receiver fired on time"
                else -> "receiver fired ${lateBy}s LATE — the system held it back"
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
            // Android 12+ forbids starting a foreground service from a
            // background broadcast unless the alarm was an exact one. When NESA
            // has been reduced to inexact alarms, this is the path taken — and a
            // full-screen notification is always allowed, so the alarm still
            // reaches the user even though nothing can play a sound for it.
            Log.w(TAG, "Foreground start refused; falling back to a notification", refused)
            AlarmEventLog.write(context, "service REFUSED by platform — notification only, no sound")
            // Whether NESA was even permitted to open a screen from the
            // background at that moment, so the trace says why it could not.
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

    companion object {
        private const val TAG = "NesaAlarmReceiver"
        const val ACTION_FIRE = "com.nesa.action.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "com.nesa.extra.ALARM_ID"
        const val EXTRA_SCHEDULED_AT = "com.nesa.extra.SCHEDULED_AT"

        /** Delivery is never to the millisecond; this much is not "late". */
        private const val GRACE_SECONDS = 5L
    }
}
