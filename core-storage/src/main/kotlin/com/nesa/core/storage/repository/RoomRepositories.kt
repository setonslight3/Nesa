package com.nesa.core.storage.repository

import com.nesa.core.model.Activity
import com.nesa.core.model.ActivityState
import com.nesa.core.model.Alarm
import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.Exercise
import com.nesa.core.model.Goal
import com.nesa.core.model.LifeSchedule
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.RecurrenceFrequency
import com.nesa.core.model.ScheduleBlock
import com.nesa.core.model.WakeChallengeResult
import com.nesa.core.model.WorkoutRoutine
import com.nesa.core.model.WorkoutSession
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.AlarmRepository
import com.nesa.core.model.repository.FitnessRepository
import com.nesa.core.model.repository.GoalRepository
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.model.repository.LifeScheduleRepository
import com.nesa.core.storage.dao.ActivityDao
import com.nesa.core.storage.dao.AlarmDao
import com.nesa.core.storage.dao.FitnessDao
import com.nesa.core.storage.dao.GoalDao
import com.nesa.core.storage.dao.HistoryDao
import com.nesa.core.storage.dao.LifeScheduleDao
import com.nesa.core.storage.entity.ActivityEntity
import com.nesa.core.storage.entity.ScheduleBlockEntity
import com.nesa.core.storage.entity.SetLogEntity
import com.nesa.core.storage.entity.WorkoutSessionEntity
import com.nesa.core.storage.mapper.toDomain
import com.nesa.core.storage.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed persistence.
 *
 * Reads always come from the database, never from a cache built elsewhere, so
 * what the screen shows and what survives a restart are the same thing.
 */
@Singleton
class RoomActivityRepository @Inject constructor(
    private val dao: ActivityDao
) : ActivityRepository {

    override fun observePlan(date: LocalDate): Flow<List<PlannedActivity>> =
        observeRange(date, date)

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<PlannedActivity>> =
        combine(
            dao.observeBlocksBetween(from.toString(), to.toString()),
            dao.observeActivitiesBetween(from.toString(), to.toString())
        ) { blocks, activities -> join(activities, blocks) }

    override suspend fun plan(date: LocalDate): List<PlannedActivity> =
        join(dao.activitiesOn(date.toString()), dao.blocksOn(date.toString()))

    override suspend fun findBlock(blockId: String): PlannedActivity? {
        val block = dao.block(blockId) ?: return null
        val activity = dao.activity(block.activityId) ?: return null
        return PlannedActivity(activity.toDomain(), block.toDomain())
    }

    override suspend fun repeatingActivities(): List<Activity> =
        dao.repeatingActivities(RecurrenceFrequency.NONE.name).map { it.toDomain() }

    override suspend fun save(activity: Activity, block: ScheduleBlock) {
        dao.save(activity.toEntity(), block.toEntity())
    }

    override suspend fun saveActivity(activity: Activity) {
        dao.upsertActivity(activity.toEntity())
    }

    override suspend fun addBlocks(blocks: List<ScheduleBlock>) {
        if (blocks.isEmpty()) return
        dao.upsertBlocks(blocks.map { it.toEntity() })
    }

    override suspend fun updateBlocks(blocks: List<ScheduleBlock>) {
        if (blocks.isEmpty()) return
        dao.upsertBlocks(blocks.map { it.toEntity() })
    }

    override suspend fun updateBlockState(blockId: String, state: ActivityState) {
        dao.updateState(blockId, state.name)
    }

    override suspend fun incrementRemindersSent(blockId: String) {
        dao.incrementRemindersSent(blockId)
    }

    override suspend fun deleteActivity(activityId: String) {
        dao.deleteActivity(activityId)
    }

    /**
     * Pairs each block with its activity. A block whose activity is missing is
     * dropped rather than faked: the foreign key makes that state impossible in
     * practice, and inventing a placeholder would hide a real bug.
     */
    private fun join(
        activities: List<ActivityEntity>,
        blocks: List<ScheduleBlockEntity>
    ): List<PlannedActivity> {
        val byId = activities.associateBy { it.id }
        return blocks.mapNotNull { block ->
            byId[block.activityId]?.let { PlannedActivity(it.toDomain(), block.toDomain()) }
        }
    }
}

@Singleton
class RoomGoalRepository @Inject constructor(
    private val dao: GoalDao
) : GoalRepository {

    override fun observeGoals(): Flow<List<Goal>> =
        dao.observeGoals().map { goals -> goals.map { it.toDomain() } }

    override suspend fun replaceAll(goals: List<Goal>) {
        dao.replaceAll(goals.map { it.toEntity() })
    }

    override suspend fun add(goal: Goal) {
        dao.upsert(goal.toEntity())
    }

    override suspend fun remove(goalId: String) {
        dao.delete(goalId)
    }
}

