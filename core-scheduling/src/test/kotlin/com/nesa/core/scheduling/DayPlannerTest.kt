package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.CompletionResult
import com.nesa.core.model.Flexibility
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.Priority
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DayPlannerTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun clockAt(time: LocalDateTime): Clock =
        Clock.fixed(time.toInstant(ZoneOffset.UTC), zone)

    private fun planner(
        activities: FakeActivityRepository,
        history: FakeHistoryRepository = FakeHistoryRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(
            NesaSettings.Default.copy(dayWindow = TestWindow)
        ),
        now: LocalDateTime
    ) = DayPlanner(
        activities = activities,
        history = history,
        settings = settings,
        clock = clockAt(now),
        idFactory = { "record-1" }
    )

    @Test
    fun `an unanswered activity becomes missed and is recorded`() = runTest {
        val activities = FakeActivityRepository(listOf(planned("stretch", at(9, 0), 30)))
        val history = FakeHistoryRepository()

        planner(activities, history, now = on(11, 0)).refresh(TestDate)

        assertEquals(CompletionResult.MISSED, history.records.single().result)
        assertEquals("stretch", history.records.single().blockId)
    }

    @Test
    fun `a missed activity is replanned into the remaining day`() = runTest {
        val activities = FakeActivityRepository(listOf(planned("stretch", at(9, 0), 30)))

        val result = planner(activities, now = on(11, 0)).refresh(TestDate)

        assertEquals(at(11, 0), result.placementFor("stretch")?.start)
        assertEquals(ActivityState.MISSED, activities.block("stretch")?.state)
        assertEquals("the new placement is persisted", at(11, 0), activities.block("stretch")?.start)
    }

    @Test
    fun `a completed activity is never turned into a missed one`() = runTest {
        val activities = FakeActivityRepository(
            listOf(planned("breakfast", at(8, 0), 30, state = ActivityState.COMPLETED))
        )
        val history = FakeHistoryRepository()

        planner(activities, history, now = on(12, 0)).refresh(TestDate)

        assertTrue(history.records.isEmpty())
        assertEquals(ActivityState.COMPLETED, activities.block("breakfast")?.state)
    }

    @Test
    fun `a deliberate skip stays a skip`() = runTest {
        val activities = FakeActivityRepository(
            listOf(planned("run", at(8, 0), 30, state = ActivityState.SKIPPED))
        )
        val history = FakeHistoryRepository()

        planner(activities, history, now = on(12, 0)).refresh(TestDate)

        assertTrue("a skip is already an answer", history.records.isEmpty())
        assertEquals(ActivityState.SKIPPED, activities.block("run")?.state)
    }

    @Test
    fun `a plan that already works is written back unchanged`() = runTest {
        val activities = FakeActivityRepository(listOf(planned("study", at(14, 0), 60)))
        val before = activities.block("study")

        planner(activities, now = on(9, 0)).refresh(TestDate)

        assertEquals(before, activities.block("study"))
    }

    @Test
    fun `anchors survive a refresh and flexible work moves around them`() = runTest {
        val activities = FakeActivityRepository(
            listOf(
                planned("lecture", at(9, 0), 120, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
                planned("gym", at(9, 30), 60)
            )
        )

        planner(activities, now = on(8, 0)).refresh(TestDate)

        assertEquals(at(9, 0), activities.block("lecture")?.start)
        assertEquals(at(11, 0), activities.block("gym")?.start)
    }

    @Test
    fun `work that no longer fits is kept and marked for later`() = runTest {
        val activities = FakeActivityRepository(
            listOf(
                planned("work", at(7, 0), 960, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
                planned("chores", at(12, 0), 120)
            )
        )

        val result = planner(activities, now = on(7, 0)).refresh(TestDate)

        assertEquals(ActivityState.LATER, activities.block("chores")?.state)
        assertTrue(result.unplaced.any { it.blockId == "chores" })
    }
}
