package com.nesa.core.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.nesa.core.model.Alarm
import com.nesa.core.model.repository.AlarmRepository
import com.nesa.core.notifications.NesaNotifier
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rings the alarm.
 *
 * It runs as a foreground service so that Android does not stop it mid-ring,
 * holds a wake lock so the device does not fall back asleep, and fades the
 * volume in so waking up is a gradual thing rather than a shock.
 *
 * If nobody answers, it stops on its own and hands the alarm back to
 * [NesaAlarmCoordinator] for a retry, which is what makes "no response"
 * recoverable instead of a missed morning.
 */
@AndroidEntryPoint
class AlarmRingerService : Service() {

    @Inject lateinit var alarms: AlarmRepository
    @Inject lateinit var coordinator: NesaAlarmCoordinator
    @Inject lateinit var notifier: NesaNotifier
    @Inject lateinit var screenLauncher: AlarmScreenLauncher
    @Inject lateinit var audio: AlarmAudioPlayer
    @Inject lateinit var events: AlarmEventLog

    // Main-thread scope: the notification and the audio player are main-thread
    // affine, and the repository calls suspend onto their own dispatchers anyway.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutJob: Job? = null
    private var currentAlarmId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)

        // Android allows five seconds between startForegroundService and
        // startForeground, and kills the process if the deadline passes. So this
        // happens first, synchronously, on every path — including the ones that
        // only stop the alarm, which are still started as foreground services and
        // would otherwise crash. Nothing that touches the database comes before it.
        if (!promoteToForeground(alarmId)) {
            // The safety net the broadcast receiver used to provide: if the
            // service cannot become foreground, a full-screen notification is
            // still permitted and still reaches the user.
            if (alarmId != null) {
                events.record("could not ring — falling back to a notification")
                notifier.postRinger(null, fullScreenIntent(alarmId))
            }
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START -> if (alarmId != null) start(alarmId) else stopEverything()
            ACTION_SNOOZE -> if (alarmId != null) finish(alarmId, Outcome.SNOOZED) else stopEverything()
            ACTION_DISMISS -> if (alarmId != null) finish(alarmId, Outcome.DISMISSED) else stopEverything()
            ACTION_SLEEP_IN -> if (alarmId != null) finish(alarmId, Outcome.SLEEPING_IN) else stopEverything()
            else -> stopEverything()
        }
        // The alarm is only meaningful at the moment it fires; there is nothing
        // useful to restore if the process dies afterwards.
        return START_NOT_STICKY
    }

    /**
     * Becomes a foreground service immediately, with a placeholder label.
     *
     * The real label needs a database read, and waiting for one here is what
     * would blow the five-second deadline. The notification is refined once the
     * alarm loads.
     *
     * @return false when the platform refused the promotion, in which case the
     *   caller must stop rather than continue as a background service that
     *   Android will kill mid-ring.
     */
    private fun promoteToForeground(alarmId: String?): Boolean = try {
        startForeground(
            NesaNotifier.RINGER_NOTIFICATION_ID,
            notifier.buildRingerNotification(null, alarmId?.let(::fullScreenIntent))
        )
        events.record("ringer became a foreground service")
        true
    } catch (refused: IllegalStateException) {
        // Android 12+ throws ForegroundServiceStartNotAllowedException, a
        // subclass of IllegalStateException, when a background start is not
        // exempt. Caught by supertype so this compiles and runs on API 26 too.
        Log.w(TAG, "The platform refused to start the alarm in the foreground", refused)
        events.record("ringer REFUSED foreground — stopping")
        false
    }

    private fun start(alarmId: String) {
        currentAlarmId = alarmId
        scope.launch {
            val alarm = runCatching { alarms.find(alarmId) }.getOrNull()
            if (alarm == null) {
                // The alarm was deleted between being scheduled and firing.
                stopEverything()
                return@launch
            }

            // Now that the label is known, refine the notification already showing.
            notifier.postRinger(alarm.label, fullScreenIntent(alarmId))
            acquireWakeLock()
            audio.start(alarm)

            // With the overlay permission held this succeeds and the alarm takes
            // over the screen; without it Android silently refuses the launch and
            // only the notification remains.
            try {
                val ringIntent = screenLauncher.ringingIntent(this@AlarmRingerService, alarmId).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(ringIntent)
                events.record("alarm screen launched over the foreground")
            } catch (e: Exception) {
                Log.w(TAG, "Could not start AlarmRingActivity directly", e)
                events.record("could not launch the alarm screen: ${e.javaClass.simpleName}")
            }

            startUnansweredTimeout(alarm)
        }
    }

    /** Stops ringing if nobody answers, and asks for a retry. */
    private fun startUnansweredTimeout(alarm: Alarm) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(RING_TIMEOUT_MILLIS)
            finish(alarm.id, Outcome.UNANSWERED)
        }
    }

    private fun finish(alarmId: String, outcome: Outcome) {
        timeoutJob?.cancel()
        scope.launch {
            // Rearming must not be skipped because something else threw, or a
            // repeating alarm would silently stop repeating.
            events.record("alarm ${outcome.name.lowercase()}")
            runCatching {
                when (outcome) {
                    Outcome.SNOOZED -> coordinator.snooze(alarmId, ZonedDateTime.now())
                    Outcome.UNANSWERED -> coordinator.retryAfterSilence(alarmId, ZonedDateTime.now())
                    Outcome.DISMISSED, Outcome.SLEEPING_IN -> coordinator.dismiss(alarmId)
                }
            }.onFailure { Log.w(TAG, "Could not settle alarm $alarmId after $outcome", it) }
            stopEverything()
        }
    }

    /** Stops the sound, drops the notification, and leaves the foreground. */
    private fun stopEverything() {
        stopRinging()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRinging() {
        audio.stop()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val power = getSystemService<PowerManager>() ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(RING_TIMEOUT_MILLIS + WAKE_LOCK_SLACK_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
    }

    private fun fullScreenIntent(alarmId: String): PendingIntent? = try {
        PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            screenLauncher.ringingIntent(this, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } catch (error: Exception) {
        Log.w(TAG, "Could not build the full-screen alarm intent", error)
        null
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    private enum class Outcome { SNOOZED, DISMISSED, SLEEPING_IN, UNANSWERED }

    companion object {
        const val ACTION_START = "com.nesa.action.RING_START"
        const val ACTION_SNOOZE = "com.nesa.action.RING_SNOOZE"
        const val ACTION_DISMISS = "com.nesa.action.RING_DISMISS"
        const val ACTION_SLEEP_IN = "com.nesa.action.RING_SLEEP_IN"

        private const val TAG = "NesaAlarmRinger"
        private const val RING_TIMEOUT_MILLIS = 2 * 60 * 1000L
        private const val WAKE_LOCK_SLACK_MILLIS = 30 * 1000L
        private const val WAKE_LOCK_TAG = "nesa:alarm-ringer"

        /** Builds the intent a screen sends back to stop or postpone the alarm. */
        fun command(context: Context, action: String, alarmId: String): Intent =
            Intent(context, AlarmRingerService::class.java).apply {
                this.action = action
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            }
    }
}
