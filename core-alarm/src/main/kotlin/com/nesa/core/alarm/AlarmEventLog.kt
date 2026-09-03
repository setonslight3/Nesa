package com.nesa.core.alarm

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A short, readable trace of what the alarm actually did.
 *
 * The alarm runs where nobody can watch it: in a receiver, in a service, on a
 * phone with no debugger attached and no way to read logcat. Three rounds of
 * this have now been diagnosed by reasoning from a description of the symptom,
 * and reasoning has been wrong as often as right.
 *
 * So the alarm writes down what happened, and the reliability screen shows it.
 * "The alarm did not ring" becomes a list of steps with times against them, and
 * whichever step is missing is the bug.
 *
 * SharedPreferences rather than the database, because this has to survive the
 * process being killed mid-alarm — which is the case most in need of a trace —
 * and must never itself be the thing that fails.
 */
@Singleton
class AlarmEventLog @Inject constructor(
    @ApplicationContext context: Context
) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("nesa_alarm_events", Context.MODE_PRIVATE)

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    /**
     * Appends one line. Never throws: a failure to record must not become a
     * failure to ring.
     */
    @Synchronized
    fun record(message: String) {
        runCatching {
            val stamped = "${formatter.format(Instant.now())}  $message"
            val kept = (recent() + stamped).takeLast(MAX_ENTRIES)
            preferences.edit().putString(KEY, kept.joinToString(SEPARATOR)).apply()
        }
    }

    /** Oldest first, so the trace reads downwards like a story. */
    fun recent(): List<String> =
        preferences.getString(KEY, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "events"
        const val SEPARATOR = "\n"
        const val MAX_ENTRIES = 60
    }
}
