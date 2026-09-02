package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.ChangeReason
import com.nesa.core.model.DayCycle
import com.nesa.core.model.DayWindow
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.Priority
import com.nesa.core.model.ScheduleBlock

/**
 * NESA's deterministic scheduling engine.
 *
 * It is a pure function: the same request always produces the same plan. AI,
 * when it arrives in Stage 4, proposes intents — it never schedules. This class
 * remains the only thing that decides where an activity actually goes.
 *
 * The rules it implements, in order of authority:
 *  1. Fixed anchors are never moved, and never silently overwritten.
 *  2. Nothing is planned into the past.
 *  3. Nothing is planned past the sleep target.
 *  4. Deadlines are preserved where possible, and flagged when they are not.
 *  5. Higher priority is placed first; flexible work fills the gaps around it.
 *  6. The evening absorbs overflow; the night is left for winding down.
 *  7. Nothing important is ever deleted — it comes back as an [UnplacedItem].
 *  8. The least disruptive plan wins: an activity that already fits stays put.
 */
object AdaptiveScheduler {

    /** An occupied slice of the day. */
    private data class Occupied(
        val start: Int,
        val end: Int,
        val title: String,
        val isAnchor: Boolean
    )

    fun schedule(request: ScheduleRequest): ScheduleResult {
        val window = request.dayWindow
        val nowMinute = currentMinuteFor(request)

        // A day that is already over is history: report it as it is rather than
        // rewriting what the user did.
        if (nowMinute == PAST_DAY) {
            return ScheduleResult(
                date = request.date,
                placements = request.items.map { it.toUnchangedPlacement(window) }.sortedBy { it.startMinute },
                unplaced = emptyList(),
                conflicts = emptyList(),
                feasibility = Feasibility.FULLY_FEASIBLE
            )
        }

        val earliest = maxOf(window.wakeMinute, nowMinute)
        val dayEnd = window.sleepMinute
        val nightStart = window.nightStartMinute

        // Skipped and cancelled work still shows on the timeline, but it no
        // longer owns any time.
        val (inactive, live) = request.items.partition {
            it.state == ActivityState.SKIPPED || it.state == ActivityState.CANCELLED
        }
        val (immovable, movable) = live.partition { it.isImmovable }

        val occupied = immovable
            .map {
                Occupied(
                    start = it.block.startMinuteOfDay,
                    end = it.block.endMinuteOfDay,
                    title = it.title,
                    isAnchor = it.activity.isAnchor || it.block.locked
                )
            }
            .toMutableList()

        val conflicts = detectAnchorConflicts(immovable)

        val placements = mutableListOf<Placement>()
        val unplaced = mutableListOf<UnplacedItem>()

        immovable.forEach { placements += it.toUnchangedPlacement(window) }
        inactive.forEach { placements += it.toUnchangedPlacement(window) }

        movable.sortedWith(placementOrder).forEach { item ->
            val duration = item.activity.durationMinutes
            val desired = desiredStartFor(item)
            val deadlineLimit = deadlineLimitFor(item, dayEnd)

            // Least disruption comes first: an activity that still works exactly
            // where it is stays exactly where it is, even if that is late in the
            // evening. Only work that has to move gets an opinion imposed on it.
            val fromDesired = maxOf(desired, earliest)
            val latest = minOf(deadlineLimit, dayEnd)
            val fits = desired >= earliest &&
                desired + duration <= latest &&
                isFree(occupied, desired, duration)

            // Otherwise widen the search in steps, giving up exactly one
            // preference at a time, so the first hit is the smallest concession.
            val beforeNight = minOf(deadlineLimit, nightStart)
            val searchWindows = listOf(
                fromDesired to beforeNight,   // at/after the wanted time, before the night
                earliest to beforeNight,      // earlier than wanted, still before the night
                fromDesired to deadlineLimit, // let the evening and then the night absorb it
                earliest to deadlineLimit,
                fromDesired to dayEnd,        // last resort: accept missing the deadline
                earliest to dayEnd
            )
            val chosen = if (fits) desired else searchWindows.firstNotNullOfOrNull { (from, until) ->
                findSlot(occupied, from, until, duration)
            }

            if (chosen == null) {
                unplaced += UnplacedItem(
                    blockId = item.block.id,
                    activityId = item.activity.id,
                    title = item.title,
                    priority = item.priority,
                    flexibility = item.flexibility,
                    reason = if (item.flexibility.movableAcrossDays) {
                        ChangeReason.DeferredToAnotherDay
                    } else {
                        ChangeReason.NoRoomToday
                    }
                )
                return@forEach
            }

            val blocker = blockerAt(occupied, maxOf(desired, earliest), duration)
            val missesDeadline = deadlineLimit < dayEnd && chosen + duration > deadlineLimit
            val moved = chosen != item.block.startMinuteOfDay
            val reason = when {
                missesDeadline -> ChangeReason.DeadlineAtRisk
                !moved -> ChangeReason.Unchanged
                else -> explainMove(item, desired, chosen, earliest, blocker, window)
            }

            occupied += Occupied(chosen, chosen + duration, item.title, isAnchor = false)
            placements += Placement(
                blockId = item.block.id,
                activityId = item.activity.id,
                title = item.title,
                start = DayWindow.timeOf(chosen),
                end = DayWindow.timeOf(chosen + duration),
                state = item.state,
                priority = item.priority,
                flexibility = item.flexibility,
                isAnchor = false,
                deadline = item.activity.deadline,
                cycle = window.cycleAtMinute(chosen),
                changeReason = reason,
                moved = moved
            )
        }

        return ScheduleResult(
            date = request.date,
            placements = placements.sortedWith(compareBy({ it.startMinute }, { it.title })),
            unplaced = unplaced,
            conflicts = conflicts,
            feasibility = feasibilityOf(placements, unplaced, conflicts)
        )
    }

