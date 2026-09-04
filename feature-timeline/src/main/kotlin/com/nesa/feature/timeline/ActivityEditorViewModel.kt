package com.nesa.feature.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.Activity
import com.nesa.core.model.ActivityState
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Flexibility
import com.nesa.core.model.Priority
import com.nesa.core.model.Recurrence
import com.nesa.core.model.RecurrenceFrequency
import com.nesa.core.model.ScheduleBlock
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.scheduling.AdaptiveInsights
import com.nesa.core.scheduling.DayBand
import com.nesa.core.scheduling.DayPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class ActivityEditorUiState(
    val activityId: String? = null,
    val title: String = "",
    val notes: String = "",
    val start: LocalTime = LocalTime.of(9, 0),
    val durationMinutes: Int = 30,
    val priority: Priority = Priority.NORMAL,
    val flexibility: Flexibility = Flexibility.TIME_FLEXIBLE,
    val deadline: LocalTime? = null,
    val recurrence: Recurrence = Recurrence.Once,
    /**
     * The part of the day this activity is heading for, when history says that
     * part tends not to survive. Null means NESA has nothing worth saying —
     * which is the usual case and must stay the quiet one.
     */
    val weakBandWarning: DayBand? = null,
    val date: LocalDate = LocalDate.now(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val titleError: Boolean = false
) {
    val isEditing: Boolean get() = activityId != null
    val showsDeadline: Boolean get() = flexibility == Flexibility.DEADLINE_BASED

    /** The day chips are only meaningful for a rule that names days. */
    val showsRecurrenceDays: Boolean
        get() = recurrence.frequency == RecurrenceFrequency.WEEKLY
}

/**
 * Creating and editing an activity.
 *
 * The form asks four things beyond the name: when, how long, how important, and
 * whether NESA may move it. Those four are exactly what the scheduler needs —
 * nothing here exists to fill a screen.
 */
@HiltViewModel
class ActivityEditorViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val planner: DayPlanner,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editingId: String? = savedStateHandle[ActivityEditorRoutes.ARG_ACTIVITY_ID]

    private val _state = MutableStateFlow(ActivityEditorUiState(date = LocalDate.now(clock)))
    val state: StateFlow<ActivityEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // A new activity opens at the next sensible slot rather than at a
            // fixed hour the user then has to correct.
            val window = settings.current().dayWindow
            _state.update { it.copy(start = suggestedStart(window)) }

            if (editingId != null) load(editingId)
            refreshWeakBandWarning()
        }
    }

    fun onTitleChanged(title: String) =
        _state.update { it.copy(title = title, titleError = false) }

    fun onNotesChanged(notes: String) = _state.update { it.copy(notes = notes) }

    fun onStartChanged(start: LocalTime) {
        _state.update { it.copy(start = start) }
        viewModelScope.launch { refreshWeakBandWarning() }
    }

    /**
     * Warns when the chosen time lands in a part of the day this person's own
     * history says does not survive.
     *
     * A warning, never a move. NESA does not quietly relocate an activity
     * because a statistic disagreed with the user — that is the difference
     * between a planner people trust and one that feels like it is arguing with
     * them. AdaptiveInsights stays silent until it has enough evidence, so most
     * of the time this sets nothing.
     */
    private suspend fun refreshWeakBandWarning() {
        val chosen = _state.value.start
        val warning = runCatching {
            val window = settings.current().dayWindow
            val records = history
                .observeRecords(LocalDate.now(clock).minusDays(HISTORY_DAYS), LocalDate.now(clock))
                .first()
            val band = AdaptiveInsights.bandOf(chosen, window)
            band.takeIf { weak -> AdaptiveInsights.weakBands(records, window).any { it.band == weak } }
        }.getOrNull()

        _state.update { it.copy(weakBandWarning = warning) }
    }

    fun onDurationChanged(minutes: Int) =
        _state.update { it.copy(durationMinutes = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)) }

    fun onPriorityChanged(priority: Priority) = _state.update { it.copy(priority = priority) }

    fun onFlexibilityChanged(flexibility: Flexibility) = _state.update { current ->
        current.copy(
            flexibility = flexibility,
            // A deadline only means something for deadline-based work.
            deadline = if (flexibility == Flexibility.DEADLINE_BASED) {
                current.deadline ?: current.start.plusMinutes(current.durationMinutes.toLong())
            } else {
                null
            }
        )
    }

    fun onDeadlineChanged(deadline: LocalTime) = _state.update { it.copy(deadline = deadline) }

    /**
     * Anchored to the day being edited, so "every other week" counts from the
     * week the user is actually looking at rather than from an arbitrary date.
     */
    fun onRecurrenceChanged(recurrence: Recurrence) = _state.update { current ->
        current.copy(
            recurrence = if (recurrence.repeats) {
                recurrence.copy(startDate = recurrence.startDate ?: current.date)
            } else {
                recurrence
            }
        )
    }

    /**
     * Switches to "certain days", seeded with the day being edited.
     *
     * Separate from the preset chips because the seed depends on the date, which
     * a constant cannot know — and without it there is no way into the day
     * chips at all: they only show for a weekly rule, and nothing else would
     * create one.
     */
    fun onChooseDaysRequested() = _state.update { current ->
        if (current.recurrence.frequency == RecurrenceFrequency.WEEKLY) {
            current
        } else {
            current.copy(
                recurrence = Recurrence(
                    frequency = RecurrenceFrequency.WEEKLY,
                    daysOfWeek = setOf(current.date.dayOfWeek),
                    startDate = current.date
                )
            )
        }
    }

    fun onRecurrenceDayToggled(day: DayOfWeek) = _state.update { current ->
        val days = current.recurrence.daysOfWeek.let { if (day in it) it - day else it + day }
        current.copy(
            recurrence = if (days.isEmpty()) {
                // Un-ticking the last day means "stop repeating", not an
                // impossible weekly rule with nowhere to land.
                Recurrence.Once
            } else {
                current.recurrence.copy(
                    frequency = RecurrenceFrequency.WEEKLY,
                    daysOfWeek = days,
                    startDate = current.recurrence.startDate ?: current.date
                )
            }
        )
    }

    fun onSave() {
        val current = _state.value
        if (current.title.isBlank()) {
            _state.update { it.copy(titleError = true) }
            return
        }
        if (current.saving) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = Instant.now(clock)
            val activityId = current.activityId ?: UUID.randomUUID().toString()
            val existing = current.activityId?.let { activities.findBlock(blockIdFor(it)) }

            val activity = Activity(
                id = activityId,
                title = current.title.trim(),
                notes = current.notes.trim().takeIf { it.isNotBlank() },
                duration = Duration.ofMinutes(current.durationMinutes.toLong()),
                priority = current.priority,
                flexibility = current.flexibility,
                preferredStart = current.start,
                deadline = current.deadline?.let { LocalDateTime.of(current.date, it) },
                recurrence = current.recurrence,
                createdAt = existing?.activity?.createdAt ?: now,
                updatedAt = now
            )

            val block = ScheduleBlock(
                id = blockIdFor(activityId),
                activityId = activityId,
                date = current.date,
                start = current.start,
                end = endFor(current.start, current.durationMinutes),
                state = existing?.block?.state ?: ActivityState.UPCOMING,
                locked = existing?.block?.locked ?: false
            )

            activities.save(activity, block)
            // Saving is not scheduling: the engine decides where this actually
            // lands, and may move it to respect an anchor the user forgot about.
            runCatching { planner.refresh(current.date) }

            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    private suspend fun load(activityId: String) {
        val item = activities.findBlock(blockIdFor(activityId)) ?: return
        _state.update {
            it.copy(
                activityId = activityId,
                title = item.activity.title,
                notes = item.activity.notes.orEmpty(),
                start = item.block.start,
                durationMinutes = item.activity.durationMinutes,
                priority = item.activity.priority,
                flexibility = item.activity.flexibility,
                deadline = item.activity.deadline?.toLocalTime(),
                recurrence = item.activity.recurrence,
                date = item.block.date
            )
        }
    }

    /** The next half hour, kept inside the user's waking day. */
    private fun suggestedStart(window: DayWindow): LocalTime {
        val now = LocalTime.now(clock)
        val rounded = now.plusMinutes((SLOT_MINUTES - now.minute % SLOT_MINUTES).toLong())
            .withSecond(0)
            .withNano(0)
        val minute = DayWindow.minuteOf(rounded)
        return when {
            minute < window.wakeMinute -> window.wakeTime
            minute > window.sleepMinute - SLOT_MINUTES -> window.wakeTime
            else -> rounded
        }
    }

    /** A block never crosses midnight, so a late start is clamped to the day. */
    private fun endFor(start: LocalTime, minutes: Int): LocalTime {
        val end = DayWindow.minuteOf(start) + minutes
        return DayWindow.timeOf(end.coerceAtMost(DayWindow.END_OF_DAY_MINUTE))
    }

    /**
     * Stage 1 has one placement per activity, so the block id is derived rather
     * than stored. Stage 2's recurrence will generate real ids instead; the
     * repositories already take them as a parameter for that reason.
     */
    private fun blockIdFor(activityId: String) = "block-$activityId"

    private companion object {
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 12 * 60
        const val SLOT_MINUTES = 30

        /**
         * How far back the weak-band warning looks.
         *
         * Long enough to be evidence, short enough that a habit someone fixed
         * two months ago stops being held against them.
         */
        const val HISTORY_DAYS = 45L
    }
}
