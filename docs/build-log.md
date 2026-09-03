# Build log

Append-only. Newest entry at the top. Antigravity records build results here;
Claude reads it before making the next change.

Format: date, commit built, outcome, and the exact output if it failed.

---

## Pending — reminder delivery and background reliability

Needs building and installing. Three changes, all from a device report that
"nothing fires until I open the app":

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
