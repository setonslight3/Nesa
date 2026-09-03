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
