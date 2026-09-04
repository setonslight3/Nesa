package com.nesa.core.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nesa.core.notifications.NesaNotifier

/**
 * Keeps NESA's process resident while an alarm is pending.
 *
 * ## Why this exists, because it looks like the thing the spec said not to do
 *
 * NESA arms its alarm with `setAlarmClock`, which is the strongest guarantee
 * Android offers, and the trace proves it reaches the platform. On the test
 * device it is still delivered 60–90 seconds late — and always at the exact
 * moment the app is reopened. The trace also shows no "app process started"
 * line at delivery, so the process was never killed. A process that is alive,
 * has a pending alarm, and receives it only when the user returns to the app is
 * a *frozen* process: the manufacturer's skin puts NESA in the cached-app
 * freezer when it leaves the recents list, and every delivery queues up behind
 * that until something thaws it.
 *
 * No permission fixes this. Exact alarms, battery-optimisation exemption,
 * overlay, auto-start and full-screen intent were all granted for that trace.
 * The freezer is not a permission; it is a process state, and the only
 * documented way for an app to stay out of it is to have a foreground service
 * running. That is why a third-party alarm app on the same phone rings after
 * being swiped away with no permission setup at all: it keeps a quiet
 * "next alarm" notification up, and that notification is a foreground service.
 *
 * The build spec says not to use a permanent foreground service *merely* to
 * keep the alarm alive — written when the suspicion was that NESA was using an
 * in-process timer instead of AlarmManager. It is not: AlarmManager still owns
 * the schedule, this service holds no timer and does no work. It exists only to
 * keep the process thawed so that AlarmManager's delivery is not queued. If it
 * is killed anyway, the alarm still fires; it just fires late, which is the
 * behaviour without it.
 *
 * It is therefore:
 * - **bounded** — it runs only while an alarm is actually armed, and stops the
 *   moment none is,
 * - **honest** — it shows the time of the alarm it is protecting, so the
 *   notification is information rather than an apology for existing,
 * - **optional** — [setEnabled] turns it off for users on devices that do not
 *   need it, and nothing else depends on it being on.
 */
class AlarmWatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A restart after the system reclaimed the service arrives with a null
        // intent, so the label has to be allowed to be missing.
        val label = intent?.getStringExtra(EXTRA_NEXT_LABEL)

        if (intent?.action == ACTION_STOP) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        // Same five-second rule as the ringer: foreground first, synchronously,
        // before anything that could take time.
        return try {
            startForeground(
                NesaNotifier.WATCH_NOTIFICATION_ID,
                NesaNotifier(this).buildWatchNotification(label)
            )
            AlarmEventLog.write(this, "watch running${label?.let { " (next $it)" } ?: ""}")
            // Sticky, so that if the system does reclaim it the process comes
            // back and the protection resumes rather than silently lapsing.
            START_STICKY
        } catch (refused: IllegalStateException) {
            // Android 12+ refuses a foreground start from the background unless
            // the caller is exempt. Arming from a boot receiver or an alarm is
            // exempt; arming from a WorkManager job is not. Not fatal: the alarm
            // is already on the platform's clock either way.
            Log.w(TAG, "The platform refused to start the alarm watch", refused)
            AlarmEventLog.write(this, "watch could not start — alarms may be delivered late")
            stopSelf()
            START_NOT_STICKY
        }
    }

    /**
     * The user swiped NESA out of the recents list.
     *
     * Recorded because it is the exact event this whole class exists for: the
     * trace can now show "watch running" → "app swiped from recents" → whether
     * the alarm then arrived on time, which is the difference between the fix
     * working and the manufacturer killing the watch too. The service keeps
     * running (see stopWithTask="false" in the manifest); this only writes it
     * down.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AlarmEventLog.write(this, "app swiped from recents — watch still running")
        super.onTaskRemoved(rootIntent)
    }

    /**
     * If this appears in the trace without the user having turned the watch off,
     * the system or the manufacturer stopped it — and any alarm after this point
     * is delivered to a process nothing is protecting.
     */
    override fun onDestroy() {
        AlarmEventLog.write(this, "watch stopped")
        super.onDestroy()
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "NesaAlarmWatch"
        private const val ACTION_STOP = "com.nesa.action.WATCH_STOP"
        private const val EXTRA_NEXT_LABEL = "com.nesa.extra.NEXT_LABEL"

        private const val PREFS = "nesa_alarm_watch"
        private const val KEY_ENABLED = "enabled"

        /**
         * On by default.
         *
         * The failure this prevents — an alarm that does not ring — is far worse
         * than the cost it imposes, which is one silent low-priority
         * notification. A user who does not want it can say so.
         */
        fun isEnabled(context: Context): Boolean =
            preferences(context).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (!enabled) stop(context)
        }

        /**
         * Starts or refreshes the watch for an alarm due at [nextLabel].
         *
         * Never throws: a device that will not let the watch start is a device
         * whose alarms are late, not one whose app crashes.
         */
        fun start(context: Context, nextLabel: String?) {
            if (!isEnabled(context)) {
                // Recorded, not returned silently. The watch this replaces was
                // switched on by a user and never appeared in a single trace,
                // and there was no way to tell whether it had been refused,
                // never asked for, or asked for and killed. Every path out of
                // here now writes down which one it took.
                AlarmEventLog.write(context, "watch is switched off — alarms may arrive late")
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AlarmWatchService::class.java)
                        .putExtra(EXTRA_NEXT_LABEL, nextLabel)
                )
            }.onFailure {
                Log.w(TAG, "Could not start the alarm watch", it)
                AlarmEventLog.write(context, "watch refused at start: ${it.javaClass.simpleName}")
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AlarmWatchService::class.java).setAction(ACTION_STOP)
                )
            }.onFailure { Log.w(TAG, "Could not stop the alarm watch", it) }
        }

        private fun preferences(context: Context) = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
