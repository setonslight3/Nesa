# Build log

Append-only. Newest entry at the top. Antigravity records build results here;
Claude reads it before making the next change.

Format: date, commit built, outcome, and the exact output if it failed.

---

## Pending — Stage 2 begins: recurrence

Activities can now repeat. Domain-first, as usual:

- `Recurrence` in core-model — a flat data class, not a sealed hierarchy,
  because Room stores primitives only. Daily/weekly/monthly with an interval,
  named days, an optional anchor and an optional end date. `occursOn(date)` is
  pure and total.
- `RecurrenceMaterialiser` in core-scheduling — creates the blocks a day is
  missing and is **idempotent**, which is the safety property that matters:
  `DayPlanner.refresh` runs on every pass. `AdaptiveScheduler` is untouched; a
  recurring activity is placed by exactly the same rules as a hand-added one.
- **Schema 2 → 3** — five columns on `activities`, every existing row defaulting
  to NONE so nothing a user already has starts repeating behind their back.
- `ActivityRepository` gains `repeatingActivities()` and `addBlocks()`.
- Repeat chips on the activity editor: Once / Every day / Weekdays / Certain days.

Fourteen new domain tests, including the every-other-week case that keeps both
of its days in the same week, and the monthly rule on the 31st that still
happens in February. Expect **115 domain tests**.

`python3 tools/check-imports.py` reports 0 problems. Not compiled here — no
Android SDK in this environment.

## Pending — reminders that can be heard, and the alarm left open

The reminders channel was created at `IMPORTANCE_DEFAULT`: a sound, never a
heads-up pop-up. A channel's importance is frozen at creation and Android
ignores later attempts to raise it, so on any phone that already had NESA
installed no change to the old channel id could have produced a pop-up. The id
is now `nesa_reminders_v2` at `IMPORTANCE_HIGH` with vibration, and the old
`nesa_reminders` channel is deleted so it does not linger in the user's
notification settings.

The alarm's delivery lateness is **not fixed** and is now recorded as the
project's one open defect, in full, under the UNRESOLVED heading in
docs/verification.md and pointed at from CLAUDE.md. Stage 2 starts with it open,
by the user's decision.

`python3 tools/check-imports.py` reports 0 problems. Not compiled here — no
Android SDK in this environment.

## 2026-09-04 — 2275622 — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 101/101 domain tests).
Uploaded updated `NESA-debug.apk` to GitHub Release `v0.1.0-stage1`.

Delivers:
- System Ringtone Picker via `RingtoneManager.ACTION_RINGTONE_PICKER` directly in Alarm Settings.
- Per-alarm volume slider (10%–100%, default 80%) with automatic system stream level adjustment during ring and full restore on alarm stop.
- Room database schema migration (Version 1 → 2) via `NesaMigrations` safely preserving existing user alarms.
- 3 new domain tests verifying volume bounds and validation invariants (101 total domain tests).

The schema 1 → 2 migration built and the tests passed, so `NesaMigrations`
covers the new `volumePercent` column correctly and an existing install keeps
its alarms.

## 2026-09-04 — d211ff8 — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 98/98 domain tests).
Uploaded updated `NESA-debug.apk` to GitHub Release `v0.1.0-stage1`.

Delivers:
- `AlarmWatchService`: Foreground service active strictly while an alarm is armed (`android:stopWithTask="false"`), preventing process caching/freezing on aggressive OEM ROMs (like Infinix/Transsion).
- `NesaAlarmCoordinator.refreshWatch`: Synchronizes alarm arming state and watch lifecycle to ensure no drift.
- "Keep NESA ready to ring" setting toggle in reliability screen (enabled by default).
- Direct, DI-free static `AlarmEventLog.write(context, message)` to capture receiver execution before DI injection.
- Complete trace logging for watch service state and lifecycle events.

with no permission setup at all. The correction and the reasoning are in
docs/verification.md gate run 7 and docs/android-platform.md.

Changed:

