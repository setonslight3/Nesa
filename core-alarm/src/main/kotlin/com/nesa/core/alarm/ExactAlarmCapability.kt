package com.nesa.core.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What NESA is currently allowed to do with alarms on this device. */
enum class AlarmCapability {
    /** Exact alarms are permitted: the alarm rings at the minute it should. */
    EXACT,

    /**
     * Only inexact alarms are permitted. NESA still wakes the user, but the
     * system may shift the time by a few minutes. The UI must say so rather
     * than promise precision it cannot deliver.
     */
    INEXACT_ONLY
}

/**
 * Android has tightened exact alarms repeatedly, and a permission that exists
 * today can be revoked tomorrow. Every scheduling path asks this class first and
 * has a working fallback, so the alarm degrades instead of disappearing.
 */
@Singleton
class ExactAlarmCapability @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val current: AlarmCapability
        get() {
            val manager = context.getSystemService<AlarmManager>() ?: return AlarmCapability.INEXACT_ONLY
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                AlarmCapability.EXACT
            } else {
                AlarmCapability.INEXACT_ONLY
            }
        }

    val isExact: Boolean get() = current == AlarmCapability.EXACT

    /**
     * The settings screen where the user can grant exact alarms, or null when
     * the platform has no such screen. NESA points at it; it never nags.
     */
    fun settingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