    /**
     * Rewrites the blocks of [items] to match [result]. Anything the engine
     * could not place is marked [ActivityState.LATER] rather than deleted, so
     * it stays visible and recoverable.
     */
    fun applyTo(items: List<PlannedActivity>, result: ScheduleResult): List<ScheduleBlock> =
        items.map { item ->
            val placement = result.placementFor(item.block.id)
            if (placement != null) {
                item.block.copy(
                    start = placement.start,
                    end = placement.end,
                    changeReason = placement.changeReason.takeIf { placement.moved }
                        ?: item.block.changeReason
                )
            } else {
                val unplacedItem = result.unplaced.firstOrNull { it.blockId == item.block.id }
                if (unplacedItem == null) {
                    item.block
                } else {
                    item.block.copy(
                        state = if (item.state.needsPlacement) ActivityState.LATER else item.state,
                        changeReason = unplacedItem.reason
                    )
                }
            }
        }

    // --- internals ----------------------------------------------------------

    private const val PAST_DAY = Int.MAX_VALUE

    /**
     * Orders the work the engine is free to move: importance first, then the
     * tightest deadline, then the user's preference, then a stable tiebreak so
     * the same input never produces two different plans.
     */
    private val placementOrder: Comparator<PlannedActivity> =
        compareBy<PlannedActivity> { it.priority.rank }
            .thenBy(nullsLast()) { it.activity.deadline }
            .thenBy { it.activity.preferredStart?.let(DayWindow::minuteOf) ?: Int.MAX_VALUE }
            .thenBy { it.block.startMinuteOfDay }
            .thenBy { it.block.id }

    private fun currentMinuteFor(request: ScheduleRequest): Int {
        val now = request.now ?: return 0
        return when {
            now.toLocalDate().isAfter(request.date) -> PAST_DAY
            now.toLocalDate().isBefore(request.date) -> 0
            else -> DayWindow.minuteOf(now.toLocalTime())
        }
    }

    /**
     * Where the activity would like to start: its explicit preference, or
     * failing that, wherever its block already sits. Defaulting to the current
     * placement is what makes a stable day stay stable.
     */
    private fun desiredStartFor(item: PlannedActivity): Int =
        item.activity.preferredStart?.let(DayWindow::minuteOf) ?: item.block.startMinuteOfDay

