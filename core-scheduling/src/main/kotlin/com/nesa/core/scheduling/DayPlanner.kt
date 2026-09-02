package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.CompletionRecord
import com.nesa.core.model.CompletionResult
import com.nesa.core.model.GuidancePersonality
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.model.repository.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Brings one day up to date: notices what was missed, replans what is left, and
 * writes the result back.
 *
 * This is the only place that turns silence into [ActivityState.MISSED], and it
 * is deliberately a plain class taking repository interfaces rather than an
 * Android component, so the whole recovery behaviour can be tested on a JVM.
 * The application module is what constructs it.
 *
 * @param idFactory supplied so history records are reproducible in tests.
 */
class DayPlanner(
    private val activities: ActivityRepository,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val idFactory: () -> String
) {

    /**
     * Recomputes [date] and persists any change.
     *
     * @return the plan NESA settled on, including the reasons for every move.
     */
    suspend fun refresh(date: LocalDate): ScheduleResult {
        val preferences = settings.current()
        val now = LocalDateTime.now(clock)
        val stored = activities.plan(date)

        val afterMisses = recordMisses(stored, now, preferences.guidance)

        val result = AdaptiveScheduler.schedule(
            ScheduleRequest(
                date = date,
                items = afterMisses,
                dayWindow = preferences.dayWindow,
                now = now
            )
        )

        // Only write blocks that actually changed: a no-op write would wake
        // every observer of the timeline for nothing.
        val updated = AdaptiveScheduler.applyTo(afterMisses, result)
        val changed = updated.filterIndexed { index, block -> block != afterMisses[index].block }
        activities.updateBlocks(changed)

        return result
    }

    /**
     * Marks unanswered activities as missed and records them.
     *
     * Returns the same list with the new states applied, so the scheduler runs
     * against reality rather than against what was on disk a moment ago.
     */
    private suspend fun recordMisses(
        items: List<PlannedActivity>,
        now: LocalDateTime,
        guidance: GuidancePersonality
    ): List<PlannedActivity> {
        val missed = MissedActivityDetector.detect(items, now, guidance).map { it.block.id }.toSet()
        if (missed.isEmpty()) return items

        missed.forEach { blockId ->
            activities.updateBlockState(blockId, ActivityState.MISSED)
        }
        items.filter { it.block.id in missed }.forEach { item ->
            history.record(
                CompletionRecord(
                    id = idFactory(),
                    activityId = item.activity.id,
                    blockId = item.block.id,
                    date = item.block.date,
                    result = CompletionResult.MISSED,
                    recordedAt = Instant.now(clock)
                )
            )
        }

        return items.map { item ->
            if (item.block.id in missed) {
                item.copy(block = item.block.copy(state = ActivityState.MISSED))
            } else {
                item
            }
        }
    }
}
