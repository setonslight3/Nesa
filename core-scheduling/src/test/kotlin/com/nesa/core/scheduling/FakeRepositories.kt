package com.nesa.core.scheduling

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
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.model.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * In-memory repositories.
 *
 * They exist so the planning behaviour can be tested without Room, an emulator
 * or a clock that moves on its own.
 */
class FakeActivityRepository(initial: List<PlannedActivity> = emptyList()) : ActivityRepository {

    private val state = MutableStateFlow(initial)

    /**
     * Activities on their own, apart from their placements.
     *
     * A recurring activity exists on days it has no block for yet — that is the
     * whole point of it — so the fake cannot derive the activity list from the
     * blocks the way it did when every activity had exactly one.
     */
    private val known = initial.map { it.activity }.associateBy { it.id }.toMutableMap()

    val items: List<PlannedActivity> get() = state.value

    /** Registers an activity that has no placement yet. */
    fun addActivity(activity: Activity) {
        known[activity.id] = activity
    }

    fun block(blockId: String): ScheduleBlock? = state.value.firstOrNull { it.block.id == blockId }?.block

    override fun observePlan(date: LocalDate): Flow<List<PlannedActivity>> =
        state.map { items -> items.filter { it.block.date == date } }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<PlannedActivity>> =
        state.map { items -> items.filter { it.block.date >= from && it.block.date <= to } }

    override suspend fun plan(date: LocalDate): List<PlannedActivity> =
        observePlan(date).first()

    override suspend fun findBlock(blockId: String): PlannedActivity? =
        state.value.firstOrNull { it.block.id == blockId }

    override suspend fun repeatingActivities(): List<Activity> =
        known.values.filter { it.repeats }

    override suspend fun save(activity: Activity, block: ScheduleBlock) {
        known[activity.id] = activity
        state.value = state.value.filterNot { it.block.id == block.id } + PlannedActivity(activity, block)
    }

    override suspend fun addBlocks(blocks: List<ScheduleBlock>) {
        val added = blocks.mapNotNull { block ->
            known[block.activityId]?.let { PlannedActivity(it, block) }
        }
        state.value = state.value + added
    }

    override suspend fun updateBlocks(blocks: List<ScheduleBlock>) {
        val byId = blocks.associateBy { it.id }
        state.value = state.value.map { item -> byId[item.block.id]?.let { item.copy(block = it) } ?: item }
    }

    override suspend fun updateBlockState(blockId: String, state: ActivityState) {
        this.state.value = this.state.value.map { item ->
            if (item.block.id == blockId) item.copy(block = item.block.copy(state = state)) else item
        }
    }

    override suspend fun incrementRemindersSent(blockId: String) {
        state.value = state.value.map { item ->
            if (item.block.id == blockId) {
                item.copy(block = item.block.copy(remindersSent = item.block.remindersSent + 1))
            } else {
                item
            }
        }
    }

    override suspend fun deleteActivity(activityId: String) {
        state.value = state.value.filterNot { it.activity.id == activityId }
    }
}

class FakeHistoryRepository : HistoryRepository {

    val records = mutableListOf<CompletionRecord>()
    val challengeResults = mutableListOf<WakeChallengeResult>()

    override suspend fun record(record: CompletionRecord) {
        records += record
    }

    override fun observeRecords(from: LocalDate, to: LocalDate): Flow<List<CompletionRecord>> =
        MutableStateFlow(records.filter { it.date >= from && it.date <= to })

    override suspend fun recordChallengeResult(result: WakeChallengeResult) {
        challengeResults += result
    }

    override suspend fun recentChallengeResults(alarmId: String, limit: Int): List<WakeChallengeResult> =
        challengeResults.filter { it.alarmId == alarmId }.takeLast(limit)
}

class FakeSettingsRepository(initial: NesaSettings = NesaSettings.Default) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<NesaSettings> = state

    override suspend fun current(): NesaSettings = state.value

    override suspend fun setDisplayName(name: String?) {
        state.value = state.value.copy(displayName = name)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        state.value = state.value.copy(onboardingCompleted = completed)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setGuidance(guidance: GuidancePersonality) {
        state.value = state.value.copy(guidance = guidance)
    }

    override suspend fun setDayWindow(window: DayWindow) {
        state.value = state.value.copy(dayWindow = window)
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        state.value = state.value.copy(remindersEnabled = enabled)
    }

    override suspend fun setPrimaryAlarmId(alarmId: String?) {
        state.value = state.value.copy(primaryAlarmId = alarmId)
    }
}
