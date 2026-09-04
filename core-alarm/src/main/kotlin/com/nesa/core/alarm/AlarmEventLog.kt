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
 * phone with no debugger attached and no way to read logcat. Several rounds of
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
 *
 * The writing half is deliberately available as a plain static call as well as
 * an injected object. A broadcast receiver has to be able to record the instant
 * it was woken *before* anything else runs, and "anything else" includes
 * building the dependency graph — otherwise the trace cannot distinguish
 * "Android delivered late" from "our own start-up was slow", which is exactly
 * the distinction this whole log exists to make.
 */
@Singleton
class AlarmEventLog @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Appends one line. Never throws: a failure to record must not become a
     * failure to ring.
     */
    fun record(message: String) = write(context, message)

    /** Oldest first, so the trace reads downwards like a story. */
    fun recent(): List<String> = recent(context)

    fun clear() {
        runCatching { preferences(context).edit().remove(KEY).apply() }
    }

    companion object {
        private const val PREFS = "nesa_alarm_events"
        private const val KEY = "events"
        private const val SEPARATOR = "\n"
        private const val MAX_ENTRIES = 60

        /** Writes are cross-process-visible but not cross-process-atomic; this
         *  at least keeps the receiver and the ringer service from clobbering
         *  each other within the one process they both run in. */
        private val lock = Any()

        private val formatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

        private fun preferences(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /**
         * The no-dependencies form, safe to call as the first statement of a
         * broadcast receiver.
         */
        fun write(context: Context, message: String) {
            synchronized(lock) {
                runCatching {
                    val stamped = "${formatter.format(Instant.now())}  $message"
                    val kept = (recent(context) + stamped).takeLast(MAX_ENTRIES)
                    preferences(context).edit()
                        .putString(KEY, kept.joinToString(SEPARATOR))
                        // commit, not apply: a receiver can be torn down the
                        // moment onReceive returns, and an unflushed trace of the
                        // alarm that failed is the one we most need to keep.
                        .commit()
                }
            }
        }

        fun recent(context: Context): List<String> =
            runCatching {
                preferences(context).getString(KEY, null)
                    ?.split(SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            }.getOrDefault(emptyList())
    }
}
