# Verification status

The build contract for this project says: *never claim a feature works unless it
has been verified*, and *report exact failures*. This document is that report.

## The short version

The deterministic core — the scheduler, the state machine, the recovery loop, the
alarm arithmetic, the wake challenges — **compiles and passes 92 unit tests.**

The Android layers — Room, DataStore, notifications, the alarm platform, and
every screen — **have not been compiled.** They were written and reviewed but not
built, because the environment they were written in could not reach the Android
SDK.

Read the next two sections before treating anything here as working software.

## What was verified, and how

The environment this was built in has no Android SDK: `dl.google.com` is blocked
by network policy, which is where both the SDK and every AndroidX artifact come
from. Maven Central and the Gradle plugin portal are reachable.

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

**Nothing below has been compiled.** Treat all of it as a careful first draft.

| Area | Risk |
| --- | --- |
| `core-storage` | Room's annotation processor has not run. Entities, DAO queries and the generated schema are unchecked. |
| `core-settings` | DataStore API usage is unchecked. |
| `core-notifications`, `core-alarm` | Android API usage, manifest merging and Hilt graph construction are unchecked. |
| All `feature-*` and `:app` | Compose API usage, view models, navigation and the Hilt graph are unchecked. |
| Dependency versions | Chosen from knowledge, not resolved. AGP 8.7.3 / Gradle 8.11.1 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / Hilt 2.52 / Compose BOM 2024.10.01 is a combination believed compatible, but the resolution has not been performed. |
| Runtime behaviour | Nothing has run on a device or emulator. |

Expect a first build to surface import and API-signature errors. The domain
logic underneath is the part that has been tested.

## Stage 1 checklist

Against the checklist in the build instructions. "Implemented" means the code
exists and was reviewed; "verified" means it was executed.

| # | Item | Status |
| --- | --- | --- |
| 1 | Fresh install launches successfully | **Not verified** — never run |
| 2 | Onboarding completes without configuring optional modules | Implemented. Three steps, all skippable, all defaulted. Not run. |
| 3 | User can add an activity | Implemented in `ActivityEditorScreen`. Not run. |
| 4 | Activity can be fixed/flexible with a priority | Implemented; the five flexibilities and four priorities are in the editor with plain-language help. Not run. |
| 5 | Scheduler moves a flexible activity without moving a fixed anchor | **Verified.** Four dedicated tests, including a critical flexible activity losing to an anchor. |
| 6 | Missed and skipped behave differently | **Verified.** Separate states, separate causes, separate history; a test asserts no screen can ever raise `MISS`. |
| 7 | Timeline survives app restart | Implemented — the UI renders only from Room, and there is no in-memory cache. The persistence itself is unverified. |
| 8 | Alarm configuration persists | Implemented. `NesaAlarmCoordinator` persists before arming, deliberately, so a crash between the two is recoverable. Room path unverified. |
| 9 | Wake challenge works offline | **Partly verified.** Generation and difficulty adaptation are tested and involve no network or arithmetic. The four Compose screens are unverified. |
| 10 | Notifications work under supported permissions | Implemented, with permission checks, a `SecurityException` path, and a degraded mode the settings screen explains. Not run. |
| 11 | Light/dark/system theme behaviour | Implemented; one palette, two variants, same information architecture. Not run. |
| 12 | Tests pass | **Verified.** 92 domain tests, 0 failures. |

**Stage 1 has not passed its gate.** Items 1, 3, 7, 8, 10 and 11 need a build and
a device. The honest summary is that the thinking and the deterministic core are
done and tested; the Android shell around them is written but unproven.

## What to do next

1. Open the project in Android Studio and run `./gradlew assembleDebug`. Fix the
   compile errors; expect them in the Compose screens first.
2. Run `./gradlew test` — the 92 domain tests should still pass, and they are the
   regression net while the rest is fixed.
3. Work down the checklist on a device, starting with the alarm, which is the
   feature with the most platform risk and the least tolerance for failure.
4. Add instrumented tests for the Room layer; `core-storage` already declares
   `room-testing` for exactly this.

Only then is Stage 1 complete, and only then should Stage 2 begin.
