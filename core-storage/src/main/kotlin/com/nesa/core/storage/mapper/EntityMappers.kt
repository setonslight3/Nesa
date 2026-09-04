package com.nesa.core.storage.mapper

import com.nesa.core.model.Activity
import com.nesa.core.model.ActivityState
import com.nesa.core.model.Alarm
import com.nesa.core.model.ChallengeDifficulty
import com.nesa.core.model.ChangeReasonCodec
import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Exercise
import com.nesa.core.model.ExerciseKind
import com.nesa.core.model.Flexibility
import com.nesa.core.model.Goal
import com.nesa.core.model.LifeSchedule
import com.nesa.core.model.LifeScheduleKind
import com.nesa.core.model.GoalCategory
import com.nesa.core.model.GoalStatus
import com.nesa.core.model.NesaModule
import com.nesa.core.model.PerceivedEffort
import com.nesa.core.model.Priority
import com.nesa.core.model.Recurrence
import com.nesa.core.model.RecurrenceFrequency
import com.nesa.core.model.RoutineExercise
import com.nesa.core.model.ScheduleBlock
import com.nesa.core.model.ScheduleEntry
import com.nesa.core.model.SetLog
import com.nesa.core.model.SetOutcome
import com.nesa.core.model.SnoozePolicy
import com.nesa.core.model.WakeChallengePolicy
import com.nesa.core.model.WakeChallengeResult
import com.nesa.core.model.WakeChallengeType
import com.nesa.core.model.WorkoutRoutine
import com.nesa.core.model.WorkoutSession
import com.nesa.core.storage.entity.ActivityEntity
import com.nesa.core.storage.entity.AlarmEntity
import com.nesa.core.storage.entity.CompletionRecordEntity
import com.nesa.core.storage.entity.ExerciseEntity
import com.nesa.core.storage.entity.GoalEntity
import com.nesa.core.storage.entity.LifeScheduleEntity
import com.nesa.core.storage.entity.RoutineExerciseEntity
import com.nesa.core.storage.entity.ScheduleBlockEntity
import com.nesa.core.storage.entity.ScheduleEntryEntity
import com.nesa.core.storage.entity.SetLogEntity
import com.nesa.core.storage.entity.WakeChallengeResultEntity
import com.nesa.core.storage.entity.WorkoutRoutineEntity
import com.nesa.core.storage.entity.WorkoutSessionEntity
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

/** Guards a stored minute-of-day against a row that cannot be trusted. */
private const val MINUTES_IN_DAY = 24 * 60

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
    recurrence = readRecurrence(),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
)

/**
 * Rebuilds a recurrence rule, falling back to "happens once" rather than
 * throwing.
 *
 * `Recurrence`'s constructor rejects an incoherent rule — a weekly one with no
 * days, an interval with nothing to count from. That is right for code building
 * a rule, and wrong here: a row written by a newer build, or one left
 * inconsistent by a migration, must not be able to crash the timeline. A
 * one-off is the safe reading, because it under-schedules rather than
 * inventing days the user never asked for.
 */
private fun ActivityEntity.readRecurrence(): Recurrence = runCatching {
    Recurrence(
        frequency = recurrenceFrequency.toEnum(RecurrenceFrequency.Default),
        interval = recurrenceInterval.coerceIn(1, Recurrence.MAX_INTERVAL),
        daysOfWeek = recurrenceDays.split(',')
            .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
            .toSet(),
        startDate = recurrenceStart?.let(LocalDate::parse),
        endDate = recurrenceEnd?.let(LocalDate::parse)
    )
}.getOrDefault(Recurrence.Once)

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
    recurrenceFrequency = recurrence.frequency.name,
    recurrenceInterval = recurrence.interval,
    recurrenceDays = recurrence.daysOfWeek.joinToString(",") { it.name },
    recurrenceStart = recurrence.startDate?.toString(),
    recurrenceEnd = recurrence.endDate?.toString(),
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
    note = note,
    scheduledStartMinute = scheduledStartMinute?.takeIf { it in 0 until MINUTES_IN_DAY }
)

fun CompletionRecord.toEntity(): CompletionRecordEntity = CompletionRecordEntity(
    id = id,
    activityId = activityId,
    blockId = blockId,
    date = date.toString(),
    result = result.name,
    recordedAtEpochMillis = recordedAt.toEpochMilli(),
    note = note,
    scheduledStartMinute = scheduledStartMinute
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

// --- Fitness ----------------------------------------------------------------

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    kind = kind.toEnum(ExerciseKind.Default),
    notes = notes
)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    kind = kind.name,
    notes = notes
)

