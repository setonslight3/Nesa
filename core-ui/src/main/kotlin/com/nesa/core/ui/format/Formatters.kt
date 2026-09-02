package com.nesa.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nesa.core.model.ActivityState
import com.nesa.core.model.DayCycle
import com.nesa.core.model.Flexibility
import com.nesa.core.model.GuidancePersonality
import com.nesa.core.model.Priority
import com.nesa.core.ui.R
import com.nesa.core.ui.theme.LocalNesaSemanticColors
import androidx.compose.runtime.ReadOnlyComposable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The single place domain values are turned into words and colours.
 *
 * Every label comes from a string resource, so translating NESA later is a
 * matter of adding a values folder rather than finding text buried in screens.
 */

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Respects the device's 12/24-hour preference rather than imposing one. */
fun LocalTime.formatted(): String = format(timeFormatter)

@Composable
@ReadOnlyComposable
fun ActivityState.label(): String = stringResource(
    when (this) {
        ActivityState.UPCOMING -> R.string.nesa_state_upcoming
        ActivityState.ACTIVE -> R.string.nesa_state_active
        ActivityState.COMPLETED -> R.string.nesa_state_completed
        ActivityState.LATER -> R.string.nesa_state_later
        ActivityState.SKIPPED -> R.string.nesa_state_skipped
        ActivityState.MISSED -> R.string.nesa_state_missed
        ActivityState.CANCELLED -> R.string.nesa_state_cancelled
    }
)

@Composable
fun ActivityState.color(): Color {
    val colors = LocalNesaSemanticColors.current
    return when (this) {
        ActivityState.UPCOMING -> colors.upcoming
        ActivityState.ACTIVE -> colors.active
        ActivityState.COMPLETED -> colors.completed
        ActivityState.LATER -> colors.later
        ActivityState.SKIPPED -> colors.skipped
        ActivityState.MISSED -> colors.missed
        ActivityState.CANCELLED -> colors.cancelled
    }
}

@Composable
@ReadOnlyComposable
fun DayCycle.label(): String = stringResource(
    when (this) {
        DayCycle.MORNING -> R.string.nesa_cycle_morning
        DayCycle.DAY -> R.string.nesa_cycle_day
        DayCycle.EVENING -> R.string.nesa_cycle_evening
        DayCycle.NIGHT -> R.string.nesa_cycle_night
    }
)

@Composable
@ReadOnlyComposable
fun Priority.label(): String = stringResource(
    when (this) {
        Priority.CRITICAL -> R.string.nesa_priority_critical
        Priority.HIGH -> R.string.nesa_priority_high
        Priority.NORMAL -> R.string.nesa_priority_normal
        Priority.LOW -> R.string.nesa_priority_low
    }
)

@Composable
@ReadOnlyComposable
fun Flexibility.label(): String = stringResource(
    when (this) {
        Flexibility.FIXED -> R.string.nesa_flexibility_fixed
        Flexibility.TIME_FLEXIBLE -> R.string.nesa_flexibility_time
        Flexibility.DAY_FLEXIBLE -> R.string.nesa_flexibility_day
        Flexibility.OPTIONAL -> R.string.nesa_flexibility_optional
        Flexibility.DEADLINE_BASED -> R.string.nesa_flexibility_deadline
    }
)

@Composable
@ReadOnlyComposable
fun GuidancePersonality.label(): String = stringResource(
    when (this) {
        GuidancePersonality.GENTLE -> R.string.nesa_guidance_gentle
        GuidancePersonality.BALANCED -> R.string.nesa_guidance_balanced
        GuidancePersonality.PERSISTENT -> R.string.nesa_guidance_persistent
        GuidancePersonality.STRICT -> R.string.nesa_guidance_strict
    }
)

/** "1h 30m", "45m", "2h" — short enough to sit inside a card. */
@Composable
@ReadOnlyComposable
fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours > 0 && remainder > 0 -> stringResource(R.string.nesa_duration_hours_minutes, hours, remainder)
        hours > 0 -> stringResource(R.string.nesa_duration_hours, hours)
        else -> stringResource(R.string.nesa_duration_minutes, remainder)
    }
}

@Composable
@ReadOnlyComposable
fun formatTimeRange(start: LocalTime, end: LocalTime): String =
    stringResource(R.string.nesa_time_range, start.formatted(), end.formatted())
