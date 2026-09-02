package com.nesa.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.ActivityState
import com.nesa.core.model.ChangeReason
import com.nesa.core.model.DayCycle
import com.nesa.core.model.DayWindow
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.scheduling.ActivityActionHandler
import com.nesa.core.scheduling.ActivityEvent
import com.nesa.core.scheduling.ActivityStateMachine
import com.nesa.core.scheduling.DayPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** One activity as the timeline shows it. */
data class TimelineEntry(
    val planned: PlannedActivity,
    /** The single thing NESA thinks the user should do next. */
    val isNext: Boolean,
    val explanation: String?
) {
    val id: String get() = planned.block.id
    val availableEvents: Set<ActivityEvent> get() = ActivityStateMachine.availableEvents(planned.state)
}

/** A stretch of the day, with the work planned inside it. */
data class TimelineSection(
    val cycle: DayCycle,
    val entries: List<TimelineEntry>
)

data class TimelineUiState(
    val date: LocalDate = LocalDate.now(),
    val displayName: String? = null,
    val dayWindow: DayWindow = DayWindow.Default,
    val sections: List<TimelineSection> = emptyList(),
    /** Work NESA could not place today. Never deleted, always surfaced. */
    val needingAttention: List<TimelineEntry> = emptyList(),
    val loading: Boolean = true
) {
    val isToday: Boolean get() = date == LocalDate.now()
    val isEmpty: Boolean get() = sections.isEmpty() && needingAttention.isEmpty()
    val nextEntry: TimelineEntry?
        get() = sections.asSequence().flatMap { it.entries }.firstOrNull { it.isNext }
    val completedCount: Int
        get() = sections.sumOf { section ->
            section.entries.count { it.planned.state == ActivityState.COMPLETED }
        }
    val totalCount: Int get() = sections.sumOf { it.entries.size } + needingAttention.size
}

/**
 * The timeline.
 *
 * It renders entirely from the local database, so it works with no network and
 * survives a restart unchanged. Replanning is a write to that database rather
 * than a piece of view state, which is what keeps the screen, the notification
 * and the background worker from ever disagreeing about the plan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
    private val planner: DayPlanner,
    private val actions: ActivityActionHandler,
    private val clock: Clock
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<TimelineUiState> = selectedDate
        .flatMapLatest { date ->
            combine(
                activities.observePlan(date),
                settings.settings
            ) { items, preferences -> buildState(date, items, preferences) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TimelineUiState(date = selectedDate.value)
        )

    init {
        refresh()
    }

    fun onDateChanged(date: LocalDate) {
        selectedDate.value = date
        refresh()
    }

    fun onPreviousDay() = onDateChanged(selectedDate.value.minusDays(1))

    fun onNextDay() = onDateChanged(selectedDate.value.plusDays(1))

    fun onToday() = onDateChanged(LocalDate.now(clock))

    /** Re-runs the scheduler for the selected day and persists the result. */
    fun refresh() {
        viewModelScope.launch {
            runCatching { planner.refresh(selectedDate.value) }
        }
    }

    fun onEvent(blockId: String, event: ActivityEvent, note: String? = null) {
        viewModelScope.launch {
            actions.apply(blockId, event, note)
            // Completing or skipping frees time, so the rest of the day is
            // replanned immediately rather than at the next background run.
            runCatching { planner.refresh(selectedDate.value) }
        }
    }

    fun onDelete(activityId: String) {
        viewModelScope.launch {
            activities.deleteActivity(activityId)
            runCatching { planner.refresh(selectedDate.value) }
        }
    }

    private fun buildState(
        date: LocalDate,
        items: List<PlannedActivity>,
        preferences: NesaSettings
    ): TimelineUiState {
        val window = preferences.dayWindow
        val nextId = nextUpId(date, items, window)

        val (unplaceable, placed) = items.partition { it.needsANewSlot() }

        val sections = placed
            .sortedBy { it.block.startMinuteOfDay }
            .groupBy { window.cycleAtMinute(it.block.startMinuteOfDay) }
            .toSortedMap()
            .map { (cycle, cycleItems) ->
                TimelineSection(
                    cycle = cycle,
                    entries = cycleItems.map { it.toEntry(isNext = it.block.id == nextId) }
                )
            }

        return TimelineUiState(
            date = date,
            displayName = preferences.displayName,
            dayWindow = window,
            sections = sections,
            needingAttention = unplaceable.map { it.toEntry(isNext = false) },
            loading = false
        )
    }

    /**
     * True when the scheduler gave up on placing this today. The block still
     * exists with its old times, so showing it on the timeline would be a lie;
     * it belongs in the "needs a slot" group instead.
     */
    private fun PlannedActivity.needsANewSlot(): Boolean =
        block.state == ActivityState.LATER &&
            (block.changeReason is ChangeReason.NoRoomToday ||
                block.changeReason is ChangeReason.DeferredToAnotherDay)

    /**
     * What to do next: whatever is running, or else the first unresolved
     * activity that has not already gone by.
     */
    private fun nextUpId(date: LocalDate, items: List<PlannedActivity>, window: DayWindow): String? {
        items.firstOrNull { it.state == ActivityState.ACTIVE }?.let { return it.block.id }

        val fromMinute = if (date == LocalDate.now(clock)) {
            DayWindow.minuteOf(LocalTime.now(clock))
        } else {
            window.wakeMinute
        }

        return items
            .filter { it.state.needsPlacement && !it.needsANewSlot() }
            .filter { it.block.endMinuteOfDay >= fromMinute }
            .minByOrNull { it.block.startMinuteOfDay }
            ?.block?.id
    }

    private fun PlannedActivity.toEntry(isNext: Boolean) = TimelineEntry(
        planned = this,
        isNext = isNext,
        explanation = block.changeReason
            ?.takeIf { it !is ChangeReason.Unchanged }
            ?.explain()
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
