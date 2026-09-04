package com.nesa.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nesa.core.storage.entity.ActivityEntity
import com.nesa.core.storage.entity.AlarmEntity
import com.nesa.core.storage.entity.CompletionRecordEntity
import com.nesa.core.storage.entity.GoalEntity
import com.nesa.core.storage.entity.ScheduleBlockEntity
import com.nesa.core.storage.entity.WakeChallengeResultEntity
import kotlinx.coroutines.flow.Flow

/**
 * Activities and their placements.
 *
 * Blocks and activities are read as two flows and joined in the repository
 * rather than through a hand-written column-aliased join. One user's day is a
 * handful of rows, so the in-memory join costs nothing and the SQL stays
 * readable — which matters more when a migration has to be reviewed.
 */
@Dao
interface ActivityDao {

    @Query("SELECT * FROM schedule_blocks WHERE date = :date ORDER BY startMinute ASC")
    fun observeBlocksOn(date: String): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE date BETWEEN :from AND :to ORDER BY date ASC, startMinute ASC")
    fun observeBlocksBetween(from: String, to: String): Flow<List<ScheduleBlockEntity>>

    @Query(
        "SELECT * FROM activities WHERE id IN " +
            "(SELECT activityId FROM schedule_blocks WHERE date BETWEEN :from AND :to)"
    )
    fun observeActivitiesBetween(from: String, to: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE date = :date ORDER BY startMinute ASC")
    suspend fun blocksOn(date: String): List<ScheduleBlockEntity>

    @Query(
        "SELECT * FROM activities WHERE id IN " +
            "(SELECT activityId FROM schedule_blocks WHERE date = :date)"
    )
    suspend fun activitiesOn(date: String): List<ActivityEntity>

    @Query("SELECT * FROM schedule_blocks WHERE id = :blockId")
    suspend fun block(blockId: String): ScheduleBlockEntity?

    @Query("SELECT * FROM activities WHERE id = :activityId")
    suspend fun activity(activityId: String): ActivityEntity?

    /**
     * Every activity with a recurrence rule.
     *
     * Filtered in SQL rather than in Kotlin because this runs on every refresh
     * of a day, and reading the whole activity table to discard most of it would
     * get slower with every activity the user ever created.
     */
    @Query("SELECT * FROM activities WHERE recurrenceFrequency != :none")
    suspend fun repeatingActivities(none: String): List<ActivityEntity>

    @Upsert
    suspend fun upsertActivity(activity: ActivityEntity)

    @Upsert
    suspend fun upsertBlock(block: ScheduleBlockEntity)

    @Upsert
    suspend fun upsertBlocks(blocks: List<ScheduleBlockEntity>)

    /** An activity and its placement always move together, so they share a transaction. */
    @Transaction
    suspend fun save(activity: ActivityEntity, block: ScheduleBlockEntity) {
        upsertActivity(activity)
        upsertBlock(block)
    }

    @Query("UPDATE schedule_blocks SET state = :state WHERE id = :blockId")
    suspend fun updateState(blockId: String, state: String)

    @Query("UPDATE schedule_blocks SET remindersSent = remindersSent + 1 WHERE id = :blockId")
    suspend fun incrementRemindersSent(blockId: String)

    /** Blocks cascade with the activity, so one delete is enough. */
    @Query("DELETE FROM activities WHERE id = :activityId")
    suspend fun deleteActivity(activityId: String)
}

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY createdAtEpochMillis ASC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Upsert
    suspend fun upsertAll(goals: List<GoalEntity>)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun delete(goalId: String)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(goals: List<GoalEntity>) {
        deleteAll()
        upsertAll(goals)
    }
}

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY timeMinute ASC")
    fun observeAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY timeMinute ASC")
    suspend fun alarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :alarmId")
    suspend fun alarm(alarmId: String): AlarmEntity?

    @Upsert
    suspend fun upsert(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :alarmId")
    suspend fun delete(alarmId: String)
}

@Dao
interface HistoryDao {

    @Upsert
    suspend fun record(record: CompletionRecordEntity)

    @Query("SELECT * FROM completion_records WHERE date BETWEEN :from AND :to ORDER BY recordedAtEpochMillis DESC")
    fun observeRecords(from: String, to: String): Flow<List<CompletionRecordEntity>>

    @Upsert
    suspend fun recordChallenge(result: WakeChallengeResultEntity)

    @Query(
        "SELECT * FROM wake_challenge_results WHERE alarmId = :alarmId " +
            "ORDER BY recordedAtEpochMillis DESC LIMIT :limit"
    )
    suspend fun recentChallenges(alarmId: String, limit: Int): List<WakeChallengeResultEntity>
}
