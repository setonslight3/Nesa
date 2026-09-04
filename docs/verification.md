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

## Gate run 1 — the alarm did not fire

The first attempt at check 1 failed: the alarm time arrived and nothing
happened, with no sound and no screen. An audit of the alarm path found three
defects, all in the handoff from the broadcast receiver to the ringing service,
and any one of them alone is enough to produce exactly that silence:

1. **`startForeground()` was called inside a coroutine**, after a database read.
   Android allows five seconds from `startForegroundService()` and kills the
   process when that passes.
2. **Three paths never called it at all** — the alarm-deleted case, and all three
   of snooze, dismiss and sleep-in, which `AlarmRingActivity` also starts as
   foreground services.
3. **Android 12+ blocks a background foreground-service start** unless the
   triggering alarm was exact. The inexact fallback therefore lost not just
   punctuality but the alarm entirely.

A fourth defect was found while fixing those: the alarm settings screen saved on
`viewModelScope`, so leaving the screen quickly could cancel the write that armed
the alarm.

All four are fixed, and the alarm path now logs each decision so a future silent
failure is diagnosable from logcat rather than by inspection. **None of this is
unit-testable** — it is service lifecycle behaviour — so it needs a device to
confirm, which is the point of running the gate.

## Gate run 2 — further alarm hardening

Built and applied on the VPS after gate run 1, then reviewed here. Two findings
were genuine improvements and are kept:

- **Doze.** The inexact fallback used `setWindow`, which does **not** wake a
  device from Doze — so on a phone idle overnight the alarm could have been
  swallowed regardless of everything else. The fallback is now
  `setExactAndAllowWhileIdle`, then `setAndAllowWhileIdle`, both of which wake
  from Doze. This composes correctly with the receiver's notification fallback,
  since `setAndAllowWhileIdle` is inexact and so does not grant the
  foreground-service exemption.
- **Audio.** The ringer now tries the alarm, ringtone, notification and system
  default URIs in turn, rather than giving up if the first is unset, and starts
  at a quarter volume instead of silence so the first seconds are audible.

Two were corrected:

- The alarm settings screen created the alarm **enabled**, which would arm an
  alarm for a user who had declined one during onboarding. It creates a disabled
  alarm again; setting a time still enables it, which is the case that mattered.
- `AlarmRingActivity` called `requestDismissKeyguard`, which raises the PIN
  prompt on a secure device. `setShowWhenLocked` already shows the alarm over the
  lock screen, and putting authentication between a half-asleep person and their
  alarm is the wrong trade.

## Gate run 3 — the device, not the code

Every permission in the reliability screen read Granted — background execution,
exact alarms, notifications — and the alarm still did not fire. That eliminated
permissions, and the user then established the decisive fact: **the alarm fires
only while NESA is on screen.** Leaving the app, without even closing it, stops
it. The phone is an Infinix Smart 9.

That is not a bug. Transsion's software (Infinix, Tecno, itel) freezes an app
when it leaves the foreground, and a frozen process does not receive its alarms.
No permission changes it because it is not a permission.

It did expose one real defect. The user also reported that when the alarm did
surface it was a silent notification and popup. The alarm channel sets its sound
to null deliberately, because `AlarmRingerService` was meant to do the ringing —
so when the receiver's notification fallback ran in place of the service, nothing
could make a noise. Audio now lives in `AlarmAudioPlayer`, which the ringing
screen drives as well; a foreground activity has none of the restrictions a
background service does.

The device behaviour itself is answered by an optional keep-alive foreground
service, off by default, since the cost is a permanent notification.

## Gate run 4 — the chain is correct; the platform is late

The on-device trace settled it. From an Infinix Smart 9, with the keep-alive
service on:

```
21:56:49  armed for 21:57:49 (exact=true)
22:00:53  receiver fired — alarm is due
22:00:53  ringer service start requested
22:00:53  ringer became a foreground service
22:00:54  audio: playing
22:00:54  ring screen opened
22:01:05  alarm dismissed
22:01:05  armed for 07:00 (exact=true)
```

Two conclusions, and neither needed a guess.

**NESA's chain is correct end to end.** Armed as an exact alarm; receiver fired;
service promoted to the foreground; audio started; the screen opened; the outcome
was recorded and the next occurrence armed. Nothing in that sequence is missing
or out of order, and nothing re-armed in between, which rules out NESA cancelling
its own alarm.

