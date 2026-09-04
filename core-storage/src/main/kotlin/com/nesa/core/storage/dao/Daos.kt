package com.nesa.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

/**
 * Fitness.
 *
 * A routine and its exercises are written together in [saveRoutine], as a
 * transaction: a routine that saved its header and lost its exercises to a
 * crash would be a plan the user thought they had.
 */
@Dao
interface FitnessDao {

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun exercises(): List<ExerciseEntity>

    @Upsert
    suspend fun upsertExercise(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises WHERE id = :exerciseId")
    suspend fun deleteExercise(exerciseId: String)

    @Query("SELECT * FROM workout_routines ORDER BY name ASC")
    fun observeRoutines(): Flow<List<WorkoutRoutineEntity>>

    @Query("SELECT * FROM routine_exercises ORDER BY position ASC, id ASC")
    fun observeAllRoutineExercises(): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM workout_routines WHERE id = :routineId")
    suspend fun routine(routineId: String): WorkoutRoutineEntity?

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY position ASC, id ASC")
    suspend fun routineExercises(routineId: String): List<RoutineExerciseEntity>

    @Upsert
    suspend fun upsertRoutine(routine: WorkoutRoutineEntity)

    @Upsert
    suspend fun upsertRoutineExercises(exercises: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun clearRoutineExercises(routineId: String)

    @Query("DELETE FROM workout_routines WHERE id = :routineId")
    suspend fun deleteRoutine(routineId: String)

    @Transaction
    suspend fun saveRoutine(routine: WorkoutRoutineEntity, exercises: List<RoutineExerciseEntity>) {
        upsertRoutine(routine)
        // Replaced wholesale rather than merged: an exercise the user removed
        // has to disappear, and working out which rows went missing is exactly
        // the kind of diffing that gets subtly wrong.
        clearRoutineExercises(routine.id)
        if (exercises.isNotEmpty()) upsertRoutineExercises(exercises)
    }

    @Query("SELECT * FROM workout_sessions WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeSessions(from: String, to: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    suspend fun sessions(from: String, to: String): List<WorkoutSessionEntity>

    @Query(
        """
        SELECT * FROM set_logs WHERE sessionId IN (
            SELECT id FROM workout_sessions WHERE date BETWEEN :from AND :to
        ) ORDER BY setNumber ASC
        """
    )
    fun observeSetLogs(from: String, to: String): Flow<List<SetLogEntity>>

    @Query(
        """
        SELECT * FROM set_logs WHERE sessionId IN (
            SELECT id FROM workout_sessions WHERE date BETWEEN :from AND :to
        ) ORDER BY setNumber ASC
        """
    )
    suspend fun setLogs(from: String, to: String): List<SetLogEntity>

    @Upsert
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Upsert
    suspend fun upsertSetLogs(logs: List<SetLogEntity>)

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId")
    suspend fun clearSetLogs(sessionId: String)

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Transaction
    suspend fun saveSession(session: WorkoutSessionEntity, logs: List<SetLogEntity>) {
        upsertSession(session)
        clearSetLogs(session.id)
        if (logs.isNotEmpty()) upsertSetLogs(logs)
    }
}

/**
 * Life schedules.
 *
 * A schedule and its entries are written together, as a transaction: a schedule
 * that saved its header and lost its entries would be a work week the user
 * thought they had configured.
 */
@Dao
interface LifeScheduleDao {

    @Query("SELECT * FROM life_schedules ORDER BY name ASC")
    fun observeSchedules(): Flow<List<LifeScheduleEntity>>

    @Query("SELECT * FROM schedule_entries ORDER BY startMinute ASC, title ASC")
    fun observeAllEntries(): Flow<List<ScheduleEntryEntity>>

    @Query("SELECT * FROM life_schedules")
    suspend fun schedules(): List<LifeScheduleEntity>

    @Query("SELECT * FROM life_schedules WHERE id = :scheduleId")
    suspend fun schedule(scheduleId: String): LifeScheduleEntity?

    @Query("SELECT * FROM schedule_entries WHERE scheduleId = :scheduleId ORDER BY startMinute ASC")
    suspend fun entries(scheduleId: String): List<ScheduleEntryEntity>

    @Query("SELECT * FROM schedule_entries")
    suspend fun allEntries(): List<ScheduleEntryEntity>

    @Upsert
    suspend fun upsertSchedule(schedule: LifeScheduleEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<ScheduleEntryEntity>)

    @Query("DELETE FROM schedule_entries WHERE scheduleId = :scheduleId")
    suspend fun clearEntries(scheduleId: String)

    @Query("DELETE FROM life_schedules WHERE id = :scheduleId")
    suspend fun deleteSchedule(scheduleId: String)

    @Transaction
    suspend fun saveSchedule(schedule: LifeScheduleEntity, entries: List<ScheduleEntryEntity>) {
        upsertSchedule(schedule)
        // Replaced wholesale rather than merged, so an entry the user deleted
        // actually disappears. Diffing rows is exactly the kind of thing that
        // goes subtly wrong.
        clearEntries(schedule.id)
        if (entries.isNotEmpty()) upsertEntries(entries)
    }
}
