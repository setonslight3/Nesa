package com.nesa.core.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.getSystemService
import com.nesa.core.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Makes the alarm noise, for whoever can.
 *
 * This used to live inside [AlarmRingerService], which meant the sound existed
 * only if that service started. On phones that refuse to start a foreground
 * service from the background — Infinix, Tecno and several others freeze an app
 * the moment it leaves the screen — the alarm fell back to a notification, and
 * that notification is deliberately silent because the service was supposed to
 * be doing the ringing. The result was an alarm that appeared and said nothing.
 *
 * Extracting it means the ringing screen can play the alarm too. An activity in
 * the foreground has none of the restrictions a background service does, so
 * whichever of the two gets there first wins and the other is a no-op.
 *
 * Being a singleton is what makes that safe: there is one player, so it can
 * never double up.
 */
@Singleton
class AlarmAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val events: AlarmEventLog
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var fadeJob: Job? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    /**
     * Starts ringing, unless something already is.
     *
     * Safe to call from the service and the screen without coordination between
     * them: the second call does nothing.
     */
    @Synchronized
    fun start(alarm: Alarm) {
        // Guard on a player that genuinely exists, not on an intent to make one.
        // Setting the flag up front meant that when the service tried first and
        // every sound source failed, the flag stayed true with no player behind
        // it — and the ringing screen's attempt, the one that would have worked,
        // returned immediately. The alarm was then silent for good.
        if (player != null) return

        // Walk the candidates in turn. A phone with no alarm sound configured
        // must not end up with a silent alarm.
        val candidates = listOfNotNull(
            alarm.soundUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            Settings.System.DEFAULT_ALARM_ALERT_URI
        )

        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            // USAGE_ALARM is what plays at alarm volume and gets
                            // through Do Not Disturb.
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    isLooping = true
                    setVolume(START_VOLUME, START_VOLUME)
                    prepare()
                    start()
                }
                fadeIn(alarm.fadeInSeconds)
                break
            } catch (error: Exception) {
                Log.w(TAG, "Could not play $uri; trying the next source", error)
                player?.runCatching { release() }
                player = null
            }
        }

        isPlaying = player != null
        if (player == null) {
            // Left false on purpose, so whoever tries next is allowed to.
            Log.w(TAG, "No alarm sound could be played; vibration only")
            events.record("audio: no source could be played")
        } else {
            events.record("audio: playing")
        }
        if (alarm.vibrate) startVibration()
    }

    @Synchronized
    fun stop() {
        fadeJob?.cancel()
        fadeJob = null
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
        stopVibration()
        isPlaying = false
    }

    /** Starts audible rather than silent, then rises. A fade from zero wastes the first seconds. */
    private fun fadeIn(seconds: Int) {
        fadeJob?.cancel()
        val steps = (seconds.coerceIn(0, 120) * FADE_STEPS_PER_SECOND).coerceAtLeast(1)
        fadeJob = scope.launch {
            repeat(steps) { step ->
                // Perceived loudness tracks roughly the square of amplitude, so a
                // squared ramp sounds like an even rise.
                val eased = ((step + 1).toFloat() / steps).pow(2)
                val volume = START_VOLUME + (1f - START_VOLUME) * eased
                player?.runCatching { setVolume(volume, volume) }
                delay(1_000L / FADE_STEPS_PER_SECOND)
            }
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 800)
        vibrator()?.runCatching { vibrate(VibrationEffect.createWaveform(pattern, 0)) }
    }

    private fun stopVibration() {
        vibrator()?.runCatching { cancel() }
    }

    private companion object {
        const val TAG = "NesaAlarmAudio"
        const val FADE_STEPS_PER_SECOND = 4
        const val START_VOLUME = 0.25f
    }
}
