# Build log

Append-only. Newest entry at the top. Antigravity records build results here;
Claude reads it before making the next change.

Format: date, commit built, outcome, and the exact output if it failed.

---

## 2026-09-03 — ff390cc — SUCCESS

Built and packaged successfully (`./gradlew assembleDebug` and `./gradlew test` passing 92/92 domain tests).
Published APK to GitHub Release `v0.1.0-stage1`. Removed committed binary `apk/NESA-debug.apk` to keep the repo clean.

Lint report regarding permissions:
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Lint had NO errors or complaints.
- `USE_EXACT_ALARM`: Lint reported an informational check on `core-alarm/src/main/AndroidManifest.xml` noting it requires target API 33+ (the app module targets API 35).


- Reminders are now armed the moment the plan changes, not only by the
  half-hourly worker.
- Reminders use `setAndAllowWhileIdle` instead of `setWindow`, so they survive
  Doze.
- Settings gains a "Will the alarm actually ring?" section, including the
  battery-optimisation prompt NESA never asked for.

New permission in the merged manifest:
`android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. If lint objects to it,
report the message rather than removing it — it is deliberate and an alarm clock
is one of its permitted uses.

## 2026-09-03 — f58a997 — SUCCESS

Alarm reliability changes built and installed. Device testing found the alarm
still did not fire; see `docs/verification.md`, gate run 3.
