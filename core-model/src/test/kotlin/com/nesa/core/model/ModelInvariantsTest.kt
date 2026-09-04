package com.nesa.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * The domain rejects impossible values at construction rather than letting them
 * reach the scheduler, where the symptom would be a strange plan instead of a
 * clear failure.
 */
class ModelInvariantsTest {

    @Test(expected = IllegalArgumentException::class)
    fun `an activity cannot be nameless`() {
        Activity(id = "a", title = "   ", duration = Duration.ofMinutes(30))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an activity cannot take no time`() {
        Activity(id = "a", title = "Read", duration = Duration.ZERO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a block cannot end before it starts`() {
        ScheduleBlock(
            id = "b",
            activityId = "a",
            date = LocalDate.of(2025, 3, 3),
            start = LocalTime.of(10, 0),
            end = LocalTime.of(9, 0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a snooze policy cannot be unbounded in length`() {
        SnoozePolicy(snoozeMinutes = 0)
    }

    @Test
    fun `flexibility answers the questions the scheduler asks of it`() {
        assertFalse(Flexibility.FIXED.movableWithinDay)
        assertTrue(Flexibility.TIME_FLEXIBLE.movableWithinDay)
        assertFalse(Flexibility.TIME_FLEXIBLE.movableAcrossDays)
        assertTrue(Flexibility.DAY_FLEXIBLE.movableAcrossDays)
        assertTrue(Flexibility.DEADLINE_BASED.movableAcrossDays)
        assertTrue(Flexibility.OPTIONAL.droppable)
        assertFalse(Flexibility.TIME_FLEXIBLE.droppable)
    }

    @Test
    fun `priority ranks most important first`() {
        assertEquals(
            listOf(Priority.CRITICAL, Priority.HIGH, Priority.NORMAL, Priority.LOW),
            Priority.entries.sortedBy { it.rank }
        )
    }

    @Test
    fun `only a fixed activity is an anchor`() {
        Flexibility.entries.forEach { flexibility ->
            val activity = Activity(
                id = "a",
                title = "Work",
                duration = Duration.ofMinutes(60),
                flexibility = flexibility
            )
            assertEquals(flexibility == Flexibility.FIXED, activity.isAnchor)
        }
    }

    @Test
    fun `an alarm defaults to a volume that can wake somebody`() {
        val alarm = Alarm(id = "a", time = LocalTime.of(7, 0))
        assertEquals(Alarm.DEFAULT_VOLUME_PERCENT, alarm.volumePercent)
        assertTrue(alarm.volumePercent >= Alarm.MIN_VOLUME_PERCENT)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an alarm cannot be silenced by its volume`() {
        // Zero is the value a slider can reach by accident and a user never
        // means. A silent alarm is a broken alarm, not a quiet preference.
        Alarm(id = "a", time = LocalTime.of(7, 0), volumePercent = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an alarm volume cannot exceed the scale`() {
        Alarm(id = "a", time = LocalTime.of(7, 0), volumePercent = 101)
    }

    @Test
    fun `settings work with no configuration at all`() {
        val defaults = NesaSettings.Default
        assertFalse(defaults.onboardingCompleted)
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(GuidancePersonality.BALANCED, defaults.guidance)
        assertTrue(defaults.remindersEnabled)
        // The default day has to be a coherent one, or a user who skips every
        // question would get an unusable plan.
        defaults.dayWindow.validated()
    }
}
