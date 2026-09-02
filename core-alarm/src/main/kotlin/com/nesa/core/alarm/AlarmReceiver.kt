package com.nesa.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint

/**
 * The moment an alarm is due.
 *
 * A receiver has only a few milliseconds, so it does exactly one thing: hand
 * the alarm to a foreground service that can hold a wake lock and ring for as
 * long as it needs to.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return

        val service = Intent(context, AlarmRingerService::class.java).apply {
            action = AlarmRingerService.ACTION_START
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        ContextCompat.startForegroundService(context, service)
    }

    companion object {
        const val ACTION_FIRE = "com.nesa.action.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "com.nesa.extra.ALARM_ID"
    }
}
