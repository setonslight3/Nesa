package com.nesa.core.alarm

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.nesa.core.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands NESA's wake time to the phone's own clock app.
 *
 * Some phones will not let any third-party app run in the background reliably
 * enough to be trusted with a morning. The clock app that shipped with the phone
 * is exempt from all of it — the manufacturer whitelists its own alarm — so on a
 * device like that, the honest answer is to let the thing that works do the
 * waking.
 *
 * `ACTION_SET_ALARM` is a documented public API and works on any phone with a
 * clock app that handles it, which in practice is all of them.
 *
 * What is lost is real and worth stating plainly: the alarm becomes the clock
 * app's, so NESA's wake challenge does not run, and NESA cannot later change or
 * remove an alarm it created — Android exposes no API for that. This is
 * therefore offered as a deliberate handoff the user performs, not a silent sync
 * that would quietly fill their clock app with duplicates.
 */
@Singleton
class SystemAlarmHandoff @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** False when nothing on this phone handles the alarm intents. */
    val isAvailable: Boolean
        get() = Intent(AlarmClock.ACTION_SET_ALARM)
            .resolveActivity(context.packageManager) != null

    /**
     * Creates [alarm] in the clock app.
     *
     * `EXTRA_SKIP_UI` asks the clock app to save it without making the user
     * confirm; a clock app that ignores the hint shows its own editor, which is
     * still a reasonable outcome.
     */
    fun createIntent(alarm: Alarm): Intent =
        Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_HOUR, alarm.time.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.time.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, alarm.label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_VIBRATE, alarm.vibrate)
            if (alarm.repeats) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(alarm.days.map(::toCalendarDay)))
            }
        }

    /** The clock app's alarm list, for reviewing or removing what was created. */
    fun showAlarmsIntent(): Intent =
        Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * java.time counts Monday as 1; Calendar counts Sunday as 1. The clock app
     * speaks Calendar.
     */
    private fun toCalendarDay(day: DayOfWeek): Int = when (day) {
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
    }
}
