package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.ChangeReason
import com.nesa.core.model.DayCycle
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Flexibility
import com.nesa.core.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class AdaptiveSchedulerTest {

    @Test
    fun `a day that already fits is left completely alone`() {
        val result = scheduleOf(
            planned("gym", at(7, 30), 60),
            planned("study", at(10, 0), 90),
            planned("errands", at(15, 0), 45)
        )

        assertEquals(at(7, 30), result.startOf("gym"))
        assertEquals(at(10, 0), result.startOf("study"))
        assertEquals(at(15, 0), result.startOf("errands"))
        assertTrue(result.movedPlacements.isEmpty())
        assertEquals(Feasibility.FULLY_FEASIBLE, result.feasibility)
    }

    @Test
    fun `a flexible activity moves out of a fixed anchor instead of the anchor moving`() {
        val result = scheduleOf(
            planned("standup", at(9, 0), 60, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("reading", at(9, 30), 30)
        )

        assertEquals("the anchor must not move", at(9, 0), result.startOf("standup"))
        assertEquals("the flexible activity moves after it", at(10, 0), result.startOf("reading"))

        val moved = result.placementFor("reading")!!
        assertTrue(moved.moved)
        assertEquals(ChangeReason.MovedForAnchor("standup"), moved.changeReason)
    }

    @Test
    fun `a locked block is protected exactly like a fixed anchor`() {
        val result = scheduleOf(
            planned("pinned", at(13, 0), 60, locked = true),
            planned("flexible", at(13, 15), 30)
        )

        assertEquals(at(13, 0), result.startOf("pinned"))
        assertEquals(at(14, 0), result.startOf("flexible"))
    }

    @Test
    fun `an anchor never moves even when a critical flexible activity wants its slot`() {
        val result = scheduleOf(
            planned("appointment", at(14, 0), 90, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("deep work", at(14, 0), 120, priority = Priority.CRITICAL)
        )

        assertEquals(at(14, 0), result.startOf("appointment"))
        assertEquals(at(15, 30), result.startOf("deep work"))
    }

    @Test
    fun `higher priority is placed before lower priority when they compete`() {
        val result = scheduleOf(
            planned("entertainment", at(10, 0), 60, priority = Priority.LOW),
            planned("project", at(10, 0), 60, priority = Priority.HIGH)
        )

        assertEquals(at(10, 0), result.startOf("project"))
        assertEquals(at(11, 0), result.startOf("entertainment"))
    }

    @Test
    fun `nothing is planned into the past`() {
        val result = scheduleOf(
            planned("workout", at(8, 0), 45),
            now = on(13, 0)
        )

        val placement = result.placementFor("workout")!!
        assertEquals(at(13, 0), placement.start)
        assertTrue(placement.moved)
        assertEquals(ChangeReason.MovedOutOfPast, placement.changeReason)
    }

    @Test
    fun `a completed activity keeps its slot and is never replanned`() {
        val result = scheduleOf(
            planned("breakfast", at(8, 0), 30, state = ActivityState.COMPLETED),
            planned("email", at(8, 0), 30),
            now = on(9, 0)
        )

        assertEquals(at(8, 0), result.startOf("breakfast"))
        assertFalse(result.placementFor("breakfast")!!.moved)
        assertEquals(at(9, 0), result.startOf("email"))
    }

    @Test
    fun `a skipped activity releases its time instead of blocking the day`() {
        val result = scheduleOf(
            planned("run", at(9, 0), 60, state = ActivityState.SKIPPED),
            planned("call", at(9, 0), 30)
        )

        assertEquals("a skip does not occupy the slot", at(9, 0), result.startOf("call"))
        assertEquals(ActivityState.SKIPPED, result.placementFor("run")!!.state)
    }

    @Test
    fun `a missed activity is recovered later in the day and says so`() {
        val result = scheduleOf(
            planned("physio", at(9, 0), 30, state = ActivityState.MISSED),
            now = on(15, 0)
        )

        val placement = result.placementFor("physio")!!
        assertEquals(at(15, 0), placement.start)
        assertEquals(ChangeReason.RecoveredFromMissed, placement.changeReason)
    }

    @Test
    fun `an activity the user deferred explains itself differently from a missed one`() {
        val result = scheduleOf(
            planned("tidy up", at(9, 0), 30, state = ActivityState.LATER),
            now = on(15, 0)
        )

        assertEquals(ChangeReason.RescheduledOnRequest, result.placementFor("tidy up")!!.changeReason)
    }

    @Test
    fun `overflow lands in the evening recovery window rather than the night`() {
        // The working day is completely booked by anchors, so the only room left
        // is the evening.
        val result = scheduleOf(
            planned("work", at(9, 0), 540, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("guitar", at(10, 0), 45)
        )

        val placement = result.placementFor("guitar")!!
        assertEquals(at(18, 0), placement.start)
        assertEquals(DayCycle.EVENING, placement.cycle)
    }

    @Test
    fun `the night window is only used once the evening is full`() {
        val result = scheduleOf(
            planned("work", at(7, 0), 660, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("evening filler", at(18, 0), 180, flexibility = Flexibility.FIXED),
            planned("journal", at(12, 0), 30)
        )

        val placement = result.placementFor("journal")!!
        assertEquals(at(21, 0), placement.start)
        assertEquals(DayCycle.NIGHT, placement.cycle)
    }

    @Test
    fun `the sleep target is never crossed`() {
        // Two hours of work with only one hour left before the 23:00 sleep
        // target: NESA gives up the activity, not the sleep.
        val result = scheduleOf(
            planned("marathon task", at(20, 0), 120),
            now = on(22, 0)
        )

        assertNull("it cannot fit before the sleep target", result.placementFor("marathon task"))
        assertTrue(result.isUnplaced("marathon task"))
    }

    @Test
    fun `an activity that already fits late in the evening is not dragged forward`() {
        val result = scheduleOf(planned("wind down", at(21, 30), 30))

        assertEquals(at(21, 30), result.startOf("wind down"))
        assertFalse(result.placementFor("wind down")!!.moved)
    }

    @Test
    fun `a day-flexible activity that cannot fit is offered another day`() {
        val result = scheduleOf(
            planned("work", at(7, 0), 960, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("side project", at(12, 0), 120, flexibility = Flexibility.DAY_FLEXIBLE)
        )

        val unplaced = result.unplaced.single()
        assertEquals("side project", unplaced.title)
        assertEquals(ChangeReason.DeferredToAnotherDay, unplaced.reason)
        assertEquals(Feasibility.PARTIALLY_FEASIBLE, result.feasibility)
    }

    @Test
    fun `an activity that cannot move days is kept, never deleted`() {
        val result = scheduleOf(
            planned("work", at(7, 0), 960, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("chores", at(12, 0), 120, flexibility = Flexibility.TIME_FLEXIBLE)
        )

        assertEquals(ChangeReason.NoRoomToday, result.unplaced.single().reason)
    }

    @Test
    fun `losing a critical activity makes the plan infeasible`() {
        val result = scheduleOf(
            planned("work", at(7, 0), 960, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("medication", at(12, 0), 120, priority = Priority.CRITICAL)
        )

        assertTrue(result.isUnplaced("medication"))
        assertEquals(Feasibility.INFEASIBLE, result.feasibility)
    }

    @Test
    fun `two overlapping anchors are reported instead of silently resolved`() {
        val result = scheduleOf(
            planned("school run", at(8, 0), 60, flexibility = Flexibility.FIXED),
            planned("dentist", at(8, 30), 60, flexibility = Flexibility.FIXED)
        )

        val conflict = result.conflicts.single()
        assertEquals("school run", conflict.firstTitle)
        assertEquals("dentist", conflict.secondTitle)
        assertEquals("both are still on the timeline", 2, result.placements.size)
        assertEquals(Feasibility.INFEASIBLE, result.feasibility)
    }

    @Test
    fun `a deadline pulls an activity earlier`() {
        val result = scheduleOf(
            planned(
                "submit report",
                at(16, 0),
                60,
                flexibility = Flexibility.DEADLINE_BASED,
                deadline = on(12, 0)
            )
        )

        val placement = result.placementFor("submit report")!!
        assertTrue(placement.end <= at(12, 0))
        assertEquals(ChangeReason.MovedForDeadline, placement.changeReason)
    }

    @Test
    fun `a deadline that cannot be met is flagged rather than hidden`() {
        val result = scheduleOf(
            planned("morning block", at(7, 0), 300, flexibility = Flexibility.FIXED),
            planned(
                "late report",
                at(13, 0),
                60,
                flexibility = Flexibility.DEADLINE_BASED,
                deadline = on(11, 0)
            )
        )

        val placement = result.placementFor("late report")!!
        assertEquals(ChangeReason.DeadlineAtRisk, placement.changeReason)
        assertEquals(Feasibility.PARTIALLY_FEASIBLE, result.feasibility)
    }

    @Test
    fun `a preferred start is honoured over the block's current position`() {
        val result = scheduleOf(
            planned("meditation", at(16, 0), 15, preferredStart = at(7, 30))
        )

        assertEquals(at(7, 30), result.startOf("meditation"))
    }

    @Test
    fun `a past day is reported as history and never rewritten`() {
        val result = scheduleOf(
            planned("yesterday task", at(9, 0), 60, state = ActivityState.MISSED),
            now = on(9, 0).plusDays(1)
        )

        assertEquals(at(9, 0), result.startOf("yesterday task"))
        assertTrue(result.movedPlacements.isEmpty())
    }

    @Test
    fun `scheduling is deterministic for equivalent input in any order`() {
        val a = planned("alpha", at(9, 0), 60, priority = Priority.HIGH)
        val b = planned("beta", at(9, 0), 60, priority = Priority.HIGH)
        val c = planned("gamma", at(9, 0), 60, priority = Priority.HIGH)

        val forwards = scheduleOf(a, b, c)
        val backwards = scheduleOf(c, b, a)

        assertEquals(forwards.placements, backwards.placements)
    }

    @Test
    fun `every meaningful automatic change comes with an explanation`() {
        val result = scheduleOf(
            planned("class", at(9, 0), 120, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("laundry", at(9, 30), 45)
        )

        val explanations = result.explanations()
        assertEquals(1, explanations.size)
        assertTrue(explanations.single().contains("laundry"))
        assertTrue(explanations.single().contains("class"))
    }

    @Test
    fun `applying a result moves the blocks and keeps unplaced work recoverable`() {
        val items = listOf(
            planned("lecture", at(9, 0), 120, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("gym", at(9, 30), 60)
        )
        val result = AdaptiveScheduler.schedule(
            ScheduleRequest(TestDate, items, TestWindow)
        )

        val updated = AdaptiveScheduler.applyTo(items, result)
        val gym = updated.single { it.id == "gym" }
        assertEquals(at(11, 0), gym.start)
        assertEquals(at(12, 0), gym.end)
        assertNotNull(gym.changeReason)
    }

    @Test
    fun `unplaced work is marked for later rather than dropped from storage`() {
        val items = listOf(
            planned("work", at(7, 0), 960, priority = Priority.CRITICAL, flexibility = Flexibility.FIXED),
            planned("chores", at(12, 0), 120)
        )
        val result = AdaptiveScheduler.schedule(ScheduleRequest(TestDate, items, TestWindow))

        val chores = AdaptiveScheduler.applyTo(items, result).single { it.id == "chores" }
        assertEquals(ActivityState.LATER, chores.state)
        assertEquals(ChangeReason.NoRoomToday, chores.changeReason)
    }

    @Test
    fun `once there is room again, the no-room explanation is cleared`() {
        // Yesterday's crowded day left this marked as unplaceable. Today it
        // fits, so the stale explanation must not survive and keep it hidden
        // away in the "needs a slot" group.
        val stale = planned("chores", at(14, 0), 60).let {
            it.copy(
                block = it.block.copy(
                    state = ActivityState.LATER,
                    changeReason = ChangeReason.NoRoomToday
                )
            )
        }
        val items = listOf(stale)
        val result = AdaptiveScheduler.schedule(ScheduleRequest(TestDate, items, TestWindow))

        val updated = AdaptiveScheduler.applyTo(items, result).single()
        assertEquals(at(14, 0), updated.start)
        assertNull("the activity was placed, so it no longer needs a slot", updated.changeReason)
    }

    @Test
    fun `an explanation for a real move survives until the next move`() {
        val moved = planned("gym", at(11, 0), 60).let {
            it.copy(block = it.block.copy(changeReason = ChangeReason.MovedForAnchor("lecture")))
        }
        val items = listOf(moved)
        val result = AdaptiveScheduler.schedule(ScheduleRequest(TestDate, items, TestWindow))

        val updated = AdaptiveScheduler.applyTo(items, result).single()
        assertEquals(ChangeReason.MovedForAnchor("lecture"), updated.changeReason)
    }

    @Test
    fun `a sleep target after midnight still closes the day at the date boundary`() {
        val nightOwl = TestWindow.copy(
            wakeTime = LocalTime.of(9, 0),
            sleepTarget = LocalTime.of(1, 0),
            nightStarts = LocalTime.of(23, 0)
        )

        assertTrue(nightOwl.sleepTargetIsAfterMidnight)
        assertEquals(DayWindow.END_OF_DAY_MINUTE, nightOwl.sleepMinute)

        val result = scheduleOf(planned("late reading", at(23, 30), 20), window = nightOwl)
        assertEquals(at(23, 30), result.startOf("late reading"))
    }
}
