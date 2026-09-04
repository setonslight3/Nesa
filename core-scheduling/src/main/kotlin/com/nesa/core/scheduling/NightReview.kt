package com.nesa.core.scheduling

import com.nesa.core.model.ActivityState
import com.nesa.core.model.DayWindow
import com.nesa.core.model.Flexibility
import com.nesa.core.model.PlannedActivity
import com.nesa.core.model.Priority
import java.time.LocalDate
import java.time.LocalTime

/**
 * Where NESA proposes an unfinished activity should go.
 *
 * A proposal, never an action. The night review shows these and the user picks;
 * nothing here moves anything by itself, which is what keeps a review from
 * feeling like the app rearranging the user's life overnight.
 */
sealed interface RescheduleSuggestion {

    /** There is still room today, before the night begins. */
    data class LaterToday(val start: LocalTime) : RescheduleSuggestion

    /** Tomorrow, in the first gap that does not disturb a fixed commitment. */
    data class Tomorrow(val date: LocalDate, val start: LocalTime) : RescheduleSuggestion

    /**
     * Let it go. Only ever offered for genuinely optional work — never for
     * something the user marked important, and never silently.
     */
    data object LetItGo : RescheduleSuggestion

    /**
     * NESA has no honest suggestion, and says so rather than inventing one.
     *
     * @param reason plain language, shown to the user. "Tomorrow is full before
     *   your deadline" is useful; a blank space is not.
     */
    data class NoRoom(val reason: String) : RescheduleSuggestion
}

/** One unfinished activity and what NESA proposes to do about it. */
data class ReviewItem(
    val item: PlannedActivity,
    val suggestion: RescheduleSuggestion
)

/**
 * The night review: what happened today, and what to do about what did not.
 *
 * @param moved activities NESA itself relocated during the day, with the reason
 *   still attached. Shown because "explain meaningful automatic changes" is a
 *   product rule, and a plan that quietly rearranged itself is one the user
 *   stops trusting.
 */
data class NightReviewResult(
    val date: LocalDate,
    val completed: List<PlannedActivity>,
    val skipped: List<PlannedActivity>,
    val missed: List<ReviewItem>,
    val deferred: List<ReviewItem>,
    val moved: List<PlannedActivity>,
    val tomorrowAnchors: List<PlannedActivity>
) {
    val plannedCount: Int
        get() = completed.size + skipped.size + missed.size + deferred.size

    /** Nothing was left hanging. The review can congratulate and stop. */
    val isSettled: Boolean get() = missed.isEmpty() && deferred.isEmpty()
}

/**
 * Closes today and prepares tomorrow.
 *
 * The product's own success criterion is that "a missed activity can be
 * recovered without rebuilding the day manually", and this is the piece that
 * has to be true for that to hold.
 *
 * Deterministic by requirement, not by preference: the Stage 2 specification is
 * explicit that suggestions come from rules, not from a model. Everything here
 * is a pure function of the two days it is given, so the suggestion a user sees
 * is one a test can pin exactly.
 *
 * The ordering of the rules matters and is the interesting part. It reads
 * priority first, then flexibility, then the deadline, then tomorrow's anchors
 * — which is the order a person would reason in, and the order the
 * specification lists.
 */
object NightReview {

    /**
     * @param today the day being closed, in whatever state it ended.
     * @param tomorrow tomorrow's plan as it currently stands, used to find room
     *   without disturbing anything already fixed there.
     */
    fun of(
        date: LocalDate,
        today: List<PlannedActivity>,
        tomorrow: List<PlannedActivity>,
        window: DayWindow,
        now: LocalTime
    ): NightReviewResult {
        val unfinished = today.filter { it.state == ActivityState.MISSED || it.state == ActivityState.LATER }

        return NightReviewResult(
            date = date,
            completed = today.filter { it.state == ActivityState.COMPLETED },
            // Skipped is listed apart from missed, everywhere, always. A skip is
            // a decision the user made and nothing needs recovering.
            skipped = today.filter { it.state == ActivityState.SKIPPED },
            missed = unfinished
                .filter { it.state == ActivityState.MISSED }
                .map { ReviewItem(it, suggestFor(it, date, today, tomorrow, window, now)) },
            deferred = unfinished
                .filter { it.state == ActivityState.LATER }
                .map { ReviewItem(it, suggestFor(it, date, today, tomorrow, window, now)) },
            moved = today.filter { it.block.changeReason != null },
            tomorrowAnchors = tomorrow.filter { it.activity.isAnchor }.sortedBy { it.block.startMinuteOfDay }
        )
    }

