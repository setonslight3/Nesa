# Architecture

NESA is built so that the parts that must be reliable do not depend on the parts
that might fail. The scheduler and the alarm work offline, with no network and
no AI, and every later stage is an extension around that core rather than a
change to it.

## Layers

```
                    ┌───────────────────────────────────┐
                    │              :app                 │   composition root
                    │  Hilt wiring · navigation · shell │
                    └───────────────────────────────────┘
                                    │
        ┌───────────────┬───────────┴───────┬────────────────┐
        ▼               ▼                   ▼                ▼
 feature-onboarding  feature-timeline  feature-alarm  feature-settings
        └───────────────┴───────────┬───────┴────────────────┘
                                    ▼
                                 core-ui                      design system
                                    │
        ┌───────────────┬───────────┴───────┬────────────────┐
        ▼               ▼                   ▼                ▼
   core-storage    core-settings    core-notifications   core-alarm      platform
     (Room)         (DataStore)      (channels)        (AlarmManager)
        └───────────────┴───────────┬───────┴────────────────┘
                                    ▼
                            core-scheduling                   use cases
                                    │
                                    ▼
                               core-model                     domain
```

**Dependencies point inward.** `core-model` and `core-scheduling` are plain
Kotlin/JVM libraries: no Android, no Room, no Compose. They can be compiled and
tested with a JDK alone, which is why the rules that matter most are also the
rules that are easiest to test.

## The rule that holds it together

**Repository contracts live in the domain, implementations live outside it.**

`core-model/repository/Repositories.kt` declares `ActivityRepository`,
`AlarmRepository`, `GoalRepository`, `HistoryRepository` and
`SettingsRepository` as interfaces over `Flow`. `core-storage` implements four
of them with Room; `core-settings` implements the fifth with DataStore.

The consequence is that **no feature module depends on `core-storage`**. Check
the build files: `feature-timeline` depends on `core-model` and
`core-scheduling`, and nothing else from the data layer. Swapping Room for
something else, or adding cloud sync in a later stage, does not touch a single
screen.

## Where each responsibility lives

| Module | Owns | Never does |
| --- | --- | --- |
| `core-model` | Types, invariants, repository contracts | Know about Android, Room or Compose |
| `core-scheduling` | The scheduler, the state machine, the use cases | Touch a database or a platform API directly |
| `core-storage` | Room entities, DAOs, mapping to domain types | Leak an entity above itself |
| `core-settings` | Scalar preferences on DataStore | Store relational data |
| `core-notifications` | Channels, reminders, notification actions | Decide what a decision means |
| `core-alarm` | AlarmManager, the ringer, boot recovery, WorkManager | Know which screen it is opening |
| `core-ui` | Theme, components, formatting | Contain business logic |
| `feature-*` | Screens and their view models | Reach past the domain interfaces |
| `:app` | Wiring, navigation, ports | Contain a feature |

## Decisions worth knowing about

**Activity and ScheduleBlock are separate.** An `Activity` is *what* the user
wants to do; a `ScheduleBlock` is *when* it is planned. Stage 1 has exactly one
block per activity, so the split costs a little indirection today — and means
Stage 2's recurrence can produce many blocks from one activity without a schema
migration or a change to the scheduler.

**Room entities store primitives only.** No type converters, no enums, no
`java.time` in the schema. One mapper file translates in both directions, and
unknown enum values fall back to a safe default rather than crashing a launch on
a database written by a newer build.

**The domain use cases are plain classes.** `DayPlanner`, `ActivityActionHandler`
and the scheduler take a `java.time.Clock` and an id factory as constructor
parameters, and carry no injection annotations. `:app` provides the real clock;
tests provide a fixed one. That is the whole reason the recovery behaviour can be
tested deterministically.

**Ports point the dependency the right way.** `core-alarm` must open a screen it
must not depend on, and onboarding must create an alarm it must not know how to
arm. Both declare a small interface — `AlarmScreenLauncher`,
`OnboardingAlarmSetup` — that `:app` implements.

**A planning day never crosses midnight.** If the sleep target is after
midnight, the plan still ends at 23:59 and the next day starts fresh. Losing
that sliver removes an entire class of date-rollover bugs from the scheduler, and
the settings screen says so where it matters.

**One place decides each thing.** State transitions live only in
`ActivityStateMachine`. Placement lives only in `AdaptiveScheduler`. Applying a
user's decision lives only in `ActivityActionHandler` — which is why a tap on a
notification and a tap on the timeline cannot drift apart, and why Stage 4's AI
can propose an action without being able to perform one.

## Hooks for later stages

These exist now because adding them later would mean a migration or a
refactor, and for no other reason:

- `Activity.module: NesaModule` tags which capability owns an activity.
- `ScheduleBlock` is already separate from `Activity`, for recurrence.
- `ChangeReason` is persisted, so a night review can explain a whole day.
- `core-ui` reads every colour through one theme, for Stage 5's theme engine.
- Repository interfaces sit in the domain, for sync.
- `ActivityActionHandler` is the single validated entry point for an action,
  which is exactly the shape Stage 4's AI command validator needs.
