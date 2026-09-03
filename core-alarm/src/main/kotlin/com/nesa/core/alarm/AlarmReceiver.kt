package com.nesa.core.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.nesa.core.notifications.NesaNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The moment an alarm is due.
 *
 * A receiver has only a few milliseconds, so it does exactly one thing: hand
 * the alarm to a foreground service that can hold a wake lock and ring for as
 * long as it needs to.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: NesaNotifier
    @Inject lateinit var screenLauncher: AlarmScreenLauncher
    @Inject lateinit var events: AlarmEventLog

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return

        val service = Intent(context, AlarmRingerService::class.java).apply {
            action = AlarmRingerService.ACTION_START
            putExtra(EXTRA_ALARM_ID, alarmId)
        }

        events.record("receiver fired — alarm is due")

        try {
            ContextCompat.startForegroundService(context, service)
            events.record("ringer service start requested")
        } catch (refused: IllegalStateException) {
            // Android 12+ forbids starting a foreground service from a
            // background broadcast unless the alarm was an exact one. When NESA
            // has been reduced to inexact alarms, this is the path taken — and a
            // full-screen notification is always allowed, so the alarm still
            // reaches the user even though nothing can play a sound for it.
            Log.w(TAG, "Foreground start refused; falling back to a notification", refused)
            events.record("service REFUSED by platform — notification only, no sound")
            notifier.postRinger(null, fullScreenIntent(context, alarmId))
        }
    }

    private fun fullScreenIntent(context: Context, alarmId: String): PendingIntent? = try {
        PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            screenLauncher.ringingIntent(context, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } catch (error: Exception) {
        Log.w(TAG, "Could not build the fallback alarm intent", error)
        null
    }

    companion object {
        private const val TAG = "NesaAlarmReceiver"
        const val ACTION_FIRE = "com.nesa.action.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "com.nesa.extra.ALARM_ID"
    }
}
