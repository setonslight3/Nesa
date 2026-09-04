package com.nesa.core.scheduling

import com.nesa.core.model.Activity
import com.nesa.core.model.ScheduleBlock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Turns recurring activities into the blocks a given day is missing.
 *
 * A pure function, like [AdaptiveScheduler]: it decides *that* a block should
 * exist and where the user would like it, and then hands over. The scheduler
 * still decides where it actually goes, so a recurring activity is placed by
 * exactly the same rules as one the user added by hand — anchors protected,
 * conflicts resolved, overflow into the evening.
 *
 * Keeping it separate from the scheduler is what stops recurrence from becoming
 * a special case inside placement logic that is already the hardest part of the
 * product to reason about.
 */
object RecurrenceMaterialiser {

    /**
     * The blocks [date] is missing.
     *
     * @param activities every activity NESA knows about. Ones that do not repeat,
     *   and ones that already have a block on [date], produce nothing.
     * @param existingActivityIds the activities already placed on [date].
     * @param idFactory supplied rather than called directly so the result is
     *   reproducible in tests, the same way [DayPlanner] takes one.
     * @return new, unplaced blocks at each activity's preferred time. The
     *   scheduler moves them from there.
     */
    fun blocksFor(
        date: LocalDate,
        activities: List<Activity>,
        existingActivityIds: Set<String>,
        idFactory: () -> String
    ): List<ScheduleBlock> = activities
        .filter { it.repeats }
        // Idempotence is the whole safety property here. This runs on every
        // refresh of the day — on launch, on a background pass, after every
        // edit — and a duplicate block would be a duplicate reminder and a
        // duplicate line on the timeline that the user cannot tell apart.
        .filterNot { it.id in existingActivityIds }
        .filter { it.recurrence.occursOn(date) }
        .map { activity -> newBlock(activity, date, idFactory()) }

    private fun newBlock(activity: Activity, date: LocalDate, id: String): ScheduleBlock {
        // Where the user asked for it, or the start of the day if they did not
        // say. Either way this is a request, not a placement: AdaptiveScheduler
        // is what decides, and it will move this if the day says otherwise.
        val start = activity.preferredStart ?: LocalTime.MIDNIGHT
        val end = start.plusMinutes(activity.duration.toMinutes())

        return ScheduleBlock(
            id = id,
            activityId = activity.id,
            date = date,
            start = start,
            // A day never crosses midnight (see DayWindow). An activity whose
            // preferred start leaves it hanging over the end of the day is
            // clamped here so the block is constructible at all; the scheduler
            // then finds it a real slot or returns it as an UnplacedItem, which
            // is how the user gets told rather than silently losing it.
            end = if (end.isAfter(start)) end else LocalTime.MAX.withNano(0)
        )
    }
}
