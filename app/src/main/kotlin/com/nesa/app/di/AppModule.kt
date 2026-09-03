package com.nesa.app.di

import android.content.Context
import android.content.Intent
import com.nesa.core.alarm.AlarmScreenLauncher
import com.nesa.core.alarm.NesaAlarmCoordinator
import com.nesa.core.alarm.ReminderPlanChangeListener
import com.nesa.core.model.Alarm
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.scheduling.ActivityActionHandler
import com.nesa.core.scheduling.DayPlanner
import com.nesa.feature.alarm.AlarmRingActivity
import com.nesa.feature.onboarding.OnboardingAlarmSetup
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The composition root.
 *
 * The domain use cases are plain classes with no framework annotations, so this
 * is where they get their clock and their id source. Keeping that decision here
 * is what lets the same classes be constructed with a fixed clock in tests.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideDayPlanner(
        activities: ActivityRepository,
        history: HistoryRepository,
        settings: SettingsRepository,
        clock: Clock,
        onPlanChanged: ReminderPlanChangeListener
    ): DayPlanner = DayPlanner(
        activities = activities,
        history = history,
        settings = settings,
        clock = clock,
        idFactory = { UUID.randomUUID().toString() },
        // Arms reminders as soon as the plan changes, rather than leaving it to
        // the half-hourly worker.
        onPlanChanged = onPlanChanged
    )

    @Provides
    @Singleton
    fun provideActivityActionHandler(
        activities: ActivityRepository,
        history: HistoryRepository,
        clock: Clock
    ): ActivityActionHandler = ActivityActionHandler(
        activities = activities,
        history = history,
        clock = clock,
        idFactory = { UUID.randomUUID().toString() }
    )
}

/**
 * Ports the lower layers declare and only the application can satisfy, because
 * only the application knows about every module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PortsModule {

    @Binds
    @Singleton
    abstract fun bindAlarmScreenLauncher(impl: NesaAlarmScreenLauncher): AlarmScreenLauncher

    @Binds
    @Singleton
    abstract fun bindOnboardingAlarmSetup(impl: NesaOnboardingAlarmSetup): OnboardingAlarmSetup
}

/** Lets the alarm layer open a screen it must not depend on. */
@Singleton
class NesaAlarmScreenLauncher @Inject constructor() : AlarmScreenLauncher {
    override fun ringingIntent(context: Context, alarmId: String): Intent =
        AlarmRingActivity.intent(context, alarmId)
}

/**
 * Creates the wake alarm onboarding offers.
 *
 * It goes through the coordinator rather than the repository so the alarm is
 * armed as well as saved — a stored alarm that never rings would be worse than
 * not offering one at all.
 */
@Singleton
class NesaOnboardingAlarmSetup @Inject constructor(
    private val coordinator: NesaAlarmCoordinator,
    private val settings: SettingsRepository
) : OnboardingAlarmSetup {

    override suspend fun createDailyWakeAlarm(time: LocalTime): Alarm {
        val alarm = Alarm(
            id = UUID.randomUUID().toString(),
            time = time,
            days = DayOfWeek.values().toSet(),
            enabled = true
        )
        coordinator.save(alarm)
        settings.setPrimaryAlarmId(alarm.id)
        return alarm
    }
}
