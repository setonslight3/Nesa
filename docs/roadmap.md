# Stages 3–5: what is left, and what each needs

Stage 1 is built (one open defect, see `verification.md`). Stage 2 is built and
**green**: `c532b1c` compiled and passed 127/127 domain tests, and Room accepted
the hand-written `3 → 4` migration — `schemas/…/4.json` is the proof.

This document exists so the remaining work does not have to be re-derived from a
conversation nobody else can see. It is written for whoever picks this up next,
which may be a different assistant entirely.

## Where the specification actually lives

Stages 1 and 2 were built from three `.docx` files the user supplied: Build
Instructions, Product Specification, Technical Blueprint. **Those documents are
not in this repository.** Everything below is inferred from `CLAUDE.md`'s stage
list and from the product rules already encoded here.

Anyone continuing this should ask the user for those documents before building
Stage 4 or 5. The cost of guessing at a whole module is a rewrite; the cost of
asking is one message.

## Stage 3 — Personalization, focus, learning

### Built

- **`AdaptiveInsights`** (`core-scheduling`) — counts completion outcomes per
  part of the user's own day and reports them. Deliberately the least clever
  thing that works: no model, no weights, no decay curve. It must be able to say
  "I do not know" (`TimeBandInsight.isTrustworthy`), it never acts on its own,
  and it counts a skip apart from a miss because a decision is not a failure.
- **`CompletionRecord.scheduledStartMinute`** (schema 4 → 5) — the slot a record
  is about, as distinct from when NESA found out. Nullable, and left null for
  older rows: back-filling a guess would teach the learner something untrue.
- **The weak-band notice** in the activity editor — a warning beside the time
  picker when history says that part of the day does not survive. A warning,
  never a move.

### Not built

- **Focus / screen-time.** Needs `PACKAGE_USAGE_STATS` (a special-access
  permission granted in system settings, like the overlay one) and
  `UsageStatsManager`. Fully offline, no keys. Note that the test device already
  fights background work hard — see the UNRESOLVED section in `verification.md`
  — so usage sampling should be pull-on-open, not a background poll.
- **Learning.** Study topics with a spaced-repetition interval. Pure domain
  arithmetic, an obvious fit for `core-scheduling`, and it produces ordinary
  `Activity` rows the way fitness does.

## Stage 4 — AI and voice

**Blocked on a decision only the user can make.** Two of the build
instructions' non-negotiables bear directly on it:

> Do not hard-code secrets or API keys.
> Do not give AI direct database or arbitrary code execution access.

### The shape that is already decided

`CLAUDE.md` records it: **AI proposes intents; the existing use-case layer
decides.** A model never touches Room and never runs code. It emits a typed
intent, an intent validator maps it onto `ActivityActionHandler`,
`NesaAlarmCoordinator` and the rest, and anything unrecognised is refused. That
is the same single-source-of-truth rule that already keeps a notification tap and
a timeline tap from drifting apart, and it is what makes an AI layer safe here.

### What the user has to decide

1. **Which model, and where the key lives.** A key must never be committed. The
   safe default, if nobody says otherwise: the user pastes their own key into
   settings, it is stored in `EncryptedSharedPreferences`, and the app works
   fully without one.
2. **Whether there is a server.** The user has asked about a hosted version
   before. A server changes this from "bring your own key" to "NESA has an
   account", which is a different product and a different privacy story.

### What can be built with neither

A `LocalIntentParser` — rule-based, offline, no key — over the same intent
types, plus voice input through Android's `SpeechRecognizer`. "Move gym to six"
does not need a language model. This is worth building first regardless, because
it is what proves the intent layer is safe before anything clever is wired to it.

## Stage 5 — Sync, widgets, theme engine

- **Theme engine.** Already hooked: `core-ui` reads every colour through one
  theme and `ThemeMode` exists. Extending it to accents or palettes touches one
  module. Offline, no decisions needed.
- **Widgets.** A Glance app widget showing the next block. Needs the
  `androidx.glance` dependency, which must be justified in
  `docs/dependencies.md` per the project's own rule.
- **Cloud sync.** **Blocked.** There is no backend and inventing one is not a
  code decision — it is an account, a cost and a privacy commitment. What *can*
  be built without any of that is a local encrypted export/import: a real backup,
  and the same serialisation a sync layer would need later.

## The order to build in, and why

1. **One schema change per build.** Migrations `1→2` through `3→4` are validated
   — `c532b1c` built green and Room exported `4.json`. `4→5` is not yet. Room
   checks a migrated schema against its own and throws on any difference, so
   stacking several unverified schema changes turns one clear error into a
   cascade. Each stage below that adds tables should get a build of its own
   before the next one lands.
2. Stage 3's focus and learning modules — unblocked, no decisions needed.
3. Stage 4's intent layer and voice — unblocked, and it is the safety boundary
   the model work depends on.
4. Stage 5's theme engine, then widgets.
5. The AI client and sync, once the user has decided the two questions above.
