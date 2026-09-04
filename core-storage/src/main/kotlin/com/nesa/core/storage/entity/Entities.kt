package com.nesa.core.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities.
 *
 * They store primitives only — no enums, no java.time, no type converters. The
 * translation to and from domain types happens in one place, the mappers, so
 * the database schema stays obvious and a domain change never silently alters
 * what is on disk.
 *
 * Wall-clock times are stored as minutes since midnight, dates as ISO-8601
 * strings, and instants as epoch milliseconds.
 */
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val module: String,
    val durationMinutes: Int,
    val priority: String,
    val flexibility: String,
    val preferredStartMinute: Int?,
    /** ISO-8601 local date-time, or null when the activity has no deadline. */
    val deadline: String?,
    // Recurrence, added in schema 3. Five primitives rather than a serialised
    // rule object: Room stores primitives only, and a column per field is
    // queryable and readable in a schema diff.
    val recurrenceFrequency: String,
    val recurrenceInterval: Int,
    /** Comma-separated DayOfWeek names; empty for non-weekly rules. */
    val recurrenceDays: String,
    /** ISO-8601 local dates, or null when the rule needs no anchor or no end. */
    val recurrenceStart: String?,
    val recurrenceEnd: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "schedule_blocks",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("activityId"), Index("date")]
)
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    /** ISO-8601 local date. */
    val date: String,
    val startMinute: Int,
    val endMinute: Int,
    val state: String,
    val locked: Boolean,
    val changeReason: String?,
    val remindersSent: Int
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val status: String,
    val createdAtEpochMillis: Long
)

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val label: String,
    val timeMinute: Int,
    /** Comma-separated DayOfWeek names; empty means a one-shot alarm. */
    val days: String,
    val enabled: Boolean,
    val challengeType: String,
    val challengeDifficulty: String,
    val challengeAdaptive: Boolean,
    val challengeRequired: Boolean,
    val snoozeMinutes: Int,
    val maxSnoozes: Int,
    val autoRetryMinutes: Int,
    val maxAutoRetries: Int,
    val allowReturnToSleep: Boolean,
    val vibrate: Boolean,
    val soundUri: String?,
    val fadeInSeconds: Int,
    /** Added in schema 2. Existing rows default to Alarm.DEFAULT_VOLUME_PERCENT. */
    val volumePercent: Int
)

@Entity(
    tableName = "completion_records",
    indices = [Index("date"), Index("activityId")]
)
data class CompletionRecordEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val blockId: String,
    val date: String,
    val result: String,
    val recordedAtEpochMillis: Long,
    val note: String?
)

@Entity(
    tableName = "wake_challenge_results",
    indices = [Index("alarmId")]
)
data class WakeChallengeResultEntity(
    @PrimaryKey val id: String,
    val alarmId: String,
    val type: String,
    val difficulty: String,
    val succeeded: Boolean,
    val mistakes: Int,
    val elapsedMillis: Long,
    val recordedAtEpochMillis: Long
)

/** An activity together with the placements that belong to it. */
data class ActivityWithBlocks(
    @androidx.room.Embedded val activity: ActivityEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "activityId")
    val blocks: List<ScheduleBlockEntity>
)
