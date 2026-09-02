package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.HistoryRepository
import java.time.Clock
import java.time.Instant

/**
 * Applies a user's decision about an activity.
 *
 * There is exactly one of these so that a tap on a notification, a tap on the
 * timeline and a future AI-proposed action all go through the same rules and
 * leave the same history behind. The state machine decides what is legal; this
 * class decides what gets written down.
 */
class ActivityActionHandler(
    private val activities: ActivityRepository,
    private val history: HistoryRepository,
    private val clock: Clock,
    private val idFactory: () -> String
) {

    /**
     * @return the state the activity ended in, or null when the decision was
     *   not legal from its current state — which is not an error: two taps on
     *   "done" should not become two completions.
     */
    suspend fun apply(blockId: String, event: ActivityEvent, note: String? = null): ActivityState? {
        val item = activities.findBlock(blockId) ?: return null
        val next = ActivityStateMachine.tryApply(item.state, event) ?: return null

        activities.updateBlockState(blockId, next)

        val outcome = when (next) {
            ActivityState.COMPLETED -> CompletionResult.COMPLETED
            ActivityState.SKIPPED -> CompletionResult.SKIPPED
            ActivityState.CANCELLED -> CompletionResult.CANCELLED
            // LATER, ACTIVE and UPCOMING are not outcomes: the activity is
            // still in play, and history records what happened, not what is
            // still pending.
            else -> null
        }

        if (outcome != null) {
            history.record(
                CompletionRecord(
                    id = idFactory(),
                    activityId = item.activity.id,
                    blockId = blockId,
                    date = item.block.date,
                    result = outcome,
                    recordedAt = Instant.now(clock),
                    note = note
                )
            )
        }

        return next
    }
}
