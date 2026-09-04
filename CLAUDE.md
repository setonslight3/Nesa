# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What NESA is

An Android-first adaptive personal assistant. It plans a practical day, protects
commitments that cannot move, and helps the user recover the ones that slip.

**It is being built in five stages, and the stages are gates, not suggestions.**

**`docs/spec/` holds the three specification documents verbatim, and they are the
source of truth.** Read them before deciding what belongs where. They were absent
from this repository until Stage 2, and their absence cost real work: the fitness
module was built a whole stage early on an inference from a one-word list. If the
code and the specification disagree, the code is wrong.

| Stage | Scope |
| --- | --- |
| 1 — Core | Onboarding, timeline, activities, scheduling, alarm, wake challenge, notifications, settings |
| 2 — Life | Work/school/training schedules, recurrence, optional prayer and meals, recovery, night review, missed-activity review, daily/weekly statistics |
| 3 — Health & Focus | Fitness, learning, focus/screen-time |
| 4 — Intelligence | AIProvider abstraction, Gemini adapter, typed commands, granular permissions, voice |
| 5 — Personalize | Themes, custom colours, backgrounds, widgets, advanced personalization, polish |

Stage 1 is implemented. **Stage 2 (Life) is feature-complete and has not yet
passed its gate.** Fitness is built but
belongs to Stage 3 — see `docs/roadmap.md` for why it was not reverted. Do not
implement a later stage's features early. A small architectural hook is
acceptable; a half-built feature is not.

`docs/verification.md` records exactly what has been verified and what has not.
**Read it before claiming anything works.** Stage 1 has not yet passed its gate.

**There is one known, open defect, and it is the important one.** On the test
device (Infinix Smart 9, Transsion XOS) everything NESA schedules through
`AlarmManager` — the wake alarm and the activity reminders alike — is delivered
26 to 77 seconds late, and the delay ends the moment the user reopens the app.
Everything after delivery works. `docs/verification.md` has a section headed
**UNRESOLVED** that carries the full evidence, what has been ruled out and why,
and the next thing to try. Read that section before touching the alarm path;
six of nine attempts at this went wrong by theorising from the symptom instead
of reading the on-device trace.

## Two agents work on this repository

Claude writes the code; Antigravity on the VPS compiles it and runs it on a
device. **Read `docs/build-handoff.md` before changing anything** — it says who
does what and why, and `docs/build-log.md` carries the latest build result.

The rule that matters: put the *reasoning* for a non-obvious decision in a code
comment, not only in a chat. Neither agent can see the other's conversation, and
an unexplained decision gets "fixed" back.

## Commands

```bash
./gradlew assembleDebug          # build the app
./gradlew installDebug           # build and install to a connected device
./gradlew test                   # all unit tests
./gradlew :core-scheduling:test  # the domain tests — the ones that matter most
./gradlew connectedAndroidTest   # instrumented tests (needs a device/emulator)
./gradlew lint                   # Android lint

python3 tools/check-imports.py   # missing-import check for anyone who cannot compile
```

Run a single test class or method:

```bash
./gradlew :core-scheduling:test --tests "*AdaptiveSchedulerTest"
./gradlew :core-scheduling:test --tests "*AdaptiveSchedulerTest.a day that already fits*"
```

Test names are backtick-quoted sentences, so wildcards are usually easier than
exact names.

Watching the alarm, which is the hardest part to diagnose:

```bash
adb logcat -c && adb logcat | grep -iE "Nesa|FATAL|ForegroundService"
```

The arming path logs at each decision (`NesaAlarmScheduler`,
`NesaAlarmCoordinator`, `NesaAlarmReceiver`, `NesaAlarmRinger`). A silent alarm
failure should always be diagnosable from logcat — if it isn't, that is a defect
in its own right.

Requires the Android SDK (compileSdk 35) and JDK 17+. minSdk is 26, chosen so
`java.time` works without desugaring.

## Architecture

Full detail in `docs/architecture.md`; the scheduler's rules in
`docs/scheduling.md`; platform limitations and their fallbacks in
`docs/android-platform.md`.

### The rule that holds it together

**Dependencies point inward, and repository contracts live in the domain.**

`core-model` and `core-scheduling` are plain Kotlin/JVM libraries — no Android,
no Room, no Compose. `core-model/repository/Repositories.kt` declares the
persistence interfaces over `Flow`; `core-storage` (Room) and `core-settings`
(DataStore) implement them.

The consequence, which is worth preserving: **no feature module depends on
`core-storage`.** Check any `feature-*/build.gradle.kts` — they depend on
`core-model` and the use cases, nothing else from the data layer.

