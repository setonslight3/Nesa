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
    private val capability: ExactAlarmCapability,
    private val events: AlarmEventLog
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
        val next = NextAlarmCalculator.next(alarm, from)
        if (next == null) {
            Log.w(
                TAG,
                "Alarm ${alarm.id} has no next occurrence " +
                    "(enabled=${alarm.enabled}, days=${alarm.days.size}) — nothing armed"
            )
            return null
        }
        return if (scheduleAt(alarm, next)) next else null
    }

    /** Arms [alarm] for one specific moment, used by snooze and auto-retry. */
    fun scheduleAt(alarm: Alarm, at: ZonedDateTime): Boolean {
        val manager = alarmManager ?: return false
        val triggerAtMillis = at.toInstant().toEpochMilli()
        val operation = firePendingIntent(alarm.id, triggerAtMillis)

        // Ids and times only: enough to diagnose a silent failure from logcat,
        // without writing the user's plan into the system log.
        Log.i(TAG, "Arming ${alarm.id} for $at (exact=${capability.isExact})")
        events.record("armed for ${at.toLocalTime()} (exact=${capability.isExact})")

        if (capability.isExact) {
            try {
                // setAlarmClock is the strongest guarantee Android offers, and it
                // surfaces the alarm in the status bar so the user can see it.
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent()),
                    operation
                )
                return true
            } catch (denied: SecurityException) {
                Log.w(TAG, "setAlarmClock refused for alarm ${alarm.id}, trying exact while idle", denied)
                try {
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operation
                    )
                    return true
                } catch (deniedExact: SecurityException) {
                    Log.w(TAG, "setExactAndAllowWhileIdle refused for alarm ${alarm.id}, falling back to setAndAllowWhileIdle", deniedExact)
                }
            }
        }

        return try {
            // setAndAllowWhileIdle wakes the device even in Doze mode without requiring exact alarm permissions
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "All alarm scheduling mechanisms failed for alarm ${alarm.id}", e)
            events.record("arming FAILED: ${e.javaClass.simpleName}")
            false
        }
    }

    fun cancel(alarmId: String) {
        alarmManager?.cancel(firePendingIntent(alarmId))
    }

    /**
     * Whether the platform is still holding an alarm for [alarmId].
     *
     * `FLAG_NO_CREATE` returns null when no matching PendingIntent exists, and a
     * PendingIntent held by AlarmManager stays alive for as long as the alarm
     * does. So this answers the question neither logs nor guesswork can: has the
     * alarm been armed, and is it *still* armed now?
     *
     * That distinction is what separates a scheduling bug from a device that
     * cancelled the alarm afterwards — several manufacturers drop every alarm an
     * app owns when the app is swiped out of the recents list.
     */
    fun isArmed(alarmId: String): Boolean {
        val intent = Intent(context, AlarmRingerService::class.java).apply {
            action = AlarmRingerService.ACTION_START
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getForegroundService(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }

    /**
     * The next alarm clock the system knows about, from any application.
     *
     * Not conclusive on its own — another alarm app could own it — but when it
     * matches the time NESA just set, the alarm reached the platform.
     */
    fun nextSystemAlarmClockMillis(): Long? =
        alarmManager?.nextAlarmClock?.triggerTime

    /**
     * Starts the ringer service directly, rather than sending a broadcast.
     *
     * This is the difference between an alarm that arrives and one that waits.
     * Android defers broadcasts to an app it has frozen, holding them until
     * something wakes the process — which is exactly the symptom seen on a
     * Transsion device: armed for 23:34:40, delivered at 23:35:25, the moment the
     * user reopened the app. Every step after delivery was already correct.
     *
     * A PendingIntent that starts a foreground service is not a broadcast and is
     * not subject to that queue. An exact alarm also exempts the start from the
     * usual ban on launching a foreground service from the background, so the
     * two work together.
     *
     * @param triggerAtMillis travels with the alarm so the service can report how
     *   late delivery actually was. PendingIntent equality ignores extras, so
     *   cancelling and the armed check still match regardless.
     */
    private fun firePendingIntent(alarmId: String, triggerAtMillis: Long = 0L): PendingIntent {
        val intent = Intent(context, AlarmRingerService::class.java).apply {
            action = AlarmRingerService.ACTION_START
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, triggerAtMillis)
        }
        return PendingIntent.getForegroundService(
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
