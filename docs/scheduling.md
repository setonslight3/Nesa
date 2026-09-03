# The scheduling engine

`AdaptiveScheduler` is a pure function. The same request always produces the same
plan: no clock is read inside it, no database is touched, and nothing about
Android reaches it. When Stage 4 adds AI, the model will propose intents — this
class will still be the only thing that decides where an activity actually goes.

## What it takes and what it returns

```
ScheduleRequest(date, items, dayWindow, now)  ──►  ScheduleResult(
                                                     placements,   where things go, and why
                                                     unplaced,     what did not fit
                                                     conflicts,    anchors that overlap
                                                     feasibility)  whether the plan works
```

Every placement carries a `ChangeReason`, which is how NESA explains itself. The
timeline shows that sentence on the card that moved.

## The rules, in the order they win

1. **Fixed anchors never move.** An activity marked `FIXED`, a block the user
   pinned, and anything already running or finished own their time. The scheduler
   plans around them.
2. **Nothing is planned into the past.** When `now` falls on the day being
   planned, the earliest usable minute is now.
3. **Nothing crosses the sleep target.** If the work does not fit before it, the
   work gives way — not the sleep.
4. **Deadlines are preserved where possible, and flagged where not.** A
   deadline-based activity is pulled earlier to meet its deadline. If no
   arrangement meets it, it is still placed, marked `DeadlineAtRisk`, and the day
   is reported as only partially feasible.
5. **Higher priority is placed first.** Ordering is priority, then the tightest
   deadline, then the user's preferred time, then the current placement, then id
   — the last purely so the same input never yields two different plans.
6. **The least disruptive plan wins.** An activity that still works exactly where
   it is stays exactly where it is, even late in the evening. Only work that must
   move gets an opinion imposed on it.
7. **The evening absorbs overflow; the night is left alone.** The search widens
   in steps — the wanted time before the night window, anywhere before the night
   window, then the evening and night — giving up one preference at a time.
8. **Nothing important is deleted.** Work that cannot be placed comes back as an
   `UnplacedItem` and is marked `LATER`, never removed.

## Two overlapping anchors

NESA reports the conflict and keeps both. It does not pick a winner, because both
are commitments the user called immovable, and quietly dropping one would be the
worst possible answer. The day is reported `INFEASIBLE`, which is the signal for
the user to intervene.

## Missed is not skipped

This distinction runs through the whole product.

| | Skipped | Missed |
| --- | --- | --- |
| Caused by | A user decision | The absence of one |
| Raised by | A tap, with an optional reason | `MissedActivityDetector`, after a grace period |
| Recorded as | `CompletionResult.SKIPPED` | `CompletionResult.MISSED` |
| Afterwards | Resolved for today | Still in play, and replanned |

`ActivityEvent.MISS` is never offered by any screen — `availableEvents()`
excludes it by construction, and there is a test that asserts so for every state.
Silence is never read as agreement.

The grace period comes from the user's guidance personality: gentle waits an
hour, strict waits ten minutes. Persistence means a bounded number of meaningful
reminders — two on the default setting — and then nothing more.

## Recovery

`DayPlanner.refresh(date)` is the loop that keeps a day honest, and it runs from
the timeline, after every user decision, and from a background worker every half
hour:

1. Detect activities whose window elapsed unanswered; mark them `MISSED` and
   record them.
2. Re-run the scheduler against that reality.
3. Write back only the blocks that actually changed.

A missed activity is therefore given a new slot in the remaining day, with
`RecoveredFromMissed` as its explanation — recovery without rebuilding the day by
hand, which is one of the product's stated success criteria.

## Testing

The engine and its use cases carry 92 unit tests, all runnable on a JDK with no
emulator. They cover anchor protection, priority ordering, past-time handling,
sleep-target protection, evening overflow, night protection, deadlines met and
missed, anchor conflicts, determinism under input reordering, the full state
transition table, missed-versus-skipped, reminder bounds, DST transitions in both
directions, challenge generation and difficulty adaptation, and the recovery loop
end to end over in-memory repositories.
