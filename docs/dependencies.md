# Dependencies

Every dependency is a long-term commitment, so each one here has a reason and a
pinned version. Versions live in `gradle/libs.versions.toml`.

| Dependency | Why it is here | Alternative rejected because |
| --- | --- | --- |
| Kotlin 2.0.21 | The language. Brings the standalone Compose compiler plugin, which removes the old compiler-extension version matrix. | — |
| Coroutines + Flow | Reactive local state. The one dependency the domain modules take, purely for `Flow` in the repository contracts. | RxJava is a heavier dependency and not idiomatic in modern Android. |
| Jetpack Compose (BOM 2024.10.01) + Material 3 | Declarative UI, and Material 3's colour scheme is what makes light/dark a palette swap rather than two designs. | Views would mean XML layouts and a second styling system. |
| Navigation Compose | Lets each feature own its route and register itself, so `:app` composes screens without knowing their contents. | Hand-rolled navigation would not survive process death. |
| Lifecycle (runtime, viewmodel, compose) | `collectAsStateWithLifecycle` stops collection when the screen is not visible. Relevant when the alternative is a scheduler re-running in the background. | — |
| Room 2.6.1 | Structured offline persistence with compile-time-checked SQL and `Flow` queries. Schemas are exported for reviewable migrations. | Raw SQLite means hand-written mapping and no query verification. |
| DataStore 1.1.1 | Typed scalar preferences with a `Flow` API and no `apply()`-on-the-main-thread problem. | SharedPreferences has no reactive read and a blocking first read. |
| WorkManager 2.9.1 | Deferrable background replanning. Noticing a missed activity half an hour late costs nothing; an exact alarm every half hour would cost battery and the user's exact-alarm allowance. | — |
| Hilt 2.52 | Compile-time dependency injection. Broadcast receivers, services and workers all need injection, and Hilt is the only option that handles all three. | Manual injection would mean a service locator reachable from a `BroadcastReceiver`. |
| androidx.hilt (work, navigation-compose) | `@HiltWorker` and `hiltViewModel()`. | — |
| KSP 2.0.21-1.0.28 | Annotation processing for Room and Hilt. Materially faster than kapt and the supported path for both. | kapt is in maintenance. |
| AppCompat | Pulled in for resource compatibility only. | — |
| Material icons (extended) | The handful of icons the interface uses. | Shipping hand-drawn vectors for standard glyphs is worse, not lighter. |
| JUnit 4 + coroutines-test | The test framework. JUnit 4 because AndroidX test still targets it. | — |

## Deliberately absent

- **No network library.** Stage 1 makes no network calls at all. Stage 4 will add
  one behind the `AIProvider` abstraction.
- **No image loading library.** Nothing loads a remote image yet.
- **No serialization library.** `ChangeReason` is persisted through a small,
  tested codec; adding a serialization framework for one sealed type would be
  more machinery than the problem needs.
- **No analytics, crash reporting, or advertising SDK.** Personal data stays on
  the device, and nothing here contradicts that.

## Native code and ABIs

NESA has no native code of its own. `abiFilters` is nonetheless pinned to
`armeabi-v7a` and `arm64-v8a`, which documents what must keep working if a
dependency ever brings native libraries with it, per the blueprint's Android
compatibility requirement.

## Adding one

Justify it in this table, pin the version in the catalogue, and check that it
does not pull Android into `core-model` or `core-scheduling` — those two modules
staying framework-free is what makes the scheduler testable without an emulator.
