package com.nesa.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nesa.core.alarm.DayPlanWorker
import com.nesa.core.alarm.NesaAlarmCoordinator
import com.nesa.core.alarm.AlarmEventLog
import com.nesa.core.alarm.ReminderScheduler
import com.nesa.core.notifications.NesaChannels
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Application start-up.
 *
 * Two things have to happen before any screen exists: the notification channels
 * must be registered, and the alarms must be re-armed. Re-arming on every launch
 * is deliberate belt-and-braces — the boot receiver normally handles it, but
 * some manufacturers restrict boot broadcasts, and an alarm that quietly stops
 * working is the one failure NESA cannot afford.
 */
@HiltAndroidApp
class NesaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var alarmCoordinator: NesaAlarmCoordinator
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var events: AlarmEventLog

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NesaChannels.ensureCreated(this)
        // A line here means the process had died and been recreated. Its absence
        // across an alarm means the process merely slept — different problems
        // with different answers, and until now indistinguishable.
        events.record("app process started")

        startupScope.launch {
            runCatching { alarmCoordinator.rearmAll() }
            // Reminders too: re-deriving only the alarms left today's reminders
            // waiting on the half-hourly worker after every cold start.
            runCatching { reminders.scheduleFor(LocalDate.now()) }
            DayPlanWorker.enqueuePeriodic(this@NesaApplication)

        }
    }
}
