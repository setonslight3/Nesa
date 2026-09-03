# NESA

An adaptive personal assistant for Android. NESA plans a practical day with you,
protects the commitments that cannot move, and helps you recover the ones that
slip — without turning productivity into punishment.

**Status: Stage 1 (Core) is implemented.** Stages 2–5 have not been started.

---

## What Stage 1 does

- **Onboarding** in three short, skippable steps. No module is configured here.
- **A timeline** for any day, grouped into morning, day, evening and night, with
  the single next thing to do highlighted.
- **Activities** with a priority, a duration, and a flexibility that tells NESA
  whether it may move them.
- **An adaptive scheduler** that protects fixed anchors, never plans into the
  past or past your sleep target, preserves deadlines, uses the evening as a
  recovery window, and explains every meaningful change it makes.
- **Seven activity states**, in which a deliberate skip and an unanswered miss
  are deliberately different things.
- **A smart alarm** with a wake challenge, a snooze policy, and a bounded retry
  when nobody answers — all of it working with no network.
- **Reminders** carrying done / do later / skip, so answering NESA never
  requires opening the app.
- **Light and dark** variants of one green theme, following the system by
  default.

Everything is stored on the device. Nothing is sent anywhere, and there is no
account.

## What Stage 1 deliberately does not do

Fitness, learning, screen-time and focus, AI, voice, cloud sync, widgets and the
theme engine all belong to later stages. They are not stubbed out or half-built;
the architectural seams they will need are in place, and nothing else.

---

## Building

Requires the Android SDK (compileSdk 35) and JDK 17 or newer.

```bash
./gradlew assembleDebug        # build the app
./gradlew test                 # unit tests
./gradlew connectedAndroidTest # instrumented tests, needs a device or emulator
```

The two domain modules carry no Android dependency, so they can also be built
and tested with nothing but a JDK — see [docs/verification.md](docs/verification.md).

`minSdk` is 26 (Android 8.0). That choice buys `java.time` without desugaring,
which the scheduler uses throughout.

### Signing

No keystore or signing configuration is committed. Supply one locally before
building a release; `local.properties`, `*.jks` and `keystore.properties` are
git-ignored.

---

## Documentation

| Document | What is in it |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | Module graph, layer rules, and the decisions behind them |
| [docs/scheduling.md](docs/scheduling.md) | The scheduling engine's rules, in the order it applies them |
| [docs/dependencies.md](docs/dependencies.md) | Every dependency and why it is there |
| [docs/android-platform.md](docs/android-platform.md) | Where Android limits what NESA can promise, and what it does instead |
| [docs/verification.md](docs/verification.md) | The Stage 1 checklist, and exactly what has and has not been verified |

Read `docs/verification.md` before trusting anything in this list: parts of the
build have not been compiled, and that document says precisely which.