/**
 * Rebuilds a planned exercise, repairing a row rather than throwing on it.
 *
 * `RoutineExercise` requires that either reps or seconds is present, which is
 * right for code constructing one and wrong for a row read off disk: a routine
 * that could crash the fitness screen because one of its rows was written by a
 * different build is a worse outcome than a row that reads as a plain set of
 * ten. The repair is visible and editable; a crash is neither.
 */
fun RoutineExerciseEntity.toDomain(): RoutineExercise {
    val hasCount = (reps != null && reps > 0) || (seconds != null && seconds > 0)
    return RoutineExercise(
        id = id,
        exerciseId = exerciseId,
        position = position,
        sets = sets.coerceIn(1, RoutineExercise.MAX_SETS),
        reps = if (hasCount) reps?.takeIf { it > 0 } else RoutineExercise.DEFAULT_REPS,
        seconds = seconds?.takeIf { it > 0 },
        weightKg = weightKg?.takeIf { it > 0.0 },
        restSeconds = restSeconds.coerceIn(0, RoutineExercise.MAX_SECONDS)
    )
}

fun RoutineExercise.toEntity(routineId: String): RoutineExerciseEntity = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    position = position,
    sets = sets,
    reps = reps,
    seconds = seconds,
    weightKg = weightKg,
    restSeconds = restSeconds
)

fun WorkoutRoutineEntity.toDomain(exercises: List<RoutineExerciseEntity>): WorkoutRoutine =
    WorkoutRoutine(
        id = id,
        name = name,
        focus = focus,
        exercises = exercises.map { it.toDomain() },
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
    )

fun WorkoutRoutine.toEntity(): WorkoutRoutineEntity = WorkoutRoutineEntity(
    id = id,
    name = name,
    focus = focus,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)

fun SetLogEntity.toDomain(): SetLog = SetLog(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber.coerceAtLeast(1),
    reps = reps?.coerceAtLeast(0),
    seconds = seconds?.coerceAtLeast(0),
    weightKg = weightKg?.coerceAtLeast(0.0),
    outcome = outcome.toEnum(SetOutcome.Default)
)

fun SetLog.toEntity(): SetLogEntity = SetLogEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    reps = reps,
    seconds = seconds,
    weightKg = weightKg,
    outcome = outcome.name
)

fun WorkoutSessionEntity.toDomain(sets: List<SetLogEntity>): WorkoutSession = WorkoutSession(
    id = id,
    routineId = routineId,
    blockId = blockId,
    date = LocalDate.parse(date),
    durationMinutes = durationMinutes.coerceAtLeast(0),
    effort = effort.toEnum(PerceivedEffort.Default),
    sets = sets.map { it.toDomain() },
    notes = notes,
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMillis)
)

fun WorkoutSession.toEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
    id = id,
    routineId = routineId,
    blockId = blockId,
    date = date.toString(),
    durationMinutes = durationMinutes,
    effort = effort.name,
    notes = notes,
    recordedAtEpochMillis = recordedAt.toEpochMilli()
)

// --- Life schedules ---------------------------------------------------------

/**
 * Rebuilds an entry, repairing a row rather than throwing on it.
 *
 * `ScheduleEntry` rejects an entry that happens on no day and one with no
 * duration, which is right for code constructing one and wrong here: a row that
 * could crash the schedules screen is a worse outcome than a row that reads as
 * a Monday. The repair is visible and editable; a crash is neither.
 */
fun ScheduleEntryEntity.toDomain(): ScheduleEntry {
    val parsed = days.split(',')
        .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
        .toSet()
    return ScheduleEntry(
        id = id,
        title = title.ifBlank { "Untitled" },
        days = parsed.ifEmpty { setOf(DayOfWeek.MONDAY) },
        start = DayWindow.timeOf(startMinute),
        duration = Duration.ofMinutes(durationMinutes.toLong().coerceAtLeast(1L)),
        priority = priority.toEnum(Priority.NORMAL),
        flexibility = flexibility.toEnum(Flexibility.TIME_FLEXIBLE)
    )
}

fun ScheduleEntry.toEntity(scheduleId: String): ScheduleEntryEntity = ScheduleEntryEntity(
    id = id,
    scheduleId = scheduleId,
    title = title,
    days = days.joinToString(",") { it.name },
    startMinute = DayWindow.minuteOf(start),
    durationMinutes = durationMinutes,
    priority = priority.name,
    flexibility = flexibility.name
)

fun LifeScheduleEntity.toDomain(entries: List<ScheduleEntryEntity>): LifeSchedule = LifeSchedule(
    id = id,
    name = name.ifBlank { "Schedule" },
    kind = kind.toEnum(LifeScheduleKind.CUSTOM),
    enabled = enabled,
    entries = entries.map { it.toDomain() }
)

fun LifeSchedule.toEntity(): LifeScheduleEntity = LifeScheduleEntity(
    id = id,
    name = name,
    kind = kind.name,
    enabled = enabled
)
