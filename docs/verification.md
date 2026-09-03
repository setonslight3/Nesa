# Verification status

The build contract for this project says: *never claim a feature works unless it
has been verified*, and *report exact failures*. This document is that report.

## The short version

**The whole project compiles.** A debug APK has been built and installed on a
device, and the application launches.

The deterministic core — the scheduler, the state machine, the recovery loop, the
alarm arithmetic, the wake challenges — **passes 92 unit tests.**

What remains unverified is *runtime behaviour*: that the alarm actually rings,
that reminders arrive, that state survives a restart. Compiling is not working.
The checklist at the end of this document says which items are which.

## What was verified, and how

### The build (verified on a separate machine)

The environment the code was written in has no Android SDK: `dl.google.com` is
blocked by network policy. The project was therefore built elsewhere, and the
result committed back.

Three changes were needed to compile, which are now in the history:

| Change | Why |
| --- | --- |
| `abiFilters` gained `x86`, `x86_64` | So the APK installs on an emulator. NESA has no native code, so this affects nothing at runtime. |
| `setBypassDnd(true)` removed from the alarm channel | It only ever took effect with Notification Policy Access, which NESA does not request — it was a no-op promising something the app had not earned. Do Not Disturb resistance comes from the ringer's `USAGE_ALARM` audio attributes instead, which are unchanged. |
| `org.gradle.tooling.parallel=true` | Build-tool setting only. |

Two things follow from that build succeeding, and both are real verification:

- **`core-storage` compiles, and its SQL is correct.** Room validates every
  `@Query` against the schema at compile time, so a successful build means every
  query in `Daos.kt` is valid. The generated schema is committed at
  `core-storage/schemas/.../1.json`: six tables, the cascade from
  `schedule_blocks` to `activities`, and the indices on `activityId` and `date`,
  all exactly as designed.
- **The Hilt graph is complete.** Hilt fails the build on a missing binding, so
  the receivers, services, workers and view models all resolve their
  dependencies — including the ports `:app` supplies for `AlarmScreenLauncher`
  and `OnboardingAlarmSetup`.

### The domain (verified continuously)

Maven Central and the Gradle plugin portal are reachable from the authoring
environment, so the domain tests run there on every change.

`core-model` and `core-scheduling` carry no Android dependency — that is a
deliberate architectural property, and it is what made verification possible at
all. Both were built with their **real production `build.gradle.kts` files**,
resolving their real declared dependencies, on JDK 21:

```
core-model       ChangeReasonCodecTest        5 tests
core-model       ModelInvariantsTest          8 tests
core-scheduling  ActivityActionHandlerTest    6 tests
core-scheduling  ActivityStateMachineTest     8 tests
core-scheduling  AdaptiveSchedulerTest       29 tests
core-scheduling  DayPlannerTest               7 tests
core-scheduling  DayWindowTest                6 tests
core-scheduling  MissedActivityDetectorTest   7 tests
core-scheduling  NextAlarmCalculatorTest      9 tests
core-scheduling  WakeChallengeTest            7 tests
                                        ─────────────
                                            92 tests, 0 failures
```

Two real bugs were found this way and fixed, both with a regression test:

1. The night-avoidance heuristic moved an activity that already fitted late in
   the evening, breaking the least-disruption rule.
2. A block kept its "no room today" explanation after the scheduler had placed it
   again, which left recovered work stranded in the needs-a-slot group.

To reproduce, on any machine with a JDK and no Android SDK:

```bash
mkdir /tmp/nesa-core && cd /tmp/nesa-core && touch build.gradle.kts
cat > settings.gradle.kts <<'SETTINGS'
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("/path/to/Nesa/gradle/libs.versions.toml")) } }
}
rootProject.name = "nesa-core-only"
include(":core-model")
project(":core-model").projectDir = file("/path/to/Nesa/core-model")
include(":core-scheduling")
project(":core-scheduling").projectDir = file("/path/to/Nesa/core-scheduling")
SETTINGS
gradle test
```

With the Android SDK present, `./gradlew test` runs the same tests as part of the
normal build.

Two further checks were run across the Android sources, which do catch a
worthwhile class of mistake without a compiler:

- Every `R.string` and `R.drawable` reference resolves, checked under
  non-transitive R semantics — so a resource used across a module boundary must
  be fully qualified, and each one is.
