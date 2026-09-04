# Stages, corrected against the specification

`docs/spec/` now holds the three source documents verbatim. **They are the
source of truth.** Read `spec/build-instructions.md` §5–8 for the stage prompts
and `spec/product-specification.md` §8 and §14 for the module-to-stage table.

## The stage map, from the specification

| Stage | Name | Scope |
| --- | --- | --- |
| 1 | Core | Onboarding, timeline, activities, scheduling, smart alarm, wake challenge, notifications, settings |
| 2 | **Life** | Work/school/training schedules, recurring activities, day-of-week rules, optional prayer and meals, richer recovery, evening overflow, **night review**, missed-activity review with suggested rescheduling, daily/weekly statistics |
| 3 | Health & Focus | **Fitness** (assessment, progression, rest), **Learning**, **Focus/screen-time** |
| 4 | Intelligence | AIProvider abstraction, Gemini adapter, structured commands, granular permissions, confirmation for high-impact changes, voice |
| 5 | Personalize | Theme engine, curated themes, custom primary colour, background image, widgets, advanced personalization, accessibility and performance polish |

## A correction, recorded rather than hidden

**The fitness module was built during Stage 2. It belongs to Stage 3.** This
happened because the specification documents were not in the repository, and
`CLAUDE.md` carried only a one-word list — "fitness, focus, learning, AI, voice,
sync, widgets and the theme engine all belong to Stages 2–5" — which invited
exactly this inference. The user was asked and agreed, but the specification is
unambiguous and the user was working from the same faulty summary.

It has **not been reverted**, for reasons that are themselves in the
non-negotiables: *do not rewrite working systems unnecessarily*. The module
compiles, has twelve passing domain tests, is off by default, and is gated
behind a settings toggle so it changes nothing for a user who has not asked for
it. Deleting working, tested, inert code to satisfy a calendar would be the
worse engineering decision.

What it does mean:

- Stage 3 starts with fitness already present, and needs the parts the
  specification asks for that were not built from inference: the **assessment**
  (goals, experience, equipment, current ability, self-reported limitations),
  **beginner/intermediate/advanced pathways**, **conservative progression**, and
  **rest days**. Also: *never make medical claims* — that is a product rule, not
  a nicety.
- `AdaptiveInsights` sits on a line. Stage 2 asks for missed-activity review
  "from deterministic rules", which is exactly what it is; Stage 5 owns
  "advanced personalization". It is kept because it only ever produces a
  *warning* beside a time picker, and it is listed here so a later reader knows
  it was a judgement call rather than an oversight.

Two other things that were also inference and turned out to be **wrong**, now
corrected in this document: sync is not a Stage 5 deliverable at all (it is a
"future expansion hook", blueprint §18), and "advanced personalization" is
Stage 5, not Stage 3.

## Stage 2 — Life. The current gate.

Blueprint §17 gives the gate: *recurring schedules, work/school,
recovery/rescheduling, and daily review tested.*

### Built

- **Recurrence** — `Recurrence`, `RecurrenceMaterialiser`, day-of-week rules,
  intervals, monthly clamping. Fourteen tests.
- **Night review** — `NightReview`, deterministic suggestions from priority,
  flexibility, deadline, and tomorrow's anchors, in that order. Refuses to
  suggest rather than inventing a slot. Twelve tests.
- **Daily/weekly statistics** — `PlanStatistics`. Skips are excluded from the
  completion denominator, so deciding to clear a bad day does not count against
  the user. Nine tests.

- **Life schedules** — `LifeSchedule` and `ScheduleEntry` in the domain,
  `LifeScheduleApplier` turning one into ordinary activities carrying a weekly
  `Recurrence`, and `LifeSchedulePresets` for work, school, training, prayer and
  meals. Every schedule switches on and off independently, and presets arrive
  switched **off**. Eleven tests.
- **`feature-life`** — the module the blueprint names (§4). Schedules list,
  schedule editor, night review and statistics screens.

Stage 2 is now feature-complete against the specification. **Its gate has not
been run** — see `verification.md`.

### Judgement calls worth knowing about

- **The prayer preset is empty.** Times differ by tradition, location and
  season; a preset filled with one tradition's schedule would presume something
  this product has no business presuming. The screen invites the user to add
  their own, and prayer entries default to CRITICAL and FIXED so the rest of the
  day is arranged around them.
- **Generated activity ids are derived, not random** —
  `life:<scheduleId>:<entryId>`. That is what makes applying a schedule
  idempotent (no second "Work" every Monday) and removing one exact (an activity
  the user made by hand and called "Work" is never caught by a schedule's
  cleanup).
- **Life is not behind a master switch**, unlike fitness. Its individual
  schedules are what the user enables, and a module with nothing enabled already
  shows nothing.

## Stages 3–5

Now specified rather than inferred — see `docs/spec/`. The decisions the user has
already made:

- **AI access:** the user supplies their own key, stored in
  `EncryptedSharedPreferences`, never committed. The app must work fully without
  one. Note blueprint §13 and the Stage 4 prompt both require that a *public*
  release move to a backend proxy; a personal build may inject a local secret,
  and that boundary must be documented in the code rather than assumed.
- **Sync:** a local encrypted export/import, not cloud sync. The specification
  agrees — cloud sync is a future expansion hook, not a stage deliverable.

`NesaIntent` and its refusal types are already written (`core-scheduling`), and
they match blueprint §11 closely: a closed set of typed commands, a validator,
and the deterministic layer executing. The Gemini adapter goes behind an
`AIProvider` interface, per §11's "avoid vendor lock-in".
