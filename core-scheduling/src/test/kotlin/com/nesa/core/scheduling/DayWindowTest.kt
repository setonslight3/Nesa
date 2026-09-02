package com.nesa.core.scheduling

import com.nesa.core.model.DayCycle
import com.nesa.core.model.DayWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DayWindowTest {

    @Test
    fun `the four cycles cover the whole day in order`() {
        val window = TestWindow
        assertEquals(DayCycle.NIGHT, window.cycleAt(LocalTime.of(3, 0)))
        assertEquals(DayCycle.MORNING, window.cycleAt(LocalTime.of(7, 0)))
        assertEquals(DayCycle.MORNING, window.cycleAt(LocalTime.of(11, 59)))
        assertEquals(DayCycle.DAY, window.cycleAt(LocalTime.of(12, 0)))
        assertEquals(DayCycle.DAY, window.cycleAt(LocalTime.of(17, 59)))
        assertEquals(DayCycle.EVENING, window.cycleAt(LocalTime.of(18, 0)))
        assertEquals(DayCycle.EVENING, window.cycleAt(LocalTime.of(20, 59)))
        assertEquals(DayCycle.NIGHT, window.cycleAt(LocalTime.of(21, 0)))
    }

    @Test
    fun `the evening is the recovery window`() {
        assertEquals(18 * 60 until 21 * 60, TestWindow.recoveryWindow)
    }

    @Test
    fun `a normal sleep target closes the day at that time`() {
        assertFalse(TestWindow.sleepTargetIsAfterMidnight)
        assertEquals(23 * 60, TestWindow.sleepMinute)
        assertEquals(16 * 60, TestWindow.plannableMinutes)
    }

    @Test
    fun `a sleep target after midnight truncates the plan at the date boundary`() {
        val window = TestWindow.copy(sleepTarget = LocalTime.of(0, 30))
        assertTrue(window.sleepTargetIsAfterMidnight)
        assertEquals(DayWindow.END_OF_DAY_MINUTE, window.sleepMinute)
    }

    @Test
    fun `minute conversion round trips`() {
        listOf(LocalTime.of(0, 0), LocalTime.of(7, 45), LocalTime.of(23, 59)).forEach {
            assertEquals(it, DayWindow.timeOf(DayWindow.minuteOf(it)))
        }
    }

    @Test
    fun `an incoherent window is rejected rather than silently accepted`() {
        val broken = TestWindow.copy(morningEnds = LocalTime.of(6, 0))
        try {
            broken.validated()
            throw AssertionError("expected validation to fail")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Morning"))
        }
    }
}