**The alarm was not delivered until the app was reopened.** Scheduled for
21:57:49, delivered at 22:00:53 — and the user reports that 22:00:53 is the
moment they returned to the app. So this is not three minutes of delivery
jitter, which is how it first reads. The broadcast was not delivered *at all*
while the process was not running, and arrived the instant the process came
back. `setAlarmClock` is the strongest guarantee Android offers and it was still
withheld, which places the remaining fault outside the application.

The distinction matters: jitter would be a tuning problem, while a broadcast
held until process start is the phone refusing to run NESA at all — and it means
the keep-alive service is either not running or not being respected. The build
that follows records the service's own start and stop so that is no longer a
guess.

That is now measured rather than inferred: the scheduled time travels with the
alarm, and the receiver records how late delivery actually was. The keep-alive
service also records its own start and stop, so a trace showing it dying before a
late alarm is direct evidence the phone is killing it despite the foreground
notification.

## Gate run 5 — two traces, two separate answers

Two controlled tests on the Infinix, and they separate the problems cleanly.

**In the foreground, delivery is perfect.**

```
22:27:02  armed for 22:28:02 (exact=true)
22:28:02  receiver fired on time
22:28:03  audio: alarm volume 1/15
22:28:03  audio: playing
```

**In the background, the alarm is withheld until the app is reopened.**

```
22:30:03  armed for 22:31:03 (exact=true)
22:35:21  receiver fired 257s LATE — the system held it back
```

So NESA's scheduling is correct, and the delivery problem is entirely the phone
declining to run the process. Neither trace contains a `keep-alive` line, and the
alarm was withheld anyway, so the keep-alive service is not helping on this
device.

**And the silence had a completely separate cause.** The alarm stream was at
1/15 — under seven per cent of maximum — which the player then multiplied by its
own fade floor of 0.25, giving about 1.7% of maximum. Audible to nobody. The
earlier check only raised the volume from *exactly* zero, so 1/15 walked straight
past it. Anything below 40% of maximum is now treated as silent and raised, and
the fade floor is higher.

That is worth separating: the alarm has been inaudible on every run for a reason
that has nothing to do with the delivery problem, and fixing delivery would never
have revealed it.

### The device ceiling, and the way past it

Transsion's software will not run a third-party alarm reliably in the background,
and a foreground service does not change its mind. That is a ceiling application
code cannot pass.

The clock app that shipped with the phone is exempt from all of it. So
`SystemAlarmHandoff` lets the user hand NESA's wake time to that app through
`AlarmClock.ACTION_SET_ALARM`, a documented public API. What is lost is stated on
the screen rather than glossed: the wake challenge does not run, dismissal
happens in the clock app, and NESA cannot later change or delete what it created
because Android exposes no API for that. It is therefore a deliberate handoff the
user performs, not a background sync that would fill their clock app with
duplicates.

## Gate run 6 — the process is frozen, and that is a ceiling

Delivering the alarm as a direct service start rather than a broadcast changed
nothing:

```
00:09:23  armed for 00:10:23 (exact=true)
00:11:40  alarm delivered 77s LATE (direct to service)
00:11:41  audio: playing
00:11:41  alarm screen launched over the foreground
```

Two things are now ruled out, and the elimination is what matters:

- **Not broadcast deferral.** A `getForegroundService` PendingIntent is not a
  broadcast and is not subject to that queue. It was held back just the same.
- **Not process death.** There is no `app process started` line anywhere in the
  trace, so the process was never killed and recreated. It survived the whole
  test.

What remains is that the process was **frozen** — suspended by the manufacturer's
power manager — and everything queued for it waited until the app was reopened
and the system thawed it. A frozen process cannot execute code by definition, so
there is no API an application can call to escape this. It is the mechanism
working as its authors intended.

Everything NESA controls is correct and has been for several builds: armed as an
exact alarm at the right time, and on delivery the service promotes, the audio
plays, and the screen takes over the foreground. The failure is entirely in the
handoff, and the handoff is the platform's.

### What is genuinely unknown

Whether the keep-alive foreground service is running at all. A foreground service
should exempt a process from freezing, and no trace has ever contained a
`keep-alive` line. The reliability screen now states which it is, and that
answer decides whether there is anything left to try or whether this device is
simply a documented limitation.

## Gate run 7 — the ceiling was ours, not the device's

Gate run 6 concluded that a frozen process is a platform ceiling with no API to
escape it. **That conclusion was wrong, and this is the correction.**

