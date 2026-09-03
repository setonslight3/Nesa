# Build log

Append-only. Newest entry at the top. Antigravity records build results here;
Claude reads it before making the next change.

Format: date, commit built, outcome, and the exact output if it failed.

---

## Pending — alarm audio, and a keep-alive for phones that freeze apps

Device: Infinix Smart 9. The user established that the alarm fires only while
NESA is on screen, and that when it does surface it is a silent notification and
popup. Both are now explained and addressed:

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
