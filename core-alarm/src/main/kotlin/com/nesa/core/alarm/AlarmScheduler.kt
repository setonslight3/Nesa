package com.nesa.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.nesa.core.model.Alarm
import com.nesa.core.scheduling.NextAlarmCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts alarms on the platform's clock.
 *
 * Order matters here: the alarm's own state is persisted before anything is
 * handed to [AlarmManager], so a process death between the two leaves NESA able
 * to re-arm on the next boot rather than losing the alarm entirely. That
 * ordering is the caller's responsibility and is honoured by
 * [NesaAlarmCoordinator].
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capability: ExactAlarmCapability
) {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService()

    /**
     * Arms [alarm] for its next occurrence after [from].
     *
     * @return the moment it will ring, or null when the alarm is disabled or
     *   the platform refused to accept it.
     */
    fun scheduleNext(alarm: Alarm, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        cancel(alarm.id)
        val next = NextAlarmCalculator.next(alarm, from) ?: return null
        return if (scheduleAt(alarm, next)) next else null
    }

    /** Arms [alarm] for one specific moment, used by snooze and auto-retry. */
    fun scheduleAt(alarm: Alarm, at: ZonedDateTime): Boolean {
        val manager = alarmManager ?: return false
        val triggerAtMillis = at.toInstant().toEpochMilli()
        val operation = firePendingIntent(alarm.id)

        return try {
            if (capability.isExact) {
                // setAlarmClock is the strongest guarantee Android offers, and it
                // surfaces the alarm in the status bar so the user can see it.
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent()),
                    operation
                )
            } else {
                // Without exact alarms the wake time can drift, but an
                // approximate alarm still beats no alarm at all.
                manager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    INEXACT_WINDOW_MILLIS,
                    operation
                )
            }
            true
        } catch (denied: SecurityException) {
            // The permission can be revoked between the check and the call.
            Log.w(TAG, "Alarm ${alarm.id} was refused by the platform", denied)
            false
        }
    }

    fun cancel(alarmId: String) {
        alarmManager?.cancel(firePendingIntent(alarmId))
    }

    private fun firePendingIntent(alarmId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Opens NESA when the user taps the system's alarm indicator. */
    private fun showPendingIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            SHOW_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val TAG = "NesaAlarmScheduler"
        const val SHOW_REQUEST_CODE = 1
        const val INEXACT_WINDOW_MILLIS = 5 * 60 * 1000L
    }
}