- New `AlarmWatchService` — a foreground service that runs only while an alarm
  is armed, with `android:stopWithTask="false"`. It holds no timer; AlarmManager
  still owns the schedule. Its purpose is to keep the process out of the
  cached-app freezer so delivery is not queued behind a thaw.
- `NesaAlarmCoordinator.refreshWatch` — every path that changes the schedule
  now ends there, so an armed alarm and a running watch cannot drift apart.
- A "Keep NESA ready to ring" switch on the reliability screen, on by default.
- `AlarmReceiver` is no longer `@AndroidEntryPoint`. Its ordinary path touches
  no injected object; the graph is reached through a Hilt `@EntryPoint` only on
  the fallback branch.
- `AlarmEventLog.write(context, message)` — a static, DI-free write using
  `commit()`, callable as a receiver's first statement.
- The watch traces every outcome: switched off, refused, running, app swiped
  from recents, stopped. The service this replaces could fail silently and did.

No new tests: the change is entirely in the Android layer, which the domain
suite does not reach. The existing 98 domain tests should be unaffected.
`python3 tools/check-imports.py` reports 0 problems. Not compiled here — no
Android SDK in this environment.

## 2026-09-04 — 7745f1e — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 98/98 domain tests).
Uploaded updated `NESA-debug.apk` to GitHub Release `v0.1.0-stage1`.

Delivers:
- Architecture clean up according to Android AlarmClock specification.
- Reverted alarm delivery to standard `BroadcastReceiver`.
- Cleaned up and removed unneeded `NesaKeepAliveService`.
- Added Android 14+ `canUseFullScreenIntent()` verification and UI row in the reliability screen.
- Added 4 new tests for editing, cancellation, restoration, and day rolling (98 total domain tests).

The audit behind that build, in full:

- Reverted alarm delivery to a `BroadcastReceiver`. The direct
  `getForegroundService` experiment was held back by exactly the same margin, so
  the deferral is not broadcast-specific and the standard architecture is the
  better one to keep.
- Removed `NesaKeepAliveService` entirely, along with its setting, its toggle and
  its permission. No trace ever showed it running, and a permanent foreground
  service to keep an alarm alive is the wrong shape regardless.
- Added `canUseFullScreenIntent()`, the Android 14+ revocable grant that silently
  downgrades an alarm to an ordinary notification. It has its own row in the
  reliability screen.

Four new tests cover editing, cancellation, restoration from stored state, and
the roll to the next matching day. 98 domain tests pass.

The freeze itself is unchanged and unfixable from application code; see
docs/verification.md gate run 6.

## 2026-09-03 — 5bc97f4 — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 92/92 domain tests).
Uploaded updated `NESA-debug.apk` to GitHub Release `v0.1.0-stage1`.

Delivers:
- Direct foreground service start (`PendingIntent.getForegroundService`) for alarms to bypass broadcast queuing on frozen apps.
- Process lifecycle diagnostics in trace: logs "app process started" on Application startup and whether keep-alive service is genuinely running.

withheld until the app was reopened, 44s late.

Untried until now: the alarm was arriving as a **broadcast**, and Android defers
broadcasts to a frozen app. `PendingIntent.getForegroundService` starts the ringer
directly and is not subject to that queue.

Also adds the two facts the trace could not previously show — whether the process
was killed or merely frozen ("app process started"), and whether the keep-alive
service is genuinely running rather than merely switched on.

## 2026-09-03 — 2f6d8ab — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 92/92 domain tests).
Uploaded updated `NESA-debug.apk` to GitHub Release `v0.1.0-stage1`.

Delivers:
- Declared `SYSTEM_ALERT_WINDOW` permission so NESA appears in Settings → Special app access → Display over other apps.
- Added direct "Fix" button and status tile for overlay permission in the reliability screen.
- Recorded overlay permission status in the on-device alarm trace.


Device screenshots showed why NESA was missing from Settings → Special app access
→ Display over other apps: it never declared `SYSTEM_ALERT_WINDOW`, and an app
that does not declare it is absent from that list entirely. Holding it is one of
Android's documented exemptions from the ban on starting an activity from the
background, which is precisely what the ringing screen must do.