- Every `import com.nesa.*` resolves to a symbol that exists in the repository.

## What was not verified

Compiling proves the code is well-formed. It proves nothing about behaviour.

| Area | Still unproven |
| --- | --- |
| The alarm | That it fires at the right minute, takes over a locked screen, survives a reboot, and retries when unanswered. The highest-risk area in the project. |
| Notifications | That reminders are delivered, and that their actions apply the right decision. |
| Persistence | That the timeline, alarm and settings genuinely survive a restart. |
| The scheduler on real data | The rules are tested exhaustively in isolation; they have not been watched working against a real day. |
| Compose screens | They compile and the app launches. Individual screens have not been walked through. |

There are no instrumented tests yet. `core-storage` already declares
`room-testing` for that purpose.

## Stage 1 checklist

Against the checklist in the build instructions. "Implemented" means the code
exists and was reviewed; "verified" means it was executed.

| # | Item | Status |
| --- | --- | --- |
| 1 | Fresh install launches successfully | **Verified.** Debug APK built, installed, and launched on a device. |
| 2 | Onboarding completes without configuring optional modules | Compiles. Three steps, all skippable, all defaulted. Not walked through. |
| 3 | User can add an activity | Compiles. Not walked through. |
| 4 | Activity can be fixed/flexible with a priority | Compiles; five flexibilities and four priorities are in the editor with plain-language help. Not walked through. |
| 5 | Scheduler moves a flexible activity without moving a fixed anchor | **Verified.** Four dedicated tests, including a critical flexible activity losing to an anchor. |
| 6 | Missed and skipped behave differently | **Verified.** Separate states, separate causes, separate history; a test asserts no screen can ever raise `MISS`. |
| 7 | Timeline survives app restart | Storage layer **verified to compile** with the intended schema; the UI renders only from Room and holds no cache. The restart itself has not been observed. |
| 8 | Alarm configuration persists | Same: schema verified, `NesaAlarmCoordinator` persists before arming by design. Not observed. |
| 9 | Wake challenge works offline | **Partly verified.** Generation and difficulty adaptation are tested and involve no network or arithmetic. The four screens compile but have not been played. |
| 10 | Notifications work under supported permissions | Compiles, with permission checks, a `SecurityException` path, and a degraded mode the settings screen explains. Not observed. |
| 11 | Light/dark/system theme behaviour | Compiles. Not observed. |
| 12 | Tests pass | **Verified.** 92 domain tests, 0 failures. |

**Stage 1 has not fully passed its gate.** Four items — 1, 5, 6, 12 — are
verified. The rest compile and are structurally sound, but have not been watched
working on a device.

## The gate: five checks

These are the observations that would close the remaining items. They take
roughly ten minutes on a real phone.

1. **Alarm.** Set one two minutes out, lock the screen, and wait. It should take
   over the lock screen; the challenge should stop it and nothing else should.
   *Closes 9, and the riskiest part of 8.*
2. **Alarm across a reboot.** Set one ten minutes out, restart the phone, and
   leave it. It should still fire. *This is the one most likely to fail on a
   manufacturer skin, and the one that matters most.*
3. **Anchor protection on real data.** Add a fixed 09:00–11:00 commitment and a
   flexible activity at 09:30. The flexible one should land at 11:00 with an
   explanation naming the anchor. *Closes 3 and 4, and confirms the tested engine
   behaves the same through the UI.*
4. **Restart.** Force-stop and reopen. Timeline, alarm and settings unchanged.
   *Closes 7 and 8.*
5. **Reminder.** Leave an activity due and wait for the notification; tap
   "Do later" and check the timeline agrees. *Closes 10.*

Switching the theme in settings covers 11 in passing.

## Known follow-ups

- **The debug APK is committed to the repository** at `apk/NESA-debug.apk`
  (19 MB). Git keeps every version of it forever, so each rebuild that gets
  committed adds another 19 MB to every future clone. A GitHub Release, or a CI
  artifact, delivers the same file to a phone without that cost.
- **No instrumented tests yet.** `core-storage` already declares `room-testing`;
  the migration path and the DAO round-trips are the first things worth covering
  there.
- **Do Not Disturb.** Worth one deliberate check that the alarm still sounds with
  DND on. It should, via `USAGE_ALARM`, but it is the kind of thing to confirm
  rather than assume.