@Singleton
class RoomAlarmRepository @Inject constructor(
    private val dao: AlarmDao
) : AlarmRepository {

    override fun observeAlarms(): Flow<List<Alarm>> =
        dao.observeAlarms().map { alarms -> alarms.map { it.toDomain() } }

    override suspend fun alarms(): List<Alarm> = dao.alarms().map { it.toDomain() }

    override suspend fun find(alarmId: String): Alarm? = dao.alarm(alarmId)?.toDomain()

    override suspend fun save(alarm: Alarm) {
        dao.upsert(alarm.toEntity())
    }

    override suspend fun delete(alarmId: String) {
        dao.delete(alarmId)
    }
}

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val dao: HistoryDao
) : HistoryRepository {

    override suspend fun record(record: CompletionRecord) {
        dao.record(record.toEntity())
    }

    override fun observeRecords(from: LocalDate, to: LocalDate): Flow<List<CompletionRecord>> =
        dao.observeRecords(from.toString(), to.toString()).map { records -> records.map { it.toDomain() } }

    override suspend fun recordChallengeResult(result: WakeChallengeResult) {
        dao.recordChallenge(result.toEntity())
    }

    override suspend fun recentChallengeResults(alarmId: String, limit: Int): List<WakeChallengeResult> =
        // Stored newest first; the difficulty policy reads history oldest first.
        dao.recentChallenges(alarmId, limit).map { it.toDomain() }.reversed()
}

/**
 * Fitness, backed by Room.
 *
 * Routines and sessions are each stored across two tables — a header and its
 * rows — so every read here joins them back together. The join is done in
 * Kotlin over two queries rather than with a Room `@Relation`, because the
 * domain types are plain data classes that know nothing about Room and are not
 * going to gain annotations to suit it.
 */
@Singleton
class RoomFitnessRepository @Inject constructor(
    private val dao: FitnessDao
) : FitnessRepository {

    override fun observeExercises(): Flow<List<Exercise>> =
        dao.observeExercises().map { rows -> rows.map { it.toDomain() } }

    override suspend fun exercises(): List<Exercise> = dao.exercises().map { it.toDomain() }

    override suspend fun saveExercise(exercise: Exercise) {
        dao.upsertExercise(exercise.toEntity())
    }

    override suspend fun deleteExercise(exerciseId: String) {
        dao.deleteExercise(exerciseId)
    }

    override fun observeRoutines(): Flow<List<WorkoutRoutine>> =
        combine(dao.observeRoutines(), dao.observeAllRoutineExercises()) { routines, exercises ->
            val byRoutine = exercises.groupBy { it.routineId }
            routines.map { routine -> routine.toDomain(byRoutine[routine.id].orEmpty()) }
        }

    override suspend fun routine(routineId: String): WorkoutRoutine? =
        dao.routine(routineId)?.toDomain(dao.routineExercises(routineId))

    override suspend fun saveRoutine(routine: WorkoutRoutine) {
        dao.saveRoutine(
            routine.toEntity(),
            routine.exercises.map { it.toEntity(routine.id) }
        )
    }

    override suspend fun deleteRoutine(routineId: String) {
        dao.deleteRoutine(routineId)
    }

    override fun observeSessions(from: LocalDate, to: LocalDate): Flow<List<WorkoutSession>> =
        combine(
            dao.observeSessions(from.toString(), to.toString()),
            dao.observeSetLogs(from.toString(), to.toString())
        ) { sessions, logs -> joinSessions(sessions, logs) }

    override suspend fun sessions(from: LocalDate, to: LocalDate): List<WorkoutSession> =
        joinSessions(
            dao.sessions(from.toString(), to.toString()),
            dao.setLogs(from.toString(), to.toString())
        )

    override suspend fun logSession(session: WorkoutSession) {
        dao.saveSession(session.toEntity(), session.sets.map { it.toEntity() })
    }

    override suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    private fun joinSessions(
        sessions: List<WorkoutSessionEntity>,
        logs: List<SetLogEntity>
    ): List<WorkoutSession> {
        val bySession = logs.groupBy { it.sessionId }
        return sessions.map { it.toDomain(bySession[it.id].orEmpty()) }
    }
}

/** Life schedules, backed by Room. Header and entries joined in Kotlin. */
@Singleton
class RoomLifeScheduleRepository @Inject constructor(
    private val dao: LifeScheduleDao
) : LifeScheduleRepository {

    override fun observeSchedules(): Flow<List<LifeSchedule>> =
        combine(dao.observeSchedules(), dao.observeAllEntries()) { schedules, entries ->
            val byScheduleId = entries.groupBy { it.scheduleId }
            schedules.map { it.toDomain(byScheduleId[it.id].orEmpty()) }
        }

    override suspend fun schedules(): List<LifeSchedule> {
        val byScheduleId = dao.allEntries().groupBy { it.scheduleId }
        return dao.schedules().map { it.toDomain(byScheduleId[it.id].orEmpty()) }
    }

    override suspend fun schedule(scheduleId: String): LifeSchedule? =
        dao.schedule(scheduleId)?.toDomain(dao.entries(scheduleId))

    override suspend fun save(schedule: LifeSchedule) {
        dao.saveSchedule(schedule.toEntity(), schedule.entries.map { it.toEntity(schedule.id) })
    }

    override suspend fun delete(scheduleId: String) {
        dao.deleteSchedule(scheduleId)
    }
}