    private fun deadlineLimitFor(item: PlannedActivity, dayEnd: Int): Int {
        val deadline = item.activity.deadline ?: return dayEnd
        return when {
            deadline.toLocalDate().isAfter(item.block.date) -> dayEnd
            deadline.toLocalDate().isBefore(item.block.date) -> dayEnd
            else -> minOf(dayEnd, DayWindow.minuteOf(deadline.toLocalTime()))
        }
    }

    /**
     * Earliest free minute at or after [from] where [duration] minutes fit
     * entirely before [until], or null when no such gap exists.
     */
    private fun findSlot(occupied: List<Occupied>, from: Int, until: Int, duration: Int): Int? {
        if (duration <= 0 || from + duration > until) return null
        var candidate = from
        for (slot in occupied.sortedBy { it.start }) {
            if (slot.end <= candidate) continue
            if (slot.start >= candidate + duration) break
            candidate = maxOf(candidate, slot.end)
        }
        return if (candidate + duration <= until) candidate else null
    }

    /** True when [duration] minutes starting at [start] overlap nothing. */
    private fun isFree(occupied: List<Occupied>, start: Int, duration: Int): Boolean =
        occupied.none { it.start < start + duration && it.end > start }

    /** The thing sitting in the slot the activity actually wanted, if any. */
    private fun blockerAt(occupied: List<Occupied>, from: Int, duration: Int): Occupied? =
        occupied.filter { it.start < from + duration && it.end > from }.minByOrNull { it.start }

    private fun explainMove(
        item: PlannedActivity,
        desired: Int,
        chosen: Int,
        earliest: Int,
        blocker: Occupied?,
        window: DayWindow
    ): ChangeReason = when {
        item.state == ActivityState.MISSED -> ChangeReason.RecoveredFromMissed
        item.state == ActivityState.LATER -> ChangeReason.RescheduledOnRequest
        desired < earliest -> ChangeReason.MovedOutOfPast
        blocker != null && blocker.isAnchor -> ChangeReason.MovedForAnchor(blocker.title)
        blocker != null -> ChangeReason.MovedForPriority(blocker.title)
        window.cycleAtMinute(chosen) == DayCycle.EVENING &&
            window.cycleAtMinute(desired) != DayCycle.EVENING -> ChangeReason.MovedToEveningRecovery
        chosen < desired && item.activity.deadline != null -> ChangeReason.MovedForDeadline
        chosen < desired -> ChangeReason.MovedForSleepTarget
        else -> ChangeReason.Unchanged
    }

    private fun detectAnchorConflicts(immovable: List<PlannedActivity>): List<AnchorConflict> {
        val sorted = immovable
            .filter { it.activity.isAnchor }
            .sortedBy { it.block.startMinuteOfDay }
        val conflicts = mutableListOf<AnchorConflict>()
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (b.block.startMinuteOfDay >= a.block.endMinuteOfDay) break
                conflicts += AnchorConflict(
                    firstBlockId = a.block.id,
                    firstTitle = a.title,
                    secondBlockId = b.block.id,
                    secondTitle = b.title
                )
            }
        }
        return conflicts
    }

    private fun feasibilityOf(
        placements: List<Placement>,
        unplaced: List<UnplacedItem>,
        conflicts: List<AnchorConflict>
    ): Feasibility {
        val protectedLoss = unplaced.any { it.priority == Priority.CRITICAL }
        val atRisk = placements.any { it.changeReason is ChangeReason.DeadlineAtRisk }
        return when {
            protectedLoss || conflicts.isNotEmpty() -> Feasibility.INFEASIBLE
            unplaced.isNotEmpty() || atRisk -> Feasibility.PARTIALLY_FEASIBLE
            else -> Feasibility.FULLY_FEASIBLE
        }
    }

    private fun PlannedActivity.toUnchangedPlacement(window: DayWindow): Placement = Placement(
        blockId = block.id,
        activityId = activity.id,
        title = title,
        start = block.start,
        end = block.end,
        state = state,
        priority = priority,
        flexibility = flexibility,
        isAnchor = activity.isAnchor || block.locked,
        deadline = activity.deadline,
        cycle = window.cycleAtMinute(block.startMinuteOfDay),
        changeReason = ChangeReason.Unchanged,
        moved = false
    )
}
