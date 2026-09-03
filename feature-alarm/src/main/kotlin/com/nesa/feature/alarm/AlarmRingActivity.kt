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
import com.nesa.core.alarm.AlarmReceiver
import com.nesa.core.alarm.AlarmRingerService
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.ui.theme.NesaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.app.KeyguardManager
import androidx.core.content.getSystemService
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
                AlarmRingScreen(onOutcome = { outcome -> handle(outcome, alarmId) })
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
        ContextCompat.startForegroundService(
            this,
            AlarmRingerService.command(this, action, alarmId)
        )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguard = getSystemService<KeyguardManager>()
            keyguard?.requestDismissKeyguard(this, null)
        }
    }

    companion object {
        fun intent(context: Context, alarmId: String): Intent =
            Intent(context, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            }
    }
}
