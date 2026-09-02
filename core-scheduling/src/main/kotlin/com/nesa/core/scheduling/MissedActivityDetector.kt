package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.GuidancePersonality
import com.nesa.core.model.PlannedActivity
import java.time.Duration
import java.time.LocalDateTime

/**
 * Decides which activities have gone unanswered long enough to count as missed.
 *
 * Silence is never treated as a skip. An activity only becomes
 * [ActivityState.MISSED] after its planned end plus the grace period defined by
 * the user's [GuidancePersonality] — long enough that a late start is not
 * punished, short enough that the evening can still recover it.
 */
object MissedActivityDetector {

    fun detect(
        items: List<PlannedActivity>,
        now: LocalDateTime,
        guidance: GuidancePersonality = GuidancePersonality.Default
    ): List<PlannedActivity> {
        val grace = Duration.ofMinutes(guidance.missedGraceMinutes.toLong())
        return items.filter { item ->
            ActivityStateMachine.canApply(item.state, ActivityEvent.MISS) &&
                isOverdue(item, now, grace)
        }
    }

    /** True when the block's window, plus grace, has already elapsed. */
    fun isOverdue(item: PlannedActivity, now: LocalDateTime, grace: Duration): Boolean {
        val deadline = LocalDateTime.of(item.block.date, item.block.end).plus(grace)
        return !now.isBefore(deadline)
    }

    /**
     * Whether another reminder is due for an unresolved activity.
     *
     * Persistence means a bounded number of meaningful attempts, so this stops
     * once [GuidancePersonality.maxReminders] have gone out.
     */
    fun shouldRemind(
        item: PlannedActivity,
        now: LocalDateTime,
        guidance: GuidancePersonality = GuidancePersonality.Default
    ): Boolean {
        if (!item.state.needsPlacement) return false
        if (item.block.remindersSent >= guidance.maxReminders) return false
        val start = LocalDateTime.of(item.block.date, item.block.start)
        val nextReminderAt = start.plusMinutes(
            (item.block.remindersSent.toLong() * guidance.reminderIntervalMinutes)
        )
        return !now.isBefore(nextReminderAt) &&
            now.isBefore(LocalDateTime.of(item.block.date, item.block.end)
                .plusMinutes(guidance.missedGraceMinutes.toLong()))
    }
}
