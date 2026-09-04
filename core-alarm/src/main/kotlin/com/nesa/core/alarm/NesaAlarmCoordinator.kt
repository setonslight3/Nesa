package com.nesa.core.alarm

import android.util.Log
import com.nesa.core.model.Alarm
import com.nesa.core.model.repository.AlarmRepository
import com.nesa.core.scheduling.NextAlarmCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that decides what happens to an alarm next.
 *
 * Every path here persists before it schedules. If the process dies in between,
 * the alarm is still in the database and [rearmAll] will pick it up on the next
 * boot — which is the difference between an alarm that is merely usually
 * reliable and one that can be trusted with a morning.
 */
@Singleton
class NesaAlarmCoordinator @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val sessions: AlarmSessionStore
) {

    /**
     * Application-lifetime scope for writes that must not be tied to a screen.
     *
     * A user who picks an alarm time and immediately navigates away would
     * otherwise have the save cancelled with the view model that started it —
     * and an alarm that silently failed to save is the worst bug this class
     * could have.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget [save], for callers whose lifetime is shorter than the write. */
    fun saveDetached(alarm: Alarm) {
        scope.launch {
            runCatching { save(alarm) }
                .onFailure { Log.w(TAG, "Could not save alarm ${alarm.id}", it) }
        }
    }

    /** Persists an alarm and then arms it. Used by the alarm settings screen. */
    suspend fun save(alarm: Alarm) {
        alarms.save(alarm)
        sessions.clear(alarm.id)
        if (alarm.enabled) {
            val armed = scheduler.scheduleNext(alarm)
            Log.i(TAG, "Saved ${alarm.id}; next ring ${armed ?: "none"}")
        } else {
            scheduler.cancel(alarm.id)
            Log.i(TAG, "Saved ${alarm.id} disabled; nothing armed")
        }
        refreshWatch()
    }

    suspend fun delete(alarmId: String) {
        scheduler.cancel(alarmId)
        sessions.clear(alarmId)
        alarms.delete(alarmId)
        refreshWatch()
    }

    /** Re-arms every enabled alarm. Safe to call repeatedly. */
    suspend fun rearmAll(from: ZonedDateTime = ZonedDateTime.now()) {
        alarms.alarms().filter { it.enabled }.forEach { alarm ->
            val armed = scheduler.scheduleNext(alarm, from)
            if (armed == null) {
                Log.w(TAG, "Alarm ${alarm.id} could not be armed; it will retry on the next trigger")
            }
        }
        refreshWatch(from)
    }

    /**
     * The user asked for a few more minutes.
     *
     * Once the snooze allowance is spent NESA stops offering more, and either
     * moves on to the next occurrence or lets the user sleep in deliberately.
     */
    suspend fun snooze(alarmId: String, now: ZonedDateTime): ZonedDateTime? {
        val alarm = alarms.find(alarmId) ?: return null
        val session = sessions.session(alarmId)

        if (NextAlarmCalculator.snoozeExhausted(alarm, session.snoozeCount)) {
            dismiss(alarmId)
            return null
        }

        sessions.recordSnooze(alarmId)
        val at = NextAlarmCalculator.afterSnooze(alarm, now)
        if (!scheduler.scheduleAt(alarm, at)) return null
        refreshWatch(now, soonest = at)
        return at
    }

    /**
     * Nobody answered. This is not a snooze and not a skip: NESA comes back a
     * bounded number of times, then stands down until the next occurrence.
     */
    suspend fun retryAfterSilence(alarmId: String, now: ZonedDateTime): ZonedDateTime? {
        val alarm = alarms.find(alarmId) ?: return null
        val session = sessions.session(alarmId)

        val at = NextAlarmCalculator.afterSilence(alarm, now, session.autoRetryCount)
        if (at == null) {
            dismiss(alarmId)
            return null
        }

        sessions.recordRetry(alarmId)
        if (!scheduler.scheduleAt(alarm, at)) return null
        refreshWatch(now, soonest = at)
        return at
    }

    /**
     * Arms the first alarm a short time from now, leaving everything else alone.
     *
     * A test that goes through the real scheduling and ringing path is the only
     * way to tell "the alarm was never armed" from "the alarm was armed and the
     * device dropped it" without a debugger attached. The regular schedule is
     * restored on the next save, boot, or launch.
     */
    suspend fun armTestAlarm(secondsFromNow: Long = 60L): ZonedDateTime? {
        val alarm = alarms.alarms().firstOrNull() ?: return null
        val at = ZonedDateTime.now().plusSeconds(secondsFromNow)
        Log.i(TAG, "Arming test alarm ${alarm.id} for $at")
        if (!scheduler.scheduleAt(alarm, at)) return null
        refreshWatch(soonest = at)
        return at
    }

    /** True when the platform is still holding an alarm for the first alarm. */
    suspend fun isPrimaryAlarmArmed(): Boolean {
        val alarm = alarms.alarms().firstOrNull() ?: return false
        return scheduler.isArmed(alarm.id)
    }

    fun nextSystemAlarmClockMillis(): Long? = scheduler.nextSystemAlarmClockMillis()

    /** The alarm is finished for today; arm the next occurrence if it repeats. */
    suspend fun dismiss(alarmId: String) {
        val alarm = alarms.find(alarmId) ?: return
        sessions.clear(alarmId)
        if (alarm.enabled && alarm.repeats) {
            scheduler.scheduleNext(alarm)
        } else {
            scheduler.cancel(alarmId)
        }
        refreshWatch()
    }

    /**
     * Starts or stops the alarm watch to match what is now armed.
     *
     * The watch is what keeps NESA's process out of the manufacturer's cached-app
     * freezer, which is what was delaying delivery by a minute and more on the
     * test device; see [AlarmWatchService]. Every path that changes the schedule
     * ends here, so "an alarm is armed" and "the watch is running" cannot drift
     * apart — a watch running with nothing to protect is a notification the user
     * did not earn, and an alarm armed with no watch is one that rings late.
     *
     * @param soonest a moment known to the caller that the repeating schedule
     *   does not describe — a snooze or a retry, which sit between occurrences.
     */
    private suspend fun refreshWatch(
        from: ZonedDateTime = ZonedDateTime.now(),
        soonest: ZonedDateTime? = null
    ) {
        runCatching {
            val upcoming = alarms.alarms()
                .filter { it.enabled }
                .mapNotNull { NextAlarmCalculator.next(it, from) }
            // minByOrNull over the instant rather than minOrNull: ZonedDateTime
            // is Comparable<ChronoZonedDateTime<*>>, not Comparable<ZonedDateTime>,
            // so minOrNull widens the result type and stops compiling here.
            scheduler.updateWatch(
                (upcoming + listOfNotNull(soonest)).minByOrNull { it.toInstant() }
            )
        }.onFailure { Log.w(TAG, "Could not update the alarm watch", it) }
    }

    private companion object {
        const val TAG = "NesaAlarmCoordinator"
    }
}