The evidence that overturned it: a third-party alarm app installed from the Play
Store on the same Infinix Smart 9 rings correctly after being swiped out of
Recents, and it asked for no permission setup at all. A device that will do that
for one app will do it for NESA. So the difference is architectural, not a
manufacturer limitation.

### What the difference is

The freezing diagnosis from gate run 6 still holds — the trace has no
`app process started` line, so the process was alive and suspended, not killed.
What was wrong was the claim that nothing can be done about it. A process with a
running foreground service is not put in the cached-app freezer. That is the
whole mechanism, and it is what every reliable third-party alarm app on Android
is doing when it shows a quiet "next alarm" notice.

Gate run 6 raised exactly this and called it "genuinely unknown": *whether the
keep-alive foreground service is running at all… no trace has ever contained a
`keep-alive` line.* The answer is more uncomfortable than that.
`NesaKeepAliveService` **did** exist, the user did switch it on, and the alarm
still arrived late — and it was then deleted in the previous commit for being
the shape the build spec warned against. Two things about that are worth being
precise on, because they decide whether this run is repeating a failed
experiment:

- It never once wrote `keep-alive: started` to the trace, although its
  `onCreate` did exactly that. So there is no evidence it ever ran. Every one of
  its start paths swallowed failure inside a bare `runCatching`, which means a
  refused start and a start that was never attempted looked identical — and both
  looked like the service simply not working.
- It also never declared `android:stopWithTask="false"`. The documented default
  is already `false`, but on a skin that treats swiping from Recents as a
  near-force-stop, stating it is not redundant.

So the previous attempt did not test this hypothesis; it tested a service that
may never have started. The one this run adds cannot fail quietly: it records
being switched off, being refused, starting, the app being swiped away, and
being destroyed. Whatever happens next, the trace will name it.

### What changed

- **`AlarmWatchService`** — a foreground service that runs while, and only
  while, an alarm is armed. It holds no timer and does no work; AlarmManager
  still owns the schedule entirely. Its single purpose is to keep the process out
  of the freezer so that AlarmManager's delivery is not queued behind a thaw.
  `android:stopWithTask="false"` is what makes it survive the swipe.
- **`NesaAlarmCoordinator.refreshWatch`** — every path that changes the schedule
  ends here, so "an alarm is armed" and "the watch is running" cannot drift
  apart.
- **A switch in the reliability screen** — on by default, because the failure it
  prevents is worse than one silent low-priority notification, but it is the
  user's to turn off.
- **`AlarmReceiver` is no longer an `@AndroidEntryPoint`.** It was, and it read
  injected fields in its first few lines, which made Hilt build the entire
  singleton graph before `onReceive` did anything. That is not the cause of a
  77-second delay, but it sat between the platform waking NESA and NESA writing
  down that it had been woken — so the trace could not distinguish "Android was
  late" from "we were slow to look". The ordinary path now touches no injected
  object at all; the graph is reached only on the fallback branch, which has
  already failed by the time it runs.
- **`AlarmEventLog.write(context, message)`** — a static, dependency-free write
  that a receiver can call as its first statement, using `commit()` rather than
  `apply()` so a receiver torn down immediately still leaves its trace.
- **`onTaskRemoved` and `onDestroy` are traced.** The next trace can therefore
  read `watch running` → `app swiped from recents` → `receiver fired on time`,
  or show `watch stopped` at the moment the manufacturer killed it — which
  answers the remaining question directly rather than by inference.

### What this does not yet claim

**Nothing here is verified.** It has not been compiled and it has not run on a
phone. The build spec's rule stands: this is not fixed until the physical-device
test succeeds. The test is the one below, and the trace decides:

- `receiver fired on time` after `app swiped from recents` — fixed.
- `watch stopped` before a late delivery — the manufacturer kills foreground
  services too. The next thing to try would be running the alarm in its own
  `android:process`, so that removing the UI task cannot touch it; that is a
  real change (Room, Hilt and the event log all stop being single-process) and
  is not worth attempting until the trace says it is needed. Failing that, the
  honest answer is the handoff to the system clock app that already exists in
  settings.
- No `watch running` line at all — the service never started; the trace will say
  whether the platform refused it.

## Gate run 8 — the watch survives the swipe, and the alarm still runs late

Two tests on the Infinix Smart 9, back to back.

**A — left the app the usual way (Home, task still in Recents):**

```
12:57:16  armed for 12:58:16.226222 (exact=true)
12:57:16  watch running (next 12:58)
12:59:27  receiver fired 70s LATE — the system held it back
```

