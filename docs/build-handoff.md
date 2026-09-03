# Build handoff

NESA is developed by two agents with different capabilities, and this file is
the contract between them. **Read it before changing anything.**

## Who does what

| | Claude (claude.ai/code) | Antigravity (on the VPS) |
| --- | --- | --- |
| Can it edit code? | Yes — full repo access | Yes, but see below |
| Can it compile? | **No** — `dl.google.com` is blocked by egress policy, so no Android SDK | Yes |
| Can it run the app? | No | Yes |
| Runs the domain tests? | Yes, on a JDK | Yes |

**The division that works: Claude writes the code, Antigravity builds it.**

Both agents *can* write code, and that is exactly the problem — two authors
editing the same files, neither seeing the other's reasoning, produces churn and
silently reverted fixes. It has already happened once: a deliberate design
decision was reversed because the reasoning behind it lived in a chat rather
than in the repository.

So, unless the user says otherwise:

### Antigravity's job

1. `git pull`
2. `./gradlew assembleDebug`
3. If it fails, **do not fix it** — paste the compiler output into
   `docs/build-log.md`, commit, push, and stop. Claude fixes it and pushes.
4. If it succeeds, publish the APK and report the result.

The exception: if the build fails on something purely mechanical that Claude
could not have known — a missing SDK component, a Gradle daemon problem, a local
environment issue — fix that and say so in the commit message.

### Claude's job

1. Write the fix, with the *reasoning* in a code comment, not just the change.
2. Run the domain tests (`:core-model:test`, `:core-scheduling:test`).
3. **Run `python3 tools/check-imports.py`** — see below.
4. Push, and say what needs verifying on the device.

### The import check

Claude cannot compile the Android modules, so a symbol used without its import
survives all the way to Antigravity's build and costs a full round trip. That
has happened: imports were stripped from `AlarmRingerService` while slimming it
and nothing caught it until the build failed.

`tools/check-imports.py` catches that specific class of mistake — a project type
or a well-known coroutines function used without its import. It is not a
compiler, it cannot type-check, and a clean run does not mean the build passes.
It is worth the two seconds it takes before every push.

Antigravity may run it too, but the real check on that side is the build.

## Why reasoning goes in comments

Neither agent can see the other's conversation. A change that looks wrong
without context will get "fixed" back. Two real examples now living in the code:

- `AlarmSettingsViewModel` creates the alarm **disabled**. That looks like a bug
  until you know it protects a user who declined the alarm during onboarding.
- `AlarmRingActivity` deliberately does **not** call `requestDismissKeyguard`.
  That looks like an omission until you know it would raise a PIN prompt between
  a half-asleep person and their alarm.

Both now carry a comment saying so. Anything similarly counter-intuitive should
get the same treatment.

## Reporting a problem

The user reports symptoms in conversation; Claude turns them into a diagnosis and
a fix. If Antigravity finds something the user has not seen — a warning, a crash
in logcat, a failing test — append it to `docs/build-log.md` rather than acting
on it, unless it blocks the build.

Useful when the alarm misbehaves:

```bash
adb logcat -c && adb logcat | grep -iE "Nesa|FATAL|ForegroundService"
```

The arming path logs every decision, so a silent failure should be readable
there.

## Standing notes

- **The APK is committed at `apk/NESA-debug.apk`.** It is how the user gets the
  build onto their phone, so it stays for now — but git keeps every version
  forever, and the repository has already grown past 39 MB from two of them. A
  GitHub Release attached to a tag would deliver the same file to a phone with
  none of that cost, and is worth switching to.
- **A stale APK is worse than no APK.** If code changes and the committed APK is
  not rebuilt, the user tests the old binary and concludes the fix failed. Always
  rebuild it in the same commit range as the fix.
- **Stage gates are real.** Stage 1 has not passed its gate; see
  `docs/verification.md`. Do not start Stage 2 work.
