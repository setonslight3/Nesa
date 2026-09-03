package com.nesa.core.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.nesa.core.notifications.NesaNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that has to be true for NESA to wake somebody up, and whether it
 * currently is.
 *
 * An alarm that does not ring is the worst failure this application has, and the
 * usual cause is not a bug — it is Android or the manufacturer declining to run
 * NESA in the background. That is invisible unless the app says so, which is why
 * this exists: the user gets told which permission is missing and taken straight
 * to it, instead of concluding the alarm is broken.
 */
data class ReliabilityStatus(
    val exactAlarmsAllowed: Boolean,
    val ignoringBatteryOptimisations: Boolean,
    val notificationsAllowed: Boolean,
    /** Whether the platform is still holding NESA's alarm right now. */
    val alarmArmed: Boolean = false,
    /** The next alarm clock the system knows about, from any app. */
    val nextSystemAlarmMillis: Long? = null
) {
    /** True when no permission is standing in the way of a dependable alarm. */
    val isFullyReliable: Boolean
        get() = exactAlarmsAllowed && ignoringBatteryOptimisations && notificationsAllowed

    /**
     * Every permission is granted but the platform is not holding the alarm.
     *
     * This is the signature of a device that accepted the alarm and then threw
     * it away — usually because the app was swiped out of recents on a skin that
     * treats that as a force stop.
     */
    val silentlyDropped: Boolean
        get() = isFullyReliable && !alarmArmed
}

@Singleton
class BackgroundReliability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exactAlarms: ExactAlarmCapability,
    private val notifier: NesaNotifier,
    private val coordinator: NesaAlarmCoordinator,
    private val events: AlarmEventLog
) {

    /** The alarm's own account of what it did, oldest first. */
    fun recentEvents(): List<String> = events.recent()

    fun clearEvents() = events.clear()

    suspend fun status(): ReliabilityStatus = ReliabilityStatus(
        exactAlarmsAllowed = exactAlarms.isExact,
        ignoringBatteryOptimisations = isIgnoringBatteryOptimisations(),
        notificationsAllowed = notifier.enabled,
        alarmArmed = coordinator.isPrimaryAlarmArmed(),
        nextSystemAlarmMillis = coordinator.nextSystemAlarmClockMillis()
    )

    /** Arms the real alarm a minute out, through the real path. */
    suspend fun runAlarmTest(): Long? =
        coordinator.armTestAlarm()?.toInstant()?.toEpochMilli()

    /**
     * True when Android has been told to leave NESA alone in the background.
     *
     * Without this, Doze and App Standby can defer everything NESA schedules —
     * which is exactly the "nothing happens until I open the app" symptom.
     */
    fun isIgnoringBatteryOptimisations(): Boolean {
        val power = context.getSystemService<PowerManager>() ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The system prompt asking to be exempt from battery optimisation.
     *
     * This is the dialog the user expects to see from an alarm app and never
     * saw, because NESA never asked. Waking the user at a chosen time is one of
     * the uses this exemption exists for.
     */
    fun batteryOptimisationRequest(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    fun exactAlarmSettings(): Intent? = exactAlarms.settingsIntent()

    fun notificationSettings(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    /**
     * NESA's own page in system settings.
     *
     * Several manufacturers — ColorOS, MIUI, EMUI and others — add an
     * "auto-start" or "auto-launch" permission with no public API and no way to
     * detect it. Without it those systems silently drop the broadcasts that fire
     * an alarm. All any app can do is bring the user here and say what to look
     * for, which is why the reliability screen spells it out in words.
     */
    fun appDetailsSettings(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
}
