package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.CompletionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

class ActivityActionHandlerTest {

    private val clock: Clock = Clock.fixed(on(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private fun handler(
        activities: FakeActivityRepository,
        history: FakeHistoryRepository
    ) = ActivityActionHandler(activities, history, clock) { "record" }

    @Test
    fun `completing an activity records a completion`() = runTest {
        val activities = FakeActivityRepository(listOf(planned("study", at(9, 0), 60)))
        val history = FakeHistoryRepository()

        val result = handler(activities, history).apply("study", ActivityEvent.COMPLETE)

        assertEquals(ActivityState.COMPLETED, result)
        assertEquals(CompletionResult.COMPLETED, history.records.single().result)
    }

    @Test
    fun `skipping records a skip and keeps the reason the user gave`() = runTest {
        val activities = FakeActivityRepository(listOf(planned("run", at(9, 0), 60)))
        val history = FakeHistoryRepository()

        handler(activities, history).apply("run", ActivityEvent.SKIP, note = "Injured")

        val record = history.records.single()
        assertEquals(CompletionResult.SKIPPED, record.result)
        assertEquals("Injured", record.note)
    }

    @Test
    fun `deferring is not an outcome and writes no history`() = runTest {
        val activities = FakeActivityRepository(listOf(planned("chores", at(9, 0), 30)))
        val history = FakeHistoryRepository()

        val result = handler(activities, history).apply("chores", ActivityEvent.DEFER)

        assertEquals(ActivityState.LATER, result)
        assertTrue("the activity is still in play", history.records.isEmpty())
    }

    @Test
    fun `a second tap on a finished activity changes nothing`() = runTest {
        val activities = FakeActivityRepository(listOf(planned("study", at(9, 0), 60)))
        val history = FakeHistoryRepository()
        val handler = handler(activities, history)

        handler.apply("study", ActivityEvent.COMPLETE)
        val second = handler.apply("study", ActivityEvent.START)

        assertNull(second)
        assertEquals(1, history.records.size)
        assertEquals(ActivityState.COMPLETED, activities.block("study")?.state)
    }

    @Test
    fun `an unknown block is ignored rather than throwing`() = runTest {
        val handler = handler(FakeActivityRepository(), FakeHistoryRepository())
        assertNull(handler.apply("nothing-here", ActivityEvent.COMPLETE))
    }

    @Test
    fun `a missed activity can still be completed late`() = runTest {
        val activities = FakeActivityRepository(
            listOf(planned("physio", at(7, 0), 30, state = ActivityState.MISSED))
        )
        val history = FakeHistoryRepository()

        val result = handler(activities, history).apply("physio", ActivityEvent.COMPLETE)

        assertEquals(ActivityState.COMPLETED, result)
        assertEquals(CompletionResult.COMPLETED, history.records.single().result)
    }
}
