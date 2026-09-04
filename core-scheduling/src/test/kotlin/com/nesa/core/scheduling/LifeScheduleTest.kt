package com.nesa.core.scheduling

import com.nesa.core.model.Flexibility
import com.nesa.core.model.LifeSchedule
import com.nesa.core.model.LifeScheduleKind
import com.nesa.core.model.NesaModule
import com.nesa.core.model.Priority
import com.nesa.core.model.RecurrenceFrequency
import com.nesa.core.model.ScheduleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * The Life module: work, school, training, prayer and meals as recurring
 * commitments the user can switch on independently.
 *
 * The properties worth guarding are idempotence and exact removal. A schedule
 * is re-applied every time it is edited, and a duplicate "Work" on every Monday
 * — or a delete that took an unrelated activity with it — is the kind of bug a
 * user only finds a week later.
 */
class LifeScheduleTest {

    private val now: Instant = Instant.EPOCH

    private fun entry(
        id: String,
        title: String,
        start: LocalTime,
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        priority: Priority = Priority.CRITICAL,
        flexibility: Flexibility = Flexibility.FIXED
    ) = ScheduleEntry(
        id = id,
        title = title,
        days = days,
        start = start,
        duration = Duration.ofHours(1),
        priority = priority,
        flexibility = flexibility
    )

    private fun schedule(
        enabled: Boolean = true,
        entries: List<ScheduleEntry> = listOf(entry("e1", "Work", at(9, 0)))
    ) = LifeSchedule(
        id = "s1",
        name = "Work",
        kind = LifeScheduleKind.WORK,
        enabled = enabled,
        entries = entries
    )

    @Test
    fun `an entry becomes a weekly activity on the days it names`() {
        val activity = LifeScheduleApplier
            .activitiesFor(schedule(), now, TestDate)
            .single()

        assertEquals("Work", activity.title)
        assertEquals(NesaModule.LIFE, activity.module)
        assertEquals(RecurrenceFrequency.WEEKLY, activity.recurrence.frequency)
        assertEquals(setOf(DayOfWeek.MONDAY), activity.recurrence.daysOfWeek)
        assertEquals(at(9, 0), activity.preferredStart)
    }

    @Test
    fun `applying the same schedule twice produces the same rows, not a duplicate set`() {
        // The bug this guards: a user edits their work hours and ends up with
        // two Works on every Monday.
        val first = LifeScheduleApplier.activitiesFor(schedule(), now, TestDate)
        val second = LifeScheduleApplier.activitiesFor(schedule(), now, TestDate)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `ids are derived from the schedule and entry, never random`() {
        val id = LifeScheduleApplier.activityIdFor("s1", "e1")
        assertEquals(id, LifeScheduleApplier.activityIdFor("s1", "e1"))
        assertNotEquals(id, LifeScheduleApplier.activityIdFor("s1", "e2"))
        assertNotEquals(id, LifeScheduleApplier.activityIdFor("s2", "e1"))
    }

    @Test
    fun `removal targets exactly what the schedule owns`() {
        val owned = LifeScheduleApplier.activityIdsFor(
            schedule(entries = listOf(entry("e1", "Work", at(9, 0)), entry("e2", "Standup", at(8, 0))))
        )
        assertEquals(2, owned.size)
        assertTrue(owned.all(LifeScheduleApplier::isScheduleOwned))
        // An activity the user created by hand and happened to call "Work" must
        // never be caught by a schedule's cleanup.
        assertFalse(LifeScheduleApplier.isScheduleOwned("some-uuid-the-user-made"))
    }

    @Test
    fun `a disabled schedule produces nothing but still knows what it owns`() {
        val off = schedule(enabled = false)
        assertTrue(LifeScheduleApplier.activitiesFor(off, now, TestDate).isEmpty())
        // Still one id, so turning it off can clean up after itself.
        assertEquals(1, LifeScheduleApplier.activityIdsFor(off).size)
    }

    @Test
    fun `work and school and prayer are anchors, training and meals are not`() {
        assertEquals(Flexibility.FIXED, LifeScheduleKind.WORK.defaultFlexibility)
        assertEquals(Flexibility.FIXED, LifeScheduleKind.SCHOOL.defaultFlexibility)
        assertEquals(Flexibility.FIXED, LifeScheduleKind.PRAYER.defaultFlexibility)
        assertEquals(Priority.CRITICAL, LifeScheduleKind.PRAYER.defaultPriority)

        assertEquals(Flexibility.TIME_FLEXIBLE, LifeScheduleKind.TRAINING.defaultFlexibility)
        assertEquals(Flexibility.TIME_FLEXIBLE, LifeScheduleKind.MEAL.defaultFlexibility)
    }

    @Test
    fun `a scheduled anchor still protects flexible work through the ordinary scheduler`() {
        // The Life module adds no scheduling rules of its own. This is the proof:
        // an activity generated from a work schedule behaves like any other
        // anchor once it reaches AdaptiveScheduler.
        val work = planned("work", at(9, 0), 4 * 60, flexibility = Flexibility.FIXED)
        val reading = planned("reading", at(10, 0), 60, preferredStart = at(10, 0))

        val result = scheduleOf(work, reading)

        // The claim is "the Life module adds no scheduling rules of its own",
        // not "reading lands at exactly 13:00". Asserting a specific minute here
        // would couple this test to AdaptiveScheduler's internals, which have
        // their own tests — and it would then fail for reasons that had nothing
        // to do with life schedules.
        assertEquals(at(9, 0), result.startOf("work"))

        val readingStart = requireNotNull(result.startOf("reading"))
        assertFalse(
            "a flexible activity must not be left overlapping a fixed anchor",
            readingStart < at(13, 0) && readingStart.plusMinutes(60) > at(9, 0)
        )
    }

    @Test
    fun `presets arrive switched off`() {
        // Applying a guessed work week to somebody's calendar unasked would be
        // exactly the coercion this product is meant not to be.
        var counter = 0
        LifeScheduleKind.entries.forEach { kind ->
            val draft = LifeSchedulePresets.draft(kind) { "id-${counter++}" }
            assertFalse("${kind.name} should start disabled", draft.enabled)
            assertTrue(draft.name.isNotBlank())
        }
    }

    @Test
    fun `the prayer preset assumes nothing`() {
        // Times differ by tradition, location and season. An empty schedule the
        // user fills in is honest; a pre-filled one presumes.
        var counter = 0
        val prayer = LifeSchedulePresets.draft(LifeScheduleKind.PRAYER) { "id-${counter++}" }
        assertTrue(prayer.entries.isEmpty())

        val meals = LifeSchedulePresets.draft(LifeScheduleKind.MEAL) { "id-${counter++}" }
        assertEquals(3, meals.entries.size)
    }

    @Test
    fun `entries are ordered by time so a schedule reads down the day`() {
        val ordered = schedule(
            entries = listOf(
                entry("e2", "Dinner", at(19, 0)),
                entry("e1", "Breakfast", at(8, 0))
            )
        ).ordered
        assertEquals(listOf("Breakfast", "Dinner"), ordered.map { it.title })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an entry that happens on no day is rejected`() {
        entry("e1", "Nowhere", at(9, 0), days = emptySet())
    }
}
