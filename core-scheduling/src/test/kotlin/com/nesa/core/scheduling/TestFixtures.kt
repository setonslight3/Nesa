package com.nesa.core.scheduling

import com.nesa.core.model.Activity
import com.nesa.core.model.ActivityState
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Flexibility
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.Priority
import com.nesa.core.model.ScheduleBlock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A Monday, so weekday-sensitive assertions read clearly. */
val TestDate: LocalDate = LocalDate.of(2025, 3, 3)

val TestWindow: DayWindow = DayWindow(
    wakeTime = LocalTime.of(7, 0),
    sleepTarget = LocalTime.of(23, 0),
    morningEnds = LocalTime.of(12, 0),
    eveningStarts = LocalTime.of(18, 0),
    nightStarts = LocalTime.of(21, 0)
)

fun at(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

fun on(hour: Int, minute: Int = 0): LocalDateTime = LocalDateTime.of(TestDate, at(hour, minute))

/**
 * Builds a planned activity whose block sits at [start] for [minutes], which is
 * how nearly every scheduling scenario is expressed.
 */
fun planned(
    id: String,
    start: LocalTime,
    minutes: Long,
    priority: Priority = Priority.NORMAL,
    flexibility: Flexibility = Flexibility.TIME_FLEXIBLE,
    state: ActivityState = ActivityState.UPCOMING,
    preferredStart: LocalTime? = null,
    deadline: LocalDateTime? = null,
    locked: Boolean = false,
    date: LocalDate = TestDate
): PlannedActivity = PlannedActivity(
    activity = Activity(
        id = "activity-$id",
        title = id,
        duration = Duration.ofMinutes(minutes),
        priority = priority,
        flexibility = flexibility,
        preferredStart = preferredStart,
        deadline = deadline
    ),
    block = ScheduleBlock(
        id = id,
        activityId = "activity-$id",
        date = date,
        start = start,
        end = start.plusMinutes(minutes),
        state = state,
        locked = locked
    )
)

fun scheduleOf(
    vararg items: PlannedActivity,
    now: LocalDateTime? = null,
    window: DayWindow = TestWindow
): ScheduleResult = AdaptiveScheduler.schedule(
    ScheduleRequest(date = TestDate, items = items.toList(), dayWindow = window, now = now)
)

fun ScheduleResult.startOf(blockId: String): LocalTime? = placementFor(blockId)?.start

fun ScheduleResult.isUnplaced(blockId: String): Boolean = unplaced.any { it.blockId == blockId }
