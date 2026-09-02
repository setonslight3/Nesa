package com.nesa.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nesa.core.scheduling.ActivityActionHandler
import com.nesa.core.scheduling.ActivityEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Applies the decision the user tapped on a reminder.
 *
 * It decides nothing itself: it translates a tap into an [ActivityEvent] and
 * hands it to the same [ActivityActionHandler] the timeline uses. A skip taken
 * here is therefore identical to a skip taken in the app — recorded the same
 * way, and still distinct from a miss.
 */
@AndroidEntryPoint
class ActivityActionReceiver : BroadcastReceiver() {

    @Inject lateinit var actions: ActivityActionHandler
    @Inject lateinit var notifier: NesaNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val action = ActivityAction.fromIntentAction(intent.action) ?: return
        val blockId = intent.getStringExtra(EXTRA_BLOCK_ID) ?: return

        // The work outlives onReceive, so hold the broadcast open while it runs.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                actions.apply(blockId, action.event)
            } catch (error: Exception) {
                Log.w(TAG, "Could not apply $action to block $blockId", error)
            } finally {
                notifier.cancel(blockId)
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_BLOCK_ID = "com.nesa.extra.BLOCK_ID"
        private const val TAG = "NesaActivityAction"
    }
}
