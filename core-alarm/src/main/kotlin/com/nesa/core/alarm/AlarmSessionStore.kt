package com.nesa.core.alarm

import android.content.Context
import android.content.SharedPreferences
import com.nesa.core.model.AlarmSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts how many times the user has snoozed, and how many times NESA has
 * retried, for the alarm that is ringing right now.
 *
 * It is persisted rather than held in memory because the ringing service can be
 * killed and restarted mid-morning, and a snooze limit that resets itself on
 * process death is not a limit.
 */
@Singleton
class AlarmSessionStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("nesa_alarm_sessions", Context.MODE_PRIVATE)

    fun session(alarmId: String): AlarmSession = AlarmSession(
        alarmId = alarmId,
        snoozeCount = preferences.getInt(key(alarmId, SNOOZES), 0),
        autoRetryCount = preferences.getInt(key(alarmId, RETRIES), 0),
        firstFiredAtEpochMillis = preferences.getLong(key(alarmId, FIRST_FIRED), 0L)
    )

    fun recordSnooze(alarmId: String): AlarmSession {
        val next = session(alarmId).copy(snoozeCount = session(alarmId).snoozeCount + 1)
        preferences.edit().putInt(key(alarmId, SNOOZES), next.snoozeCount).apply()
        return next
    }

    fun recordRetry(alarmId: String): AlarmSession {
        val next = session(alarmId).copy(autoRetryCount = session(alarmId).autoRetryCount + 1)
        preferences.edit().putInt(key(alarmId, RETRIES), next.autoRetryCount).apply()
        return next
    }

    fun markFired(alarmId: String, epochMillis: Long) {
        if (preferences.getLong(key(alarmId, FIRST_FIRED), 0L) == 0L) {
            preferences.edit().putLong(key(alarmId, FIRST_FIRED), epochMillis).apply()
        }
    }

    /** Called once the alarm is genuinely done for the day. */
    fun clear(alarmId: String) {
        preferences.edit()
            .remove(key(alarmId, SNOOZES))
            .remove(key(alarmId, RETRIES))
            .remove(key(alarmId, FIRST_FIRED))
            .apply()
    }

    private fun key(alarmId: String, suffix: String) = "$alarmId.$suffix"

    private companion object {
        const val SNOOZES = "snoozes"
        const val RETRIES = "retries"
        const val FIRST_FIRED = "first_fired"
    }
}
