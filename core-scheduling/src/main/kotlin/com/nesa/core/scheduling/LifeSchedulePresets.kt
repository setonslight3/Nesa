package com.nesa.core.scheduling

import com.nesa.core.model.LifeSchedule
import com.nesa.core.model.LifeScheduleKind
import com.nesa.core.model.ScheduleEntry
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Starting points for the schedules the specification names.
 *
 * A preset is a *draft*, not a template the user is stuck with: every field is
 * editable afterwards and nothing here is applied until the user says so. The
 * point is that setting up a work week should take one tap and one correction,
 * not eleven fields.
 *
 * Two rules held throughout:
 *
 * - **Nothing here is culturally assumed.** Prayer times are left blank rather
 *   than pre-filled with one tradition's schedule, and meals are named neutrally.
 *   A preset that guessed wrong would be worse than an empty one.
 * - **Nothing is enabled by default.** The user chooses which of these exist at
 *   all, which is the "never forced to configure a module they do not use" rule
 *   applied to the Life module.
 */
object LifeSchedulePresets {

    private val WEEKDAYS = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    /**
     * @param idFactory supplied rather than called directly, so a preset is
     *   reproducible in a test the same way the scheduler's use cases are.
     */
    fun draft(kind: LifeScheduleKind, idFactory: () -> String): LifeSchedule {
        val scheduleId = idFactory()
        return LifeSchedule(
            id = scheduleId,
            name = defaultName(kind),
            kind = kind,
            // Off until the user has looked at it. Applying a guessed work week
            // to somebody's calendar unasked would be exactly the coercion this
            // product is meant not to be.
            enabled = false,
            entries = defaultEntries(kind, idFactory)
        )
    }

    fun defaultName(kind: LifeScheduleKind): String = when (kind) {
        LifeScheduleKind.WORK -> "Work"
        LifeScheduleKind.SCHOOL -> "School"
        LifeScheduleKind.TRAINING -> "Training"
        LifeScheduleKind.PRAYER -> "Prayer"
        LifeScheduleKind.MEAL -> "Meals"
        LifeScheduleKind.CUSTOM -> "Routine"
    }

    private fun defaultEntries(
        kind: LifeScheduleKind,
        idFactory: () -> String
    ): List<ScheduleEntry> = when (kind) {
        LifeScheduleKind.WORK -> listOf(
            entry(idFactory, "Work", WEEKDAYS, LocalTime.of(9, 0), kind)
        )

        LifeScheduleKind.SCHOOL -> listOf(
            entry(idFactory, "School", WEEKDAYS, LocalTime.of(8, 0), kind)
        )

        LifeScheduleKind.TRAINING -> listOf(
            entry(
                idFactory,
                "Training",
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                LocalTime.of(18, 0),
                kind
            )
        )

        LifeScheduleKind.MEAL -> listOf(
            entry(idFactory, "Breakfast", DayOfWeek.entries.toSet(), LocalTime.of(8, 0), kind),
            entry(idFactory, "Lunch", DayOfWeek.entries.toSet(), LocalTime.of(13, 0), kind),
            entry(idFactory, "Dinner", DayOfWeek.entries.toSet(), LocalTime.of(19, 0), kind)
        )

        // Deliberately empty. Prayer times differ by tradition, by location and
        // by season, and a preset that filled in one tradition's schedule would
        // be presuming something about the user this product has no business
        // presuming. The screen invites them to add their own.
        LifeScheduleKind.PRAYER -> emptyList()

        LifeScheduleKind.CUSTOM -> emptyList()
    }

    private fun entry(
        idFactory: () -> String,
        title: String,
        days: Set<DayOfWeek>,
        start: LocalTime,
        kind: LifeScheduleKind
    ) = ScheduleEntry(
        id = idFactory(),
        title = title,
        days = days,
        start = start,
        duration = kind.defaultDuration,
        priority = kind.defaultPriority,
        flexibility = kind.defaultFlexibility
    )
}
