package com.nesa.feature.life

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.Flexibility
import com.nesa.core.model.LifeSchedule
import com.nesa.core.model.LifeScheduleKind
import com.nesa.core.model.Priority
import com.nesa.core.model.ScheduleEntry
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.LifeScheduleRepository
import com.nesa.core.scheduling.DayPlanner
import com.nesa.core.scheduling.LifeScheduleApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class ScheduleEditorUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val kind: LifeScheduleKind = LifeScheduleKind.CUSTOM,
    val enabled: Boolean = false,
    val entries: List<ScheduleEntry> = emptyList(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false
)

/**
 * Editing one life schedule.
 *
 * Saving reconciles the schedule with the activities it owns, through the same
 * single path the schedules list uses — entries the user removed have their
 * activities deleted, and the day is replanned. Two paths that both generated
 * activities is how a user ends up with two Works on a Monday, so there is one.
 */
@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(
    private val schedules: LifeScheduleRepository,
    private val activities: ActivityRepository,
    private val planner: DayPlanner,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scheduleId: String = checkNotNull(savedStateHandle[LifeRoutes.ARG_SCHEDULE_ID]) {
        "The schedule editor needs a schedule id"
    }

    /** The schedule as stored, so saving knows which entries went away. */
    private var original: LifeSchedule? = null

    private val _state = MutableStateFlow(ScheduleEditorUiState())
    val state: StateFlow<ScheduleEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val schedule = schedules.schedule(scheduleId) ?: return@launch
            original = schedule
            _state.update {
                it.copy(
                    loaded = true,
                    name = schedule.name,
                    kind = schedule.kind,
                    enabled = schedule.enabled,
                    entries = schedule.ordered
                )
            }
        }
    }

    fun onNameChanged(name: String) = _state.update { it.copy(name = name, nameError = false) }

    fun onEnabledChanged(enabled: Boolean) = _state.update { it.copy(enabled = enabled) }

    fun onAddEntry() = _state.update { current ->
        current.copy(
            entries = current.entries + ScheduleEntry(
                id = UUID.randomUUID().toString(),
                title = current.kind.let { kind -> defaultTitleFor(kind, current.entries.size) },
                days = setOf(DayOfWeek.MONDAY),
                start = LocalTime.of(9, 0),
                duration = current.kind.defaultDuration,
                priority = current.kind.defaultPriority,
                flexibility = current.kind.defaultFlexibility
            )
        )
    }

    fun onEntryTitleChanged(index: Int, title: String) = updateEntry(index) {
        // Blank would fail the domain's own check on the way back in, so it is
        // held as a placeholder here and the save filters it out.
        it.copy(title = title.ifBlank { " " })
    }

    fun onEntryStartChanged(index: Int, start: LocalTime) = updateEntry(index) { it.copy(start = start) }

    fun onEntryDurationChanged(index: Int, minutes: Int) = updateEntry(index) {
        it.copy(duration = Duration.ofMinutes(minutes.coerceIn(MIN_MINUTES, MAX_MINUTES).toLong()))
    }

    fun onEntryDayToggled(index: Int, day: DayOfWeek) = updateEntry(index) { entry ->
        val days = if (day in entry.days) entry.days - day else entry.days + day
        // An entry that happens on no day is rejected by the domain, so the last
        // day cannot be removed. Silently keeping it beats an error the user did
        // not ask for.
        entry.copy(days = days.ifEmpty { entry.days })
    }

    fun onEntryPriorityChanged(index: Int, priority: Priority) =
        updateEntry(index) { it.copy(priority = priority) }

    fun onEntryFlexibilityChanged(index: Int, flexibility: Flexibility) =
        updateEntry(index) { it.copy(flexibility = flexibility) }

    fun onRemoveEntry(index: Int) = _state.update { current ->
        current.copy(entries = current.entries.filterIndexed { i, _ -> i != index })
    }

    fun onSave() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        if (current.saving) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val schedule = LifeSchedule(
                id = scheduleId,
                name = current.name.trim(),
                kind = current.kind,
                enabled = current.enabled,
                // Entries the user added and never named are dropped rather than
                // saved blank, which would show as an empty row forever.
                entries = current.entries.filter { it.title.isNotBlank() }
            )

            runCatching { apply(schedule) }
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    /**
     * Writes the schedule and brings its activities into line.
     *
     * Deliberately the same reconciliation as `LifeSchedulesViewModel.applySchedule`.
     * If a third caller ever needs it, that is the moment to move it into a use
     * case in `core-scheduling` rather than copy it a third time.
     */
    private suspend fun apply(schedule: LifeSchedule) {
        schedules.save(schedule)

        val nowOwned = LifeScheduleApplier.activityIdsFor(schedule).toSet()
        val wasOwned = original?.let { LifeScheduleApplier.activityIdsFor(it) }.orEmpty().toSet()
        (wasOwned - nowOwned).forEach { activities.deleteActivity(it) }

        val today = LocalDate.now(clock)
        if (schedule.enabled) {
            LifeScheduleApplier.activitiesFor(schedule, Instant.now(clock), today).forEach {
                activities.saveActivity(it)
            }
        } else {
            nowOwned.forEach { activities.deleteActivity(it) }
        }

        original = schedule
        runCatching { planner.refresh(today) }
    }

    private fun defaultTitleFor(kind: LifeScheduleKind, existing: Int): String =
        if (existing == 0) kind.name.lowercase().replaceFirstChar { it.uppercase() } else ""

    private fun updateEntry(index: Int, block: (ScheduleEntry) -> ScheduleEntry) =
        _state.update { current ->
            if (index !in current.entries.indices) return@update current
            current.copy(
                entries = current.entries.mapIndexed { i, entry ->
                    if (i == index) block(entry) else entry
                }
            )
        }

    private companion object {
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 16 * 60
    }
}
