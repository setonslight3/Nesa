# Where Android limits what NESA can promise

Several Stage 1 requirements run into platform rules that no application can
argue with. This document records each one, what NESA does instead, and where
the code is — so that a later engineer does not rediscover them by watching an
alarm fail to ring.

## Exact alarms can be taken away

Android 12 made exact alarms a permission. Android 13 added `USE_EXACT_ALARM`
for applications whose core purpose is an alarm clock, which NESA qualifies for
and declares, with `SCHEDULE_EXACT_ALARM` retained for Android 12 and 12L.

Either can be unavailable at runtime, and can be revoked between a check and the
call that follows it.

**What NESA does.** `ExactAlarmCapability` reports the current state, and
`AlarmScheduler` uses `setAlarmClock` when exact alarms are permitted and
`setWindow` when they are not. The `SecurityException` path is handled rather
than assumed away. The alarm settings screen says plainly that the alarm may ring
a few minutes late, and links to the system screen where the user can fix it.

The alarm degrades. It never silently disappears.

## Alarms do not survive a reboot

Android drops every scheduled alarm on restart, and a timezone or clock change
invalidates the ones already set.

**What NESA does.** `BootCompletedReceiver` handles `BOOT_COMPLETED`,
`TIMEZONE_CHANGED`, `TIME_SET` and `MY_PACKAGE_REPLACED`, and re-derives every
alarm and reminder from the database. Nothing is kept in memory that would be
needed to recover.

Because some manufacturers restrict boot broadcasts, `NesaApplication` also
re-arms on every launch. It is idempotent, and an alarm that quietly stops
working is the one failure this product cannot afford.

## Foreground services must declare a type

Android 14 requires a `foregroundServiceType` on every foreground service, and
none of the standard types describes an alarm clock.

**What NESA does.** `AlarmRingerService` declares `specialUse` with an explicit
justification property, which is what the platform asks for in exactly this
situation. A Play Store submission will need that justification restated in the
console.

## A foreground service must announce itself within five seconds

`startForegroundService()` starts a service in a special state, and Android
kills the process if `startForeground()` is not called within five seconds.
Separately, Android 12 forbids starting a foreground service from a background
broadcast at all — unless the broadcast came from an *exact* alarm.

Both rules caught the first version of NESA's ringer, and the symptom was the
worst possible one: the alarm time arrived and nothing happened, silently.

**What NESA does.** `AlarmRingerService.onStartCommand` calls `startForeground`
**first, synchronously, on every path** — including the ones that only stop the
alarm, which are also started as foreground services and would otherwise crash
on their own. The notification starts with a placeholder label and is refined
once the alarm is read from the database, because waiting for that read is
exactly what blew the deadline.

`AlarmReceiver` catches a refused start (`ForegroundServiceStartNotAllowedException`
is an `IllegalStateException`, so it is caught by supertype and compiles on API
26) and falls back to posting the full-screen notification directly, which is
always permitted. The alarm still reaches the user; it just cannot play a sound
for itself.

This is why the inexact fallback matters more than it first appears: losing exact
alarms also loses the exemption that lets the ringer start at all.

## Broadcasts to a frozen app are deferred

Android holds broadcasts destined for an app it has frozen or cached, delivering
them when something else wakes the process. On a stock phone this rarely matters;
on one that freezes an app the moment it leaves the screen, it means an alarm
sent as a broadcast simply waits.

A Transsion device showed this exactly: armed for 23:34:40, delivered at
23:35:25 — the moment the user reopened the app. Every step after delivery was
already correct, so the fault was entirely in getting the alarm through the door.

**What NESA does.** `AlarmScheduler` schedules a PendingIntent that starts
`AlarmRingerService` directly, via `PendingIntent.getForegroundService`, rather
than a broadcast. A service start is not a broadcast and is not subject to that
queue. An exact alarm separately exempts the start from the usual ban on
launching a foreground service from the background, so the two mechanisms
complement each other.

The receiver's old safety net moved with it: if the service cannot become a
foreground service, it posts the full-screen notification itself before stopping.

## An app may not open a screen from the background

Since Android 10 an app cannot start an activity while it is in the background.
For most apps that is a welcome rule; for an alarm clock it is the whole feature,
because the ringing screen *is* the alarm.

The documented exemptions are few, and the one an alarm app can actually obtain
is **`SYSTEM_ALERT_WINDOW`** — "Display over other apps". An app holding it may
launch an activity from the background.

**What NESA does.** It declares the permission and asks for it from the
reliability screen. Two things follow from declaring it that are easy to miss:

- An app that does not declare `SYSTEM_ALERT_WINDOW` does not appear in
  Settings → Special app access → Display over other apps **at all**, so the user
  cannot grant it even if they go looking. NESA was invisible in that list for
  exactly this reason.
- NESA draws no overlay window of its own. It wants only the background-start
  exemption that comes with the permission.

Without it, the ringer can still post a full-screen-intent notification, but the
system decides whether to honour it — and on a phone that is already reluctant to
run the app, it usually does not.

## A device may not appear in the exact-alarm list, and that is correct

`USE_EXACT_ALARM` (Android 13+) is granted automatically to applications whose
core purpose is an alarm clock, and it is **not user-revocable**. Apps holding it
therefore do *not* appear under Settings → Special app access → Alarms &
reminders, which lists only apps holding the revocable `SCHEDULE_EXACT_ALARM`.

NESA's absence from that list is the stronger position, not a missing permission.
The reliability screen reads the capability directly, which is why it reports
exact alarms as granted while the system list does not mention NESA at all.

## Notifications can be refused

`POST_NOTIFICATIONS` is a runtime permission from Android 13, and it can be
revoked at any time.

**What NESA does.** The permission is requested in context, after onboarding,
never on the welcome screen. `NesaNotifier.enabled` is checked before every post
and the `SecurityException` path is handled anyway, because the grant can vanish
between the check and the call. If notifications are off, reminders stop being
delivered, the settings screen says so and offers the system settings, and every
other part of NESA keeps working.

## Daylight saving

Adding milliseconds to a timestamp is wrong twice a year.

**What NESA does.** `NextAlarmCalculator` works entirely in `ZonedDateTime`, so
java.time's rules resolve the transitions. On a spring-forward morning an alarm
set for a time that does not exist still fires that morning, at the next valid
instant. There are tests for both directions.

## A planning day never crosses midnight

A sleep target after midnight would otherwise mean blocks that belong to two
dates at once.

**What NESA does.** The plannable day ends at 23:59 whatever the sleep target,
and tomorrow starts fresh. `DayWindow.sleepTargetIsAfterMidnight` reports it, and
the settings screen explains it to the user. This costs a sliver of a night-owl's
evening and removes an entire class of date-rollover bugs.

## Battery optimisation

Aggressive manufacturer battery managers can delay or drop background work, and
no permission fully prevents it.

**What NESA does.** Nothing wake-critical depends on background work.
`DayPlanWorker` only replans and re-arms reminders; the alarm itself goes through
`AlarmManager`, and the timeline replans on open. If the worker never runs, the
day is replanned the moment the user looks at it.