Did not ring on its own. Delivery again landed at the moment the app was
reopened. Unchanged from every run before this one.

**B — swiped NESA out of Recents:**

```
13:00:24  armed for 13:01:24.259676 (exact=true)
13:00:24  watch running (next 13:01)
13:00:28  app swiped from recents — watch still running
13:01:50  receiver fired 26s LATE — the system held it back
13:01:50  ringer became a foreground service
13:01:51  audio: alarm volume 10/15  ·  audio: playing
13:01:52  alarm screen launched over the foreground  ·  ring screen opened
13:02:02  alarm dismissed
13:02:02  armed for 07:00 (exact=true)  ·  watch running (next 07:00)
```

**This is the first time in the whole investigation that the alarm rang without
the app being reopened.** It rang, it vibrated, the wake challenge ran, the
challenge dismissed it, and the next occurrence re-armed with the watch back up
behind it. Every step after delivery is correct and has been for several builds.

### What is fixed

The watch survives `onTaskRemoved` and keeps the process reachable across the
swipe. That is what gate run 7 set out to establish, and it holds.

### What is not fixed, and must not be recorded as fixed

- **Test B was still 26 seconds late.** For a 07:00 alarm that is 07:00:26. It
  rang unattended, which is the thing that matters most, but "late by half a
  minute" is not what an alarm clock promises.
- **Test A is still the original failure, in full.** 70 seconds, and no ring
  until the app came back. Pressing Home is the ordinary way to leave an app;
  it cannot be the case that NESA only works if the user swipes it away.

### The inversion, which is the useful clue

Swiping the app away now produces a **better** result than backgrounding it —
26 seconds against 70. That is backwards for a cached-app freezer, which should
punish the removed task at least as hard. A process holding a foreground service
with no task behaves differently on this ROM from the same process with a
backgrounded task, which points away from the freezer alone and towards the
delivery path itself.

The next hypothesis to test: Transsion's auto-start/background policy gates
**manifest-declared broadcast receivers** independently of whether the process is
running. NESA's alarm PendingIntent names `AlarmReceiver` as an explicit
component, so every delivery goes through that gate. A receiver registered at
runtime by the live watch service is not a manifest component and is not subject
to it. That is a concrete, testable change and it is where this goes next if the
remaining lateness is to be closed.

## Ringtone and volume, and what is deliberately left open

Added after gate run 8, at the user's request, and separately from the lateness:

- **A ringtone picker.** `RingtoneManager.ACTION_RINGTONE_PICKER` rather than a
  list of NESA's own, so the user gets every sound the phone already has, in the
  screen they know from the clock app. `Alarm.soundUri` already existed and
  `AlarmAudioPlayer` already honoured it — only the way to set it was missing.
  The picker's "Silent" entry is suppressed: an alarm that cannot make a sound
  is not a preference this app stores.
- **A volume slider, 10–100%.** `Alarm.volumePercent`, new, with a Room
  migration (schema 1 → 2, `ALTER TABLE alarms ADD COLUMN volumePercent`) because
  destructive fallback is off and there is real data on the test phone.

The volume change also replaces a judgement that turned out to be wrong.
`AlarmAudioPlayer` used to raise the device's alarm stream only when it found it
below 40% of maximum, reasoning that overriding a level the user had chosen would
be rude. But the alarm stream is global and anything can move it — the phone was
found at 1/15 — so "the level the user chose" was never knowable from there. The
user now chooses it per alarm, this honours it, and the device's own level is put
back when the alarm stops.

Three new domain tests cover the volume floor, the ceiling, and the default.

### Still open, and recorded rather than fixed

**The alarm is 26–70 seconds late on the test device, and pressing Home still
defers it entirely.** This is gate run 8's finding and it is not addressed by
anything above. The next thing to try is written down there: a receiver
registered at runtime by the live watch service, so delivery does not pass
through a manifest component and the manufacturer's auto-start gate. Stage 2
begins with this outstanding, at the user's decision, and it stays here until
it is closed.

## The gate: five checks

These are the observations that would close the remaining items. They take
roughly ten minutes on a real phone.

1. **Alarm.** Set one two minutes out, **swipe NESA out of Recents**, lock the
   screen, and wait. It should take over the lock screen; the challenge should
   stop it and nothing else should. Then reopen NESA and read the trace on the
   reliability screen — it is the trace, not the ringing, that says which of the
   three outcomes above happened. *Closes 9, and the riskiest part of 8.*
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