```
:app  →  feature-*  →  core-ui  →  core-{storage,settings,notifications,alarm}
                                              ↓
                                       core-scheduling  →  core-model
```

`:app` is the composition root. It is the only module that knows how everything
is wired, and it supplies the ports lower layers declare but cannot satisfy
(`AlarmScreenLauncher`, `OnboardingAlarmSetup`).

### Single sources of truth

Each of these exists exactly once, and new code must go through them rather than
reimplementing the decision:

| Decision | Lives in |
| --- | --- |
| Where an activity is placed | `AdaptiveScheduler` (pure function) |
| How state changes | `ActivityStateMachine` (one transition table) |
| Applying a user's decision | `ActivityActionHandler` |
| Turning silence into MISSED | `MissedActivityDetector`, via `DayPlanner` |
| What happens to an alarm next | `NesaAlarmCoordinator` |
| Training figures (streak, volume, rest) | `WorkoutProgress` (pure object) |
| Closing a day and recovering it | `NightReview` (pure object) |
| Daily/weekly figures | `PlanStatistics` (pure object) |
| A schedule's activities | `LifeScheduleApplier` (derived ids, idempotent) |

This is why a tap on a notification and a tap on the timeline cannot drift apart,
and it is the shape Stage 4's AI command validator will need: AI proposes
intents, this layer decides.

### Things that will bite you

- **`Activity` (what) and `ScheduleBlock` (when) are separate.** This is what
  let recurrence arrive without touching the scheduler: one activity carrying a
  `Recurrence` produces a block on every day it matches, and `AdaptiveScheduler`
  still sees an ordinary list of blocks. `RecurrenceMaterialiser` creates the
  missing ones and is idempotent — `DayPlanner.refresh` runs it on every pass,
  so a second block would mean a duplicate reminder and a duplicate timeline row.
- **Room entities store primitives only** — no type converters, no enums, no
  `java.time`. `mapper/EntityMappers.kt` is the single translation point, and
  unknown enum values fall back to a default rather than crashing a launch.
- **A planning day never crosses midnight.** A sleep target after midnight still
  ends the plan at 23:59. This removes a class of date-rollover bugs; do not
  "fix" it without reading `DayWindow`.
- **Domain use cases take a `Clock` and an id factory** as constructor
  parameters and carry no injection annotations. `:app` provides the real ones;
  tests provide fixed ones. Do not add `@Inject` to them — `AppModule` has
  `@Provides` for them, and both would be a duplicate binding.
- **Foreground services must call `startForeground` synchronously**, on every
  path, before any suspend work. Getting this wrong is what made the alarm fail
  silently; see `AlarmRingerService.promoteToForeground`.
- **The fitness module is off by default** (`NesaSettings.fitnessEnabled`).
  Nothing about training appears anywhere while it is off — that is the product
  rule about not forcing a module on people, and it is enforced by the settings
  toggle gating the only route in. Turning it *off* never deletes routines or
  logged sessions: those are the user's record, not the module's scaffolding.
- **A workout is scheduled as an ordinary `Activity`** with
  `module = NesaModule.FITNESS`. `AdaptiveScheduler` places it by the same rules
  as anything else. The fitness module owns plans and history; it never places.
- **SKIPPED and MISSED are different things** throughout the product. A skip is a
  user decision; a miss is the absence of one. `ActivityEvent.MISS` is never
  offered by any screen, and a test asserts that for every state. Never infer a
  skip from silence.

## Product rules that constrain implementation

From the product specification. These are not preferences:

- Balanced guidance is the default; persistence means a bounded number of
  meaningful reminders, never notification spam.
- Fixed anchors are protected. Flexible activities move around them.
- The evening is the overflow/recovery zone; the night winds down and protects
  the sleep target.
- Users are never forced to configure a module they do not use.
- The core must stay useful offline. Nothing wake-critical may depend on the
  network or on background work completing.
- Nothing important is ever silently deleted — unplaceable work comes back as an
  `UnplacedItem` and stays visible.

## Testing

The domain modules carry the weight: 167 unit tests over the scheduler, the state
machine, missed-vs-skipped, DST edge cases, challenge generation and the recovery
loop. They run on a JDK with no emulator, and they are the regression net for
everything else.

Service lifecycle and Compose screens are not unit-tested. When changing the
alarm path, verify on a device and say so — `docs/verification.md` distinguishes
"compiles" from "observed working", and that distinction should be maintained
honestly.