Declared, surfaced in the reliability screen with its own Fix button, and
recorded in the alarm trace so the next run says whether it was held.

New permission: `android.permission.SYSTEM_ALERT_WINDOW`. It is requested in
context and NESA draws no overlay of its own.

## 2026-09-03 — 16e690d — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 92/92 domain tests).
Uploaded updated `NESA-debug.apk` to GitHub Release `v0.1.0-stage1`.

Delivers:
- Audible volume floor: Any system alarm volume below 40% is raised automatically to guarantee audibility during alarm fade-in.
- Phone Clock App Handoff (`SystemAlarmHandoff`): Allows syncing NESA's alarm directly to the device's native stock clock app (`SET_ALARM` intent) as an emergency fallback on OEM ROMs that suppress third-party background alarms.

silence was unrelated: the alarm stream sat at 1/15 and the player's fade floor
multiplied it down to roughly 1.7% of maximum. The previous check only raised
from exactly zero.

Anything under 40% of maximum is now treated as inaudible and raised, and
`SystemAlarmHandoff` offers the wake time to the phone's own clock app for
devices where NESA's own alarm cannot be trusted.

New permission: `com.android.alarm.permission.SET_ALARM`.

## 2026-09-03 — e91cfa6 — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 92/92 domain tests).
Uploaded updated `NESA-debug.apk` to GitHub Release `v0.1.0-stage1`.

Delivers:
- Directly measures and displays alarm delivery lateness in the trace (with scheduled timestamp passed in intent).
- Keep-alive service lifecycle logging in trace.
- System alarm audio stream volume check & moderate auto-raise if muted/sitting at zero.

rather than leaving it to be worked out by hand: the scheduled time now travels
in the alarm intent, the receiver records how late delivery was, and the
keep-alive service records its own start and stop.

Also checks the system alarm stream volume when ringing and records it. A stream
sitting at zero produces silence however healthy the player is, and the trace
would still read "playing" — so this closes the one remaining way the alarm can
report success and make no sound. NESA raises it only from actual zero, and only
to a moderate level.

## 2026-09-03 — 3885da6 — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 92/92 domain tests).
Uploaded updated `NESA-debug.apk` to GitHub Release `v0.1.0-stage1`.

Delivers:
- Fixed audio player guard flag `isPlaying` so failed playback doesn't lock out subsequent attempts.
- Added on-device alarm trace (`AlarmEventLog`) displayed directly in the reliability screen under "What the alarm actually did".

**staying inside the app did not fire it either** — it only fired after leaving
the app and coming back. That rules out background freezing as the whole story.

One definite bug fixed: `AlarmAudioPlayer` set `isPlaying = true` before creating
a player, and never cleared it when every sound source failed. Once the ringer
service tried and failed, the ringing screen's attempt — the one with no
background restrictions, the one that would have worked — returned immediately.
The flag now tracks a player that genuinely exists.

The timing behaviour is not yet explained, and three rounds of reasoning from
symptoms have not settled it. So this build records what the alarm actually does
— armed, receiver fired, service started or refused, audio playing or not, screen
opened, outcome — and shows it in the reliability screen under "What the alarm
actually did". No adb required.

## 2026-09-03 — 68bb079 — SUCCESS

`./gradlew assembleDebug` and `./gradlew test` both pass, 92/92 domain tests.
APK published to GitHub Release `v0.1.0-stage1`.

Delivered: audio moved into `AlarmAudioPlayer` so the ringing screen can drive
it, the optional `NesaKeepAliveService` for ROMs that freeze background apps,
and the restored imports from the failure below.

## 2026-09-03 — e706b41 — FAILED, fixed in the commit that follows


```
AlarmRingerService.kt:133:47 Unresolved reference 'Alarm'.
AlarmRingerService.kt:136:13 Unresolved reference 'delay'.
AlarmRingerService.kt:137:26 Unresolved reference 'id'.
```

Cause: moving the audio out of `AlarmRingerService` stripped
`com.nesa.core.model.Alarm` and `kotlinx.coroutines.delay`, which the unanswered
timeout still uses. The third error was a cascade of the first.

