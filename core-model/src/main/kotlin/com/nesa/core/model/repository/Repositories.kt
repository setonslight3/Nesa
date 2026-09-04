package com.nesa.core.model.repository

import com.nesa.core.model.Activity
import com.nesa.core.model.ActivityState
import com.nesa.core.model.Alarm
import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Goal
import com.nesa.core.model.GuidancePersonality
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.ScheduleBlock
import com.nesa.core.model.ThemeMode
import com.nesa.core.model.WakeChallengeResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The persistence contracts, declared in the domain so that features depend on
 * behaviour rather than on Room. This is also what keeps a future sync or cloud
 * implementation from rippling through the UI.
 *
 * Every read is a [Flow] over local state: NESA renders from the database, never
 * from a network response.
 */
interface ActivityRepository {

    /** The plan for one day, kept live as the database changes. */
    fun observePlan(date: LocalDate): Flow<List<PlannedActivity>>

    /** The plan for an inclusive date range, for statistics and previews. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<PlannedActivity>>

    suspend fun plan(date: LocalDate): List<PlannedActivity>

    suspend fun findBlock(blockId: String): PlannedActivity?

    /**
     * Every activity that repeats, so a day can be filled in from its rules.
     *
     * Only the repeating ones: a one-off activity has the single block it was
     * created with and there is nothing to derive.
     */
    suspend fun repeatingActivities(): List<Activity>

    /** Creates or replaces an activity together with its placement. */
    suspend fun save(activity: Activity, block: ScheduleBlock)

    /** Adds placements the recurrence rules say a day is missing. */
    suspend fun addBlocks(blocks: List<ScheduleBlock>)

    /** Writes back placements produced by the scheduler. */
    suspend fun updateBlocks(blocks: List<ScheduleBlock>)

    suspend fun updateBlockState(blockId: String, state: ActivityState)

    suspend fun incrementRemindersSent(blockId: String)

    /** Removes the activity and every placement of it. */
    suspend fun deleteActivity(activityId: String)
}

interface GoalRepository {
    fun observeGoals(): Flow<List<Goal>>
    suspend fun replaceAll(goals: List<Goal>)
    suspend fun add(goal: Goal)
    suspend fun remove(goalId: String)
}

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>
    suspend fun alarms(): List<Alarm>
    suspend fun find(alarmId: String): Alarm?
    suspend fun save(alarm: Alarm)
    suspend fun delete(alarmId: String)
}

interface HistoryRepository {
    suspend fun record(record: CompletionRecord)
    fun observeRecords(from: LocalDate, to: LocalDate): Flow<List<CompletionRecord>>
    suspend fun recordChallengeResult(result: WakeChallengeResult)
    suspend fun recentChallengeResults(alarmId: String, limit: Int = 10): List<WakeChallengeResult>
}

/**
 * Scalar preferences. Kept apart from [ActivityRepository] because these are
 * single values a user sets, not relational data NESA reasons about.
 */
interface SettingsRepository {
    val settings: Flow<NesaSettings>
    suspend fun current(): NesaSettings
    suspend fun setDisplayName(name: String?)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setGuidance(guidance: GuidancePersonality)
    suspend fun setDayWindow(window: DayWindow)
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setPrimaryAlarmId(alarmId: String?)
}
