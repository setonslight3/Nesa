package com.nesa.feature.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nesa.core.alarm.AlarmAudioPlayer
import com.nesa.core.alarm.AlarmReceiver
import com.nesa.core.alarm.AlarmRingerService
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.ui.theme.NesaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The full-screen alarm.
 *
 * It has to appear over the lock screen and turn the display on, which is what
 * the window flags below are for. Those flags are the reason this is a separate
 * activity rather than a route inside the main one — and why the alarm layer
 * reaches it through [com.nesa.core.alarm.AlarmScreenLauncher] rather than
 * depending on this module.
 */
@AndroidEntryPoint
class AlarmRingActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    /**
     * The screen plays the alarm as well as the service does.
     *
     * On phones that refuse to start a foreground service from the background,
     * the service never runs and the notification that replaces it is silent —
     * so this screen, launched by the full-screen intent, is the only thing left
     * that can make a noise. The player is a singleton and ignores a second
     * start, so when the service did run this changes nothing.
     */
    @Inject lateinit var audio: AlarmAudioPlayer

    private val preferences = MutableStateFlow(NesaSettings.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)
        if (alarmId == null) {
            finish()
            return
        }

        // The theme is read asynchronously so a slow first disk read can never
        // delay the alarm appearing; it starts on the default and settles.
        lifecycleScope.launch {
            runCatching { settings.settings.first() }.onSuccess { preferences.value = it }
        }

        setContent {
            val current by preferences.collectAsStateWithLifecycle()
            NesaTheme(themeMode = current.themeMode) {
                AlarmRingScreen(
                    onAlarmLoaded = { alarm -> audio.start(alarm) },
                    onOutcome = { outcome -> handle(outcome, alarmId) }
                )
            }
        }
    }

    /** Tells the ringer what the user decided, then gets out of the way. */
    private fun handle(outcome: RingOutcome, alarmId: String) {
        val action = when (outcome) {
            RingOutcome.DISMISS -> AlarmRingerService.ACTION_DISMISS
            RingOutcome.SNOOZE -> AlarmRingerService.ACTION_SNOOZE
            RingOutcome.SLEEP_IN -> AlarmRingerService.ACTION_SLEEP_IN
            RingOutcome.NONE -> return
        }
        // Stop the sound here rather than relying on the service, which on a
        // restricted phone was never running to be told.
        audio.stop()
        runCatching {
            ContextCompat.startForegroundService(
                this,
                AlarmRingerService.command(this, action, alarmId)
            )
        }
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        // Deliberately NOT calling KeyguardManager.requestDismissKeyguard here.
        // setShowWhenLocked already puts this screen over the lock screen, where
        // the user can answer the alarm without unlocking. requestDismissKeyguard
        // instead raises the PIN or pattern prompt on a secure device, which puts
        // an authentication step between a half-asleep person and silencing their
        // alarm. Showing over the keyguard is what an alarm clock should do.
    }

    companion object {
        fun intent(context: Context, alarmId: String): Intent =
            Intent(context, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            }
    }
}