Both imports restored. `tools/check-imports.py` added, and confirmed to
reproduce exactly these two findings when the imports are removed again.

## 2026-09-03 — 02aa068 — FAILED

`./gradlew assembleDebug` failed with Kotlin compilation errors in `AlarmRingerService.kt`:

```
> Task :core-alarm:compileReleaseKotlin FAILED
e: file:///C:/Users/Setons/.gemini/antigravity/scratch/Nesa/core-alarm/src/main/kotlin/com/nesa/core/alarm/AlarmRingerService.kt:133:47 Unresolved reference 'Alarm'.
e: file:///C:/Users/Setons/.gemini/antigravity/scratch/Nesa/core-alarm/src/main/kotlin/com/nesa/core/alarm/AlarmRingerService.kt:136:13 Unresolved reference 'delay'.
e: file:///C:/Users/Setons/.gemini/antigravity/scratch/Nesa/core-alarm/src/main/kotlin/com/nesa/core/alarm/AlarmRingerService.kt:137:26 Unresolved reference 'id'.

> Task :core-alarm:compileDebugKotlin FAILED
e: file:///C:/Users/Setons/.gemini/antigravity/scratch/Nesa/core-alarm/src/main/kotlin/com/nesa/core/alarm/AlarmRingerService.kt:133:47 Unresolved reference 'Alarm'.
e: file:///C:/Users/Setons/.gemini/antigravity/scratch/Nesa/core-alarm/src/main/kotlin/com/nesa/core/alarm/AlarmRingerService.kt:136:13 Unresolved reference 'delay'.
e: file:///C:/Users/Setons/.gemini/antigravity/scratch/Nesa/core-alarm/src/main/kotlin/com/nesa/core/alarm/AlarmRingerService.kt:137:26 Unresolved reference 'id'.
```

Missing imports in `core-alarm/.../AlarmRingerService.kt`: `com.nesa.core.model.Alarm` and `kotlinx.coroutines.delay`.


- **No sound.** The alarm channel is deliberately silent because the ringer
  *service* was meant to make the noise. On this phone the service cannot start
  from the background, so the receiver's notification fallback ran — silently, by
  design. Audio moves out of the service into a singleton `AlarmAudioPlayer` that
  the ringing screen also drives, since a foreground activity has none of the
  restrictions a background service does.
- **Frozen process.** Optional `NesaKeepAliveService`, off by default, behind a
  toggle in the reliability section. A permanent notification is the price and
  the screen says so.

`NesaKeepAliveService` is a second `specialUse` foreground service in the merged
manifest. If lint objects, report the message rather than removing it.

Both need device verification; neither is unit-testable.

## 2026-09-03 — 3f6e2d4 — SUCCESS

`./gradlew assembleDebug` and `./gradlew test` both pass, 92/92 domain tests.
APK published to GitHub Release `v0.1.0-stage1`.

Delivered the alarm arming diagnostic: `AlarmScheduler.isArmed` asks the platform
via `FLAG_NO_CREATE` whether it is still holding NESA's alarm, a "Test the alarm
in 60 seconds" button exercises the real path, and the app arms reminders on
launch rather than only alarms.

## 2026-09-03 — ff390cc — SUCCESS

Built and published to GitHub Release `v0.1.0-stage1`. Removed the committed
binary `apk/NESA-debug.apk` to keep the repository clean.

Delivered reminder delivery fixes: reminders armed the moment the plan changes
rather than only by the half-hourly worker, `setAndAllowWhileIdle` in place of
`setWindow` so they survive Doze, and the "Will the alarm actually ring?"
section with the battery-optimisation prompt NESA had never asked for.

Lint on the new permissions:

- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — no errors or complaints.
- `USE_EXACT_ALARM` — informational only, noting it requires target API 33+.
  The app module targets 35, so this is satisfied.

## 2026-09-03 — f58a997 — SUCCESS

Alarm reliability changes built and installed. Device testing found the alarm
still did not fire; see `docs/verification.md`, gate run 3.
