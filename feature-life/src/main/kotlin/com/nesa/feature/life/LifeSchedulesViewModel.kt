package com.nesa.feature.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.LifeSchedule
import com.nesa.core.model.LifeScheduleKind
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.LifeScheduleRepository
import com.nesa.core.scheduling.DayPlanner
import com.nesa.core.scheduling.LifeScheduleApplier
import com.nesa.core.scheduling.LifeSchedulePresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class LifeSchedulesUiState(
    val schedules: List<LifeSchedule> = emptyList()
) {
    /** Kinds the user has not created yet, offered as one-tap starting points. */
    val availableKinds: List<LifeScheduleKind>
        get() = LifeScheduleKind.entries.filter { kind ->
            kind == LifeScheduleKind.CUSTOM || schedules.none { it.kind == kind }
        }
}

/**
 * The Life module's schedules.
 *
 * Every write here goes through [applySchedule], which is the one place that
 * reconciles a schedule with the activities it owns. Having two paths that both
 * generated activities is precisely how a user ends up with two Works on a
 * Monday, so there is deliberately only one.
 */
@HiltViewModel
class LifeSchedulesViewModel @Inject constructor(
    private val schedules: LifeScheduleRepository,
    private val activities: ActivityRepository,
    private val planner: DayPlanner,
    private val clock: Clock
) : ViewModel() {

    val state: StateFlow<LifeSchedulesUiState> = schedules.observeSchedules()
        .map { LifeSchedulesUiState(schedules = it.sortedBy { schedule -> schedule.name }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LifeSchedulesUiState()
        )

    /** Creates a draft of a kind the user has not set up yet. It starts off. */
    fun onAddSchedule(kind: LifeScheduleKind, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val draft = LifeSchedulePresets.draft(kind) { UUID.randomUUID().toString() }
            schedules.save(draft)
            onCreated(draft.id)
        }
    }

    fun onEnabledChanged(schedule: LifeSchedule, enabled: Boolean) {
        viewModelScope.launch { applySchedule(schedule.copy(enabled = enabled)) }
    }

    fun onDelete(schedule: LifeSchedule) {
        viewModelScope.launch {
            // The activities go first. Deleting the schedule row cascades its
            // entries away, and without them there would be no way left to work
            // out which activities it had owned.
            removeOwnedActivities(schedule)
            schedules.delete(schedule.id)
            replanToday()
        }
    }

    /**
     * Writes a schedule and brings its activities into line with it.
     *
     * @param previous the schedule as it was, when this is an edit. Entries that
     *   were in it and are not in [schedule] have their activities deleted —
     *   otherwise removing a class from a timetable would leave it on the
     *   timeline forever, which is exactly the silent-stale-data failure the
     *   product rules forbid.
     */
    suspend fun applySchedule(schedule: LifeSchedule, previous: LifeSchedule? = null) {
        schedules.save(schedule)

        val nowOwned = LifeScheduleApplier.activityIdsFor(schedule).toSet()
        val wasOwned = previous?.let { LifeScheduleApplier.activityIdsFor(it) }.orEmpty().toSet()
        (wasOwned - nowOwned).forEach { activities.deleteActivity(it) }

        if (schedule.enabled) {
            val today = LocalDate.now(clock)
            LifeScheduleApplier.activitiesFor(schedule, Instant.now(clock), today).forEach {
                // No block: the activity carries a weekly Recurrence, and
                // RecurrenceMaterialiser creates a block on each matching day.
                // Creating one here as well would produce a duplicate today.
                activities.saveActivity(it)
            }
        } else {
            removeOwnedActivities(schedule)
        }

        replanToday()
    }

    private suspend fun removeOwnedActivities(schedule: LifeSchedule) {
        LifeScheduleApplier.activityIdsFor(schedule).forEach { activities.deleteActivity(it) }
    }

    /** The day has changed shape, so it is replanned rather than left stale. */
    private suspend fun replanToday() {
        runCatching { planner.refresh(LocalDate.now(clock)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
