# Build log

Append-only. Newest entry at the top. Antigravity records build results here;
Claude reads it before making the next change.

Format: date, commit built, outcome, and the exact output if it failed.

---

## Pending — lateness measurement and keep-alive lifecycle

The trace from the previous build showed NESA's chain working end to end and the
platform delivering the alarm 3m04s late. This build measures that directly
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