    /**
     * Where one unfinished activity should go.
     *
     * Exposed so the missed-activity review can ask about a single item without
     * rebuilding a whole night review, and so each rule can be tested alone.
     */
    fun suggestFor(
        item: PlannedActivity,
        date: LocalDate,
        today: List<PlannedActivity>,
        tomorrow: List<PlannedActivity>,
        window: DayWindow,
        now: LocalTime
    ): RescheduleSuggestion {
        // A fixed commitment that did not happen is not NESA's to move. Saying
        // so is more useful than proposing a time the user never agreed to.
        if (item.activity.flexibility == Flexibility.FIXED) {
            return RescheduleSuggestion.NoRoom(FIXED_REASON)
        }

        // Optional *and* unimportant is the one case where dropping it is the
        // honest answer. Never offered for anything the user marked as
        // mattering, whatever its flexibility.
        //
        // Equality, not `<= Priority.LOW`. Priority is declared CRITICAL, HIGH,
        // NORMAL, LOW, so enum ordering puts LOW last and `<= LOW` is true for
        // every priority there is — which would have offered to throw away a
        // critical activity because it happened to be marked optional.
        if (item.activity.flexibility == Flexibility.OPTIONAL && item.priority == Priority.LOW) {
            return RescheduleSuggestion.LetItGo
        }

        val duration = item.activity.durationMinutes
        val deadlineMinute = deadlineMinuteFor(item, date)

        // Still today, if the day genuinely has room left before the night. A
        // recovery the user can act on tonight beats one that waits for
        // tomorrow — except for day-flexible work, which is by definition
        // happier on another day than crammed into this one.
        if (item.activity.flexibility != Flexibility.DAY_FLEXIBLE) {
            val fromNow = DayWindow.minuteOf(now)
            val nightStarts = DayWindow.minuteOf(window.nightStarts)
            val untilToday = minOf(nightStarts, deadlineMinute ?: nightStarts)
            firstGap(today, fromNow, untilToday, duration)?.let {
                return RescheduleSuggestion.LaterToday(DayWindow.timeOf(it))
            }
        }

        // A deadline that falls today and could not be met is a real refusal.
        // Proposing tomorrow for work that was due today would be a lie the
        // user only discovers when it is too late.
        if (deadlineMinute != null && item.activity.deadline?.toLocalDate() == date) {
            return RescheduleSuggestion.NoRoom(DEADLINE_TODAY_REASON)
        }

        val nextDay = date.plusDays(1)
        val dayStart = DayWindow.minuteOf(window.wakeTime)
        val dayEnd = DayWindow.minuteOf(window.nightStarts)
        val tomorrowLimit = if (item.activity.deadline?.toLocalDate() == nextDay) {
            minOf(dayEnd, deadlineMinuteFor(item, nextDay) ?: dayEnd)
        } else {
            dayEnd
        }

        firstGap(tomorrow, dayStart, tomorrowLimit, duration)?.let {
            return RescheduleSuggestion.Tomorrow(nextDay, DayWindow.timeOf(it))
        }

        return RescheduleSuggestion.NoRoom(NO_GAP_REASON)
    }

    /**
     * The earliest free minute in [between, until) that fits [duration].
     *
     * Only immovable things count as occupied: a fixed anchor, a locked block,
     * or something already under way. Flexible work on the day is left out
     * deliberately — the scheduler will rearrange it anyway when the activity
     * actually lands, and treating it as a wall here would make NESA claim a day
     * was full when it was merely busy.
     */
    private fun firstGap(
        day: List<PlannedActivity>,
        between: Int,
        until: Int,
        duration: Int
    ): Int? {
        if (duration <= 0 || between + duration > until) return null

        val occupied = day
            .filter { it.isImmovable }
            .map { it.block.startMinuteOfDay to it.block.endMinuteOfDay }
            .sortedBy { it.first }

        var candidate = between
        for ((start, end) in occupied) {
            if (candidate + duration <= start) return candidate
            if (end > candidate) candidate = end
        }
        return candidate.takeIf { it + duration <= until }
    }

    /** The activity's deadline as a minute of [onDate], or null if it has none. */
    private fun deadlineMinuteFor(item: PlannedActivity, onDate: LocalDate): Int? {
        val deadline = item.activity.deadline ?: return null
        return when {
            deadline.toLocalDate() == onDate -> DayWindow.minuteOf(deadline.toLocalTime())
            // A deadline already past constrains everything to nothing.
            deadline.toLocalDate().isBefore(onDate) -> 0
            else -> null
        }
    }

    private const val FIXED_REASON =
        "This was a fixed commitment, so NESA will not move it for you."
    private const val DEADLINE_TODAY_REASON =
        "Its deadline was today, and there is no time left before it."
    private const val NO_GAP_REASON =
        "Neither the rest of today nor tomorrow has a gap long enough."
}
