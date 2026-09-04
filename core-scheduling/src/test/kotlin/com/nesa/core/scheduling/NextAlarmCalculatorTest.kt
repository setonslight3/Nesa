package com.nesa.core.scheduling

import com.nesa.core.model.Alarm
import com.nesa.core.model.SnoozePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextAlarmCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")

    private fun alarm(
        time: LocalTime = LocalTime.of(6, 30),
        days: Set<DayOfWeek> = emptySet(),
        enabled: Boolean = true,
        snooze: SnoozePolicy = SnoozePolicy.Default
    ) = Alarm(id = "alarm", time = time, days = days, enabled = enabled, snooze = snooze)

    @Test
    fun `a one-shot alarm fires today when its time has not passed`() {
        val now = ZonedDateTime.of(TestDate, LocalTime.of(5, 0), zone)
        val next = NextAlarmCalculator.next(alarm(), now)!!

        assertEquals(TestDate, next.toLocalDate())
        assertEquals(LocalTime.of(6, 30), next.toLocalTime())
    }

    @Test
    fun `a one-shot alarm rolls to tomorrow once its time has passed`() {
        val now = ZonedDateTime.of(TestDate, LocalTime.of(7, 0), zone)
        val next = NextAlarmCalculator.next(alarm(), now)!!

        assertEquals(TestDate.plusDays(1), next.toLocalDate())
    }

    @Test
    fun `a repeating alarm only fires on its chosen days`() {
        // TestDate is a Monday.
        val weekend = alarm(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val now = ZonedDateTime.of(TestDate, LocalTime.of(7, 0), zone)

        val next = NextAlarmCalculator.next(weekend, now)!!
        assertEquals(DayOfWeek.SATURDAY, next.dayOfWeek)
        assertEquals(TestDate.plusDays(5), next.toLocalDate())
    }

    @Test
    fun `a disabled alarm never fires`() {
        val now = ZonedDateTime.of(TestDate, LocalTime.of(5, 0), zone)
        assertNull(NextAlarmCalculator.next(alarm(enabled = false), now))
    }

    @Test
    fun `the daylight saving spring forward still produces a valid firing`() {
        // On 2025-03-30 the UK clocks jump from 01:00 to 02:00; 01:30 does not
        // exist. The alarm must still resolve to a real instant that morning.
        val springForward = java.time.LocalDate.of(2025, 3, 30)
        val now = ZonedDateTime.of(springForward, LocalTime.of(0, 30), zone)
        val next = NextAlarmCalculator.next(alarm(time = LocalTime.of(1, 30)), now)!!

        assertEquals(springForward, next.toLocalDate())
        assertTrue("the resolved time must be a real instant", next.isAfter(now))
        assertEquals(LocalTime.of(2, 30), next.toLocalTime())
    }

    @Test
    fun `the daylight saving autumn back does not skip the alarm`() {
        val fallBack = java.time.LocalDate.of(2025, 10, 26)
        val now = ZonedDateTime.of(fallBack, LocalTime.of(0, 15), zone)
        val next = NextAlarmCalculator.next(alarm(time = LocalTime.of(6, 30)), now)!!

        assertEquals(fallBack, next.toLocalDate())
        assertEquals(LocalTime.of(6, 30), next.toLocalTime())
    }

    @Test
    fun `snoozing pushes the alarm out by the configured amount`() {
        val now = ZonedDateTime.of(TestDate, LocalTime.of(6, 30), zone)
        val snoozed = NextAlarmCalculator.afterSnooze(alarm(), now)

        assertEquals(LocalTime.of(6, 39), snoozed.toLocalTime())
    }

    @Test
    fun `silence is retried a bounded number of times`() {
        val policy = SnoozePolicy(autoRetryMinutes = 5, maxAutoRetries = 2)
        val a = alarm(snooze = policy)
        val now = ZonedDateTime.of(TestDate, LocalTime.of(6, 30), zone)

        assertEquals(LocalTime.of(6, 35), NextAlarmCalculator.afterSilence(a, now, 0)!!.toLocalTime())
        assertEquals(LocalTime.of(6, 35), NextAlarmCalculator.afterSilence(a, now, 1)!!.toLocalTime())
        assertNull("retries are bounded", NextAlarmCalculator.afterSilence(a, now, 2))
    }

    @Test
    fun `an edited alarm resolves to one occurrence, never two`() {
        // The scheduler cancels before it arms and reuses one request code per
        // alarm id, so an edit replaces the schedule. The calculator's job is to
        // give exactly one answer for the edited alarm, which is what makes that
        // replacement unambiguous.
        val now = ZonedDateTime.of(TestDate, LocalTime.of(5, 0), zone)
        val original = alarm(time = LocalTime.of(6, 30))
        val edited = original.copy(time = LocalTime.of(8, 0))

        assertEquals(LocalTime.of(6, 30), NextAlarmCalculator.next(original, now)!!.toLocalTime())
        assertEquals(LocalTime.of(8, 0), NextAlarmCalculator.next(edited, now)!!.toLocalTime())
        assertEquals(
            "the same alarm always resolves to the same instant",
            NextAlarmCalculator.next(edited, now),
            NextAlarmCalculator.next(edited, now)
        )
    }

    @Test
    fun `a disabled alarm resolves to nothing, so cancelling is unambiguous`() {
        val now = ZonedDateTime.of(TestDate, LocalTime.of(5, 0), zone)
        val repeating = alarm(days = setOf(DayOfWeek.MONDAY), enabled = false)

        assertNull(NextAlarmCalculator.next(repeating, now))
    }

    @Test
    fun `state is rebuilt from the alarm alone, with no memory of past firings`() {
        // The receiver reconstructs everything from stored configuration, so the
        // calculator must depend on nothing but the alarm and the moment asked.
        val now = ZonedDateTime.of(TestDate, LocalTime.of(9, 0), zone)
        val repeating = alarm(
            time = LocalTime.of(6, 30),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )

        val first = NextAlarmCalculator.next(repeating, now)
        val second = NextAlarmCalculator.next(repeating.copy(), now)
        assertEquals(first, second)
        assertEquals(DayOfWeek.WEDNESDAY, first!!.dayOfWeek)
    }

    @Test
    fun `a repeating alarm rolls to the next matching day once today has passed`() {
        // TestDate is a Monday; 09:00 is past a 06:30 alarm.
        val now = ZonedDateTime.of(TestDate, LocalTime.of(9, 0), zone)
        val everyDay = alarm(days = DayOfWeek.entries.toSet())

        val next = NextAlarmCalculator.next(everyDay, now)!!
        assertEquals(TestDate.plusDays(1), next.toLocalDate())
        assertEquals(LocalTime.of(6, 30), next.toLocalTime())
    }

    @Test
    fun `snoozes are bounded too`() {
        val a = alarm(snooze = SnoozePolicy(maxSnoozes = 2))
        assertFalse(NextAlarmCalculator.snoozeExhausted(a, 1))
        assertTrue(NextAlarmCalculator.snoozeExhausted(a, 2))
    }
}
