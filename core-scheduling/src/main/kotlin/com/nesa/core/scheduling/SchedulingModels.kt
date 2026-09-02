package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.ChangeReason
import com.nesa.core.model.DayCycle
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Flexibility
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Everything the scheduler needs to plan one day. The engine is a pure function
 * of this input: no clock, no database, no Android.
 */
data class ScheduleRequest(
    val date: LocalDate,
    val items: List<PlannedActivity>,
    val dayWindow: DayWindow = DayWindow.Default,
    /**
     * The current time. When it falls on [date] the engine will not plan into
     * the past. Null means "plan the whole day", which is what a preview of a
     * future day wants.
     */
    val now: LocalDateTime? = null
)

/** One activity placed on the timeline, with the reason it sits there. */
data class Placement(
    val blockId: String,
    val activityId: String,
    val title: String,
    val start: LocalTime,
    val end: LocalTime,
    val state: ActivityState,
    val priority: Priority,
    val flexibility: Flexibility,
    val isAnchor: Boolean,
    val deadline: LocalDateTime?,
    val cycle: DayCycle,
    val changeReason: ChangeReason,
    /** True when the engine chose a different time than the block already had. */
    val moved: Boolean
) {
    val startMinute: Int get() = DayWindow.minuteOf(start)
    val endMinute: Int get() = DayWindow.minuteOf(end)
}

/**
 * Something that could not be placed today. It is never deleted — it is handed
 * back so the user (or Stage 2's day-to-day rescheduling) can decide.
 */
data class UnplacedItem(
    val blockId: String,
    val activityId: String,
    val title: String,
    val priority: Priority,
    val flexibility: Flexibility,
    val reason: ChangeReason
)

/** Two fixed commitments that overlap. NESA reports these instead of picking a winner. */
data class AnchorConflict(
    val firstBlockId: String,
    val firstTitle: String,
    val secondBlockId: String,
    val secondTitle: String
)

/** Whether the produced plan actually works. */
enum class Feasibility {
    /** Everything fits and every constraint holds. */
    FULLY_FEASIBLE,

    /** The plan is usable, but something was deferred, dropped, or is at risk. */
    PARTIALLY_FEASIBLE,

    /** A protected commitment could not be honoured. The user must intervene. */
    INFEASIBLE
}

/** The proposed timeline for one day. */
data class ScheduleResult(
    val date: LocalDate,
    val placements: List<Placement>,
    val unplaced: List<UnplacedItem>,
    val conflicts: List<AnchorConflict>,
    val feasibility: Feasibility
) {
    /** Placements the engine actually moved, in timeline order. */
    val movedPlacements: List<Placement> get() = placements.filter { it.moved }

    /** Human-readable explanations for every meaningful automatic change. */
    fun explanations(): List<String> = buildList {
        movedPlacements.forEach { add("${it.title}: ${it.changeReason.explain()}") }
        unplaced.forEach { add("${it.title}: ${it.reason.explain()}") }
        conflicts.forEach {
            add("${it.firstTitle} and ${it.secondTitle} are both fixed and overlap.")
        }
    }

    fun placementFor(blockId: String): Placement? = placements.firstOrNull { it.blockId == blockId }
}
