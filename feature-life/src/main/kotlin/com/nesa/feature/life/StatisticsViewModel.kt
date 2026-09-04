package com.nesa.feature.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.scheduling.DayStatistics
import com.nesa.core.scheduling.PlanStatistics
import com.nesa.core.scheduling.WeekStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class StatisticsUiState(
    val today: DayStatistics? = null,
    val week: WeekStatistics? = null,
    val lastWeek: WeekStatistics? = null
) {
    val hasAnything: Boolean get() = week?.hasData == true || lastWeek?.hasData == true
}

/**
 * Daily and weekly figures.
 *
 * Every number comes from [PlanStatistics]. Nothing is computed here, so the
 * figure on the screen is the figure a test asserts — the same rule the fitness
 * module follows for its streak.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    history: HistoryRepository,
    clock: Clock
) : ViewModel() {

    val state: StateFlow<StatisticsUiState> = run {
        val today = LocalDate.now(clock)
        val thisMonday = today.with(DayOfWeek.MONDAY)
        // Two weeks, so "this week against last" is possible without reading a
        // history nobody is looking at.
        history.observeRecords(thisMonday.minusWeeks(1), today)
            .map { records ->
                StatisticsUiState(
                    today = PlanStatistics.forDay(records, today),
                    week = PlanStatistics.forWeek(records, thisMonday),
                    lastWeek = PlanStatistics.forWeek(records, thisMonday.minusWeeks(1))
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = StatisticsUiState()
            )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
