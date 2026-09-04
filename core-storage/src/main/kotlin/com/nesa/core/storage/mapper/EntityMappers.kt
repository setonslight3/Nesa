package com.nesa.core.storage.mapper

import com.nesa.core.model.Activity
import com.nesa.core.model.ActivityState
import com.nesa.core.model.Alarm
import com.nesa.core.model.ChallengeDifficulty
import com.nesa.core.model.ChangeReasonCodec
import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Flexibility
import com.nesa.core.model.Goal
import com.nesa.core.model.GoalCategory
import com.nesa.core.model.GoalStatus
import com.nesa.core.model.NesaModule
import com.nesa.core.model.Priority
import com.nesa.core.model.ScheduleBlock
import com.nesa.core.model.SnoozePolicy
import com.nesa.core.model.WakeChallengePolicy
import com.nesa.core.model.WakeChallengeResult
import com.nesa.core.model.WakeChallengeType
import com.nesa.core.storage.entity.ActivityEntity
import com.nesa.core.storage.entity.AlarmEntity
import com.nesa.core.storage.entity.CompletionRecordEntity
import com.nesa.core.storage.entity.GoalEntity
import com.nesa.core.storage.entity.ScheduleBlockEntity
import com.nesa.core.storage.entity.WakeChallengeResultEntity
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The single translation point between stored rows and domain types.
 *
 * Enum values are stored by name and read back defensively: a row written by a
 * newer build that adds an enum constant falls back to a safe default instead of
 * crashing the app on launch.
 */

private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

// --- Activity ---------------------------------------------------------------

fun ActivityEntity.toDomain(): Activity = Activity(
    id = id,
    title = title,
    notes = notes,
    module = module.toEnum(NesaModule.CORE),
    duration = Duration.ofMinutes(durationMinutes.toLong().coerceAtLeast(1L)),
    priority = priority.toEnum(Priority.NORMAL),
    flexibility = flexibility.toEnum(Flexibility.TIME_FLEXIBLE),
    preferredStart = preferredStartMinute?.let(DayWindow::timeOf),
    deadline = deadline?.let(LocalDateTime::parse),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
)

fun Activity.toEntity(): ActivityEntity = ActivityEntity(
    id = id,
    title = title,
    notes = notes,
    module = module.name,
    durationMinutes = durationMinutes,
    priority = priority.name,
    flexibility = flexibility.name,
    preferredStartMinute = preferredStart?.let(DayWindow::minuteOf),
    deadline = deadline?.toString(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)

// --- Schedule block ---------------------------------------------------------

fun ScheduleBlockEntity.toDomain(): ScheduleBlock = ScheduleBlock(
    id = id,
    activityId = activityId,
    date = LocalDate.parse(date),
    start = DayWindow.timeOf(startMinute),
    end = DayWindow.timeOf(endMinute),
    state = state.toEnum(ActivityState.UPCOMING),
    locked = locked,
    changeReason = ChangeReasonCodec.decode(changeReason),
    remindersSent = remindersSent
)

fun ScheduleBlock.toEntity(): ScheduleBlockEntity = ScheduleBlockEntity(
    id = id,
    activityId = activityId,
    date = date.toString(),
    startMinute = startMinuteOfDay,
    endMinute = endMinuteOfDay,
    state = state.name,
    locked = locked,
    changeReason = ChangeReasonCodec.encode(changeReason),
    remindersSent = remindersSent
)

// --- Goal -------------------------------------------------------------------

fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    title = title,
    category = category.toEnum(GoalCategory.CUSTOM),
    status = status.toEnum(GoalStatus.ACTIVE),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis)
)

fun Goal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    title = title,
    category = category.name,
    status = status.name,
    createdAtEpochMillis = createdAt.toEpochMilli()
)

// --- Alarm ------------------------------------------------------------------

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    label = label,
    time = DayWindow.timeOf(timeMinute),
    days = days.split(',')
        .mapNotNull { name -> DayOfWeek.values().firstOrNull { it.name == name.trim() } }
        .toSet(),
    enabled = enabled,
    challenge = WakeChallengePolicy(
        type = challengeType.toEnum(WakeChallengeType.Default),
        difficulty = challengeDifficulty.toEnum(ChallengeDifficulty.Default),
        adaptive = challengeAdaptive,
        required = challengeRequired
    ),
    snooze = SnoozePolicy(
        snoozeMinutes = snoozeMinutes,
        maxSnoozes = maxSnoozes,
        autoRetryMinutes = autoRetryMinutes,
        maxAutoRetries = maxAutoRetries,
        allowReturnToSleep = allowReturnToSleep
    ),
    vibrate = vibrate,
    soundUri = soundUri,
    fadeInSeconds = fadeInSeconds,
    // Coerced rather than trusted: Alarm's constructor rejects an out-of-range
    // volume, and a stored row from a future or hand-edited database must not be
    // able to throw on the way in and take the alarm list with it.
    volumePercent = volumePercent.coerceIn(Alarm.MIN_VOLUME_PERCENT, 100)
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    label = label,
    timeMinute = DayWindow.minuteOf(time),
    days = days.joinToString(",") { it.name },
    enabled = enabled,
    challengeType = challenge.type.name,
    challengeDifficulty = challenge.difficulty.name,
    challengeAdaptive = challenge.adaptive,
    challengeRequired = challenge.required,
    snoozeMinutes = snooze.snoozeMinutes,
    maxSnoozes = snooze.maxSnoozes,
    autoRetryMinutes = snooze.autoRetryMinutes,
    maxAutoRetries = snooze.maxAutoRetries,
    allowReturnToSleep = snooze.allowReturnToSleep,
    vibrate = vibrate,
    soundUri = soundUri,
    fadeInSeconds = fadeInSeconds,
    volumePercent = volumePercent
)

// --- History ----------------------------------------------------------------

fun CompletionRecordEntity.toDomain(): CompletionRecord = CompletionRecord(
    id = id,
    activityId = activityId,
    blockId = blockId,
    date = LocalDate.parse(date),
    result = result.toEnum(CompletionResult.COMPLETED),
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMillis),
    note = note
)

fun CompletionRecord.toEntity(): CompletionRecordEntity = CompletionRecordEntity(
    id = id,
    activityId = activityId,
    blockId = blockId,
    date = date.toString(),
    result = result.name,
    recordedAtEpochMillis = recordedAt.toEpochMilli(),
    note = note
)

fun WakeChallengeResultEntity.toDomain(): WakeChallengeResult = WakeChallengeResult(
    id = id,
    alarmId = alarmId,
    type = type.toEnum(WakeChallengeType.Default),
    difficulty = difficulty.toEnum(ChallengeDifficulty.Default),
    succeeded = succeeded,
    mistakes = mistakes,
    elapsedMillis = elapsedMillis,
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMillis)
)

fun WakeChallengeResult.toEntity(): WakeChallengeResultEntity = WakeChallengeResultEntity(
    id = id,
    alarmId = alarmId,
    type = type.name,
    difficulty = difficulty.name,
    succeeded = succeeded,
    mistakes = mistakes,
    elapsedMillis = elapsedMillis,
    recordedAtEpochMillis = recordedAt.toEpochMilli()
)
