package com.nesa.feature.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.scheduling.ActivityActionHandler
import com.nesa.core.scheduling.ActivityEvent
import com.nesa.core.scheduling.DayPlanner
import com.nesa.core.scheduling.NightReview
import com.nesa.core.scheduling.NightReviewResult
import com.nesa.core.scheduling.RescheduleSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class NightReviewUiState(
    val review: NightReviewResult? = null,
    val loading: Boolean = true
)

/**
 * The night review: what happened today, and what to do about what did not.
 *
 * Every suggestion the screen shows comes from [NightReview], and every action
 * the user takes goes through [ActivityActionHandler] or [DayPlanner]. Nothing
 * is decided here — this class fetches two days, hands them to the domain, and
 * carries the answer back. That is what keeps the review's behaviour testable
 * on a JVM and identical to a decision taken from the timeline.
 */
@HiltViewModel
class NightReviewViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
    private val actions: ActivityActionHandler,
    private val planner: DayPlanner,
    private val clock: Clock
) : ViewModel() {

    private val _state = MutableStateFlow(NightReviewUiState())
    val state: StateFlow<NightReviewUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val result = runCatching {
                NightReview.of(
                    date = today,
                    today = activities.plan(today),
                    tomorrow = activities.plan(today.plusDays(1)),
                    window = settings.current().dayWindow,
                    now = LocalTime.now(clock)
                )
            }.getOrNull()
            _state.update { it.copy(review = result, loading = false) }
        }
    }

    /**
     * Accepts a suggestion.
     *
     * A suggestion is only ever carried out on an explicit tap. NESA proposing
     * a new home for a missed activity and then moving it anyway would make the
     * review something that happens *to* the user rather than something they do.
     */
    fun onAccept(item: PlannedActivity, suggestion: RescheduleSuggestion) {
        viewModelScope.launch {
            runCatching {
                when (suggestion) {
                    is RescheduleSuggestion.LaterToday -> moveTo(item, LocalDate.now(clock), suggestion.start)
                    is RescheduleSuggestion.Tomorrow -> moveTo(item, suggestion.date, suggestion.start)
                    // Letting something go is a skip: a decision the user made,
                    // which is exactly what SKIPPED means and why it must not be
                    // recorded as a miss.
                    RescheduleSuggestion.LetItGo -> actions.apply(item.block.id, ActivityEvent.SKIP)
                    is RescheduleSuggestion.NoRoom -> Unit
                }
            }
            refresh()
        }
    }

    /** Leaves it where it is, and stops offering. */
    fun onDismiss(item: PlannedActivity) {
        viewModelScope.launch {
            runCatching { actions.apply(item.block.id, ActivityEvent.SKIP) }
            refresh()
        }
    }

    private suspend fun moveTo(item: PlannedActivity, date: LocalDate, start: LocalTime) {
        val minutes = item.activity.duration.toMinutes()
        activities.updateBlocks(
            listOf(
                item.block.copy(
                    date = date,
                    start = start,
                    end = start.plusMinutes(minutes),
                    // Back to upcoming: it is on the plan again, and leaving it
                    // MISSED would have the detector re-record it tomorrow.
                    state = com.nesa.core.model.ActivityState.UPCOMING
                )
            )
        )
        // Replan the day it landed on, so it settles among that day's anchors
        // rather than sitting wherever the suggestion put it.
        runCatching { planner.refresh(date) }
    }
}
