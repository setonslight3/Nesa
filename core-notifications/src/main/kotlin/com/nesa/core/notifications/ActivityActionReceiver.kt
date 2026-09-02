package com.nesa.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nesa.core.model.ActivityState
import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.scheduling.ActivityEvent
import com.nesa.core.scheduling.ActivityStateMachine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Applies the decision the user tapped on a reminder.
 *
 * The receiver never decides anything itself: it translates a tap into an
 * [ActivityEvent] and lets [ActivityStateMachine] say whether that is legal.
 * A "skip" here is therefore the same skip the timeline produces, recorded the
 * same way, and still distinct from a miss.
 */
@AndroidEntryPoint
class ActivityActionReceiver : BroadcastReceiver() {

    @Inject lateinit var activities: ActivityRepository
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var notifier: NesaNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val action = ActivityAction.fromIntentAction(intent.action) ?: return
        val blockId = intent.getStringExtra(EXTRA_BLOCK_ID) ?: return

        // Work outlives onReceive, so hold the broadcast open while it runs.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                apply(action, blockId)
            } catch (error: Exception) {
                Log.w(TAG, "Could not apply $action to block", error)
            } finally {
                notifier.cancel(blockId)
                pending.finish()
            }
        }
    }

    private suspend fun apply(action: ActivityAction, blockId: String) {
        val item = activities.findBlock(blockId) ?: return
        val event = when (action) {
            ActivityAction.COMPLETE -> ActivityEvent.COMPLETE
            ActivityAction.LATER -> ActivityEvent.DEFER
            ActivityAction.SKIP -> ActivityEvent.SKIP
        }
        val next = ActivityStateMachine.tryApply(item.state, event) ?: return

        activities.updateBlockState(blockId, next)
        recordOutcome(item.activity.id, blockId, next, item.block.date)
    }

    private suspend fun recordOutcome(
        activityId: String,
        blockId: String,
        state: ActivityState,
        date: java.time.LocalDate
    ) {
        val result = when (state) {
            ActivityState.COMPLETED -> CompletionResult.COMPLETED
            ActivityState.SKIPPED -> CompletionResult.SKIPPED
            // "Do later" is not an outcome yet — the activity is still in play.
            else -> return
        }
        history.record(
            CompletionRecord(
                id = UUID.randomUUID().toString(),
                activityId = activityId,
                blockId = blockId,
                date = date,
                result = result,
                recordedAt = Instant.now()
            )
        )
    }

    companion object {
        const val EXTRA_BLOCK_ID = "com.nesa.extra.BLOCK_ID"
        private const val TAG = "NesaActivityAction"
    }
}
