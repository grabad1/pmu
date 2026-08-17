# Focus Guard — Agent Context

Personal Android project. Not related to any other repo, internship, or work project.

## What this app is

A **focus/productivity Android app**. The user starts a timed focus session; the app
watches the environment through phone sensors, warns when conditions hurt focus, and
afterwards has OpenAI rate the session.

Core concepts:

- **Session** — a focus block with a *goal time* in minutes and a name.
- **Planned pauses** — the user picks a pause count `n` and a pause duration.
  Pauses are spaced evenly at `goalSeconds / (n + 1)`.
  *Decided deliberately:* with `goal=60, n=6` this is a pause every ~8.5 min, not every
  10 min. Dividing by `n+1` avoids scheduling a pause exactly at the finish line.
- **Unplanned pauses** — the user can hit Pause at any time. These are tracked
  separately and **should reduce the AI score**, since they represent lost focus.
- **Overtime** — passing the goal without stopping is a *good* outcome. UI turns blue
  and glows; the session keeps running.
- **Scheduled sessions** — created for a future date/time, with conflict detection
  against existing scheduled sessions. Notifications fire 1 h before, 5 min before,
  and at start.
- **AI rating** — on session end, OpenAI produces a 0-100 score, a one-line comment,
  and a paragraph of analysis. Inputs: goal vs actual duration, planned vs unplanned
  pause counts/durations, and sensor history (time spent in darkness, noise, motion).

### Sensor warnings

| Warning | Sensor | Severity |
|---|---|---|
| Phone being moved | `TYPE_LINEAR_ACCELERATION` | **Big** full-screen "Stop Using The Phone!" |
| Bad lighting | `TYPE_LIGHT` (lux) | Toast |
| Loud room | microphone RMS → dB | Toast |

"Focus drop" and "No activity" appear in the prototype but are **deferred** — decide
later what actually drives them.

Leaving the app during a session is a known future concern; not designed yet.

## Source of truth for the design

`index.html` in the repo root is a **clickable prototype** of the whole app (single
file, dark theme, orange accent). It defines the intended screens, colours, copy and
interactions. Treat it as the design spec. It is *not* shipped in the APK.

## Stack

Deliberately matched to the user's university coursework (`RunningApplication-Lekcija06`)
so the project also opens on university machines.

| | Version / choice |
|---|---|
| Language | Kotlin 2.2.21 |
| UI | Jetpack Compose (BOM 2025.10.01), Material 3 |
| Build | AGP 8.13.0, Gradle 8.13, version catalog in `gradle/libs.versions.toml` |
| **JDK for Gradle** | **21 (Temurin LTS)** — see warning below |
| Java level | 17 (`compileOptions` / `jvmTarget`) |
| min / target / compile SDK | 26 / 36 / 36 |
| DI | Hilt 2.57.2 (KSP, never kapt) |
| Database | Room 2.8.3 |
| Navigation | Navigation Compose 2.9.5, **type-safe `@Serializable` routes** |
| HTTP | Retrofit 3.0.0 + Gson + OkHttp logging interceptor |
| Async | Coroutines + Flow |
| Alarms | `AlarmManager.setExactAndAllowWhileIdle` (**not** WorkManager — WorkManager is
inexact and unusable for a "5 minutes before" reminder) |
| Dates | `java.time`, stored in Room as epoch-millis `Long` |

### ⚠ JDK: use 21, not the Studio-bundled JDK

Android Studio 2026.1 bundles **JBR 25**. AGP 8.13 cannot parse that version and fails
with a bare `* What went wrong: 25.0.2`. JDK 25 needs Gradle 9.1+ / AGP 9.x, which would
break university-PC compatibility for zero benefit (Android can't use Java 25 language
features anyway).

Build from the CLI with:

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jbr-21.0.11"
.\gradlew.bat :app:assembleDebug
```

That is the JDK 21 Android Studio downloaded for itself. Using the same one from the
CLI means Studio and the agent share a single Gradle daemon instead of running two
(~2 GB each).

In Android Studio the JDK is already set (**Settings → Build, Execution, Deployment →
Build Tools → Gradle → Gradle JDK**). Studio records it in `.gradle/config.properties`
as `java.home`, which is git-ignored and machine-local — the CLI wrapper does *not*
read that file, hence the explicit `JAVA_HOME` above.

**Decline every Android Studio prompt to upgrade AGP/Gradle.** Accepting on one machine
is the usual way a project stops opening on the other.

## Conventions

Mirrors the university project's structure, which is what the user is graded against.

```
rs.etf.focusguard
├── FocusGuardApplication.kt      @HiltAndroidApp, holds LOG_TAG
├── FocusGuardActivity.kt         single activity, @AndroidEntryPoint
├── FocusGuardDestination.kt      @Serializable nav destinations
├── data/
│   ├── room/                     entities, DAOs, database, type converters
│   ├── retrofit/                 OpenAI API models + interface
│   └── *Repository.kt            @Singleton, injected
├── hilt/                         @Module @InstallIn(SingletonComponent::class)
├── ui/
│   ├── elements/
│   │   ├── screens/              one file per screen
│   │   ├── composables/          shared widgets
│   │   └── theme/                Color / Type / Shape / Theme
│   └── stateholders/             ViewModels live HERE (not next to screens)
└── util/
```

Patterns to follow:

- ViewModels: `@HiltViewModel class XViewModel @Inject constructor(savedStateHandle, repo)`
- UI state: `@Parcelize data class XUiState(...) : Parcelable`, held via
  `savedStateHandle.getStateFlow(UI_STATE_KEY, XUiState())`, updated with `.copy()`
- DAOs: `Flow<List<T>>` for reads, `suspend` for writes
- Repositories: `@Singleton`, `@Inject constructor`, expose DAO flows as `val`
- Services: `LifecycleService` + `@AndroidEntryPoint`; sensors are separate
  `DefaultLifecycleObserver` classes attached via `lifecycle.addObserver(...)`
- Dark theme only — no light variant, no dynamic colour

## Secrets

The OpenAI key lives in `local.properties` as `OPENAI_API_KEY=...` and is surfaced
through `BuildConfig.OPENAI_API_KEY`. `local.properties` is git-ignored.

**The GitHub repo is public — never commit the key, and never paste it into chat.**

## Environment (this machine)

- Repo: `D:\PMU\pmu` → `github.com/grabad1/pmu`. Run the agent from the repo root.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (platform 36, build-tools 36.0.0)
- JDK 21: `%USERPROFILE%\.jdks\jbr-21.0.11` (downloaded by Studio; used by both
  Studio and the CLI). A second Temurin 21 sits at `%LOCALAPPDATA%\Programs\jdk-21.0.12+8`
  as a fallback and is otherwise unused.
- Emulator AVD: **`FocusGuard_API36`** (Pixel 7, API 36, x86_64, google_apis)

### Agent testing loop

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n rs.etf.focusguard/.FocusGuardActivity
& $adb exec-out screencap -p > shot.png        # screenshots ARE readable
```

Sensor injection works on the emulator — use it to exercise the warning engine:

```powershell
& $adb emu sensor set light 3            # dark room  -> bad-lighting toast
& $adb emu sensor set light 400          # normal
& $adb emu sensor set acceleration 4:9:2 # movement   -> big warning
```

Screen is **1080×2400**. Screenshots are downscaled when viewed, so scale tap
coordinates back up before `adb shell input tap`.

**The microphone cannot be injected on the emulator.** Keep loudness detection behind
an interface, fake it in unit tests, and have the user verify on a real device.

### Seeding demo data

The emulator ships `sqlite3`, so sample sessions can be inserted straight into the live
database without adding seed code to the app:

```powershell
$adb shell "run-as rs.etf.focusguard sqlite3 databases/focus_guard_database 'SELECT id,name FROM sessions;'"
```

Write multi-statement SQL to a file, `adb push` it to `/data/local/tmp`, then
`run-as ... sh -c 'cat <file> | sqlite3 databases/focus_guard_database'`. Quoting nested
SQL inline through PowerShell and `adb shell` is unreliable; the file route is not.

Note the emulator's IME may interrupt `input text` with a stylus tutorial. Dismiss it
once with `settings put secure stylus_handwriting_enabled 0`.

## Data model

Three tables, all in `data/room`:

- **`sessions`** — one row per session, scheduled or run. `focusedSeconds` counts *focus*
  time only (pauses excluded) so it compares directly against `goalMinutes`, and is allowed
  to exceed it because overtime is a success state. AI results (`focusScore`, `aiComment`,
  `aiAnalysis`) are null until Phase 6 fills them in.
- **`pauses`** — `PLANNED` or `UNPLANNED`, cascade-deleted with the session.
  `startOffsetSeconds` is the *focus* offset at which the pause began, which is what the
  pause log displays; it is not derivable from `startedAt`, since earlier pauses shift
  wall-clock time away from focus time.
- **`sensor_samples`** — raw periodic readings (lux / dB / m·s⁻²). Raw values are stored
  rather than verdicts so thresholds can be retuned later without invalidating history.

Conflict detection lives in SQL (`SessionDao.findScheduledOverlapping`): a scheduled
session occupies `scheduledAt .. scheduledAt + (goal + count × pauseMinutes)` minutes.
Adjacent sessions that merely touch do not count as overlapping.

Planned pause offsets come from `util/plannedPauseOffsetsSeconds`, unit-tested in
`PauseScheduleTest`.

## Testing

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jbr-21.0.11"
.\gradlew.bat :app:testDebugUnitTest          # pure logic, no device
.\gradlew.bat :app:connectedDebugAndroidTest  # Room tests, needs the emulator running
```

## Session engine

`SessionEngine` is a `@Singleton` that owns the clock and the rules of a running session.
`FocusSessionService` only keeps the process alive and renders the notification, so the
session is unaffected if the service is restarted.

Key decisions, each of which fixed a real bug during Phase 3:

- **Time is derived from timestamps, never counted.** A tick only triggers recomputation,
  so a delayed or coalesced tick shows the correct time instead of silently drifting.
- **`attach(sessionId)` is the single, idempotent entry point.** Starting a new session and
  recovering a killed one are the same code path: state is always rebuilt from stored rows,
  and a brand-new session simply reconstructs to zero. Attaching to the session already
  running is a no-op. An earlier design had separate `start`/`restore` paths that raced.
- **Only the service and `FocusGuardApplication` may attach.** The UI must not, or reopening
  the app races with the service.
- **The service must not stop on the first null state.** The engine has no state until the
  session is loaded, so treating "state is null" as "session finished" killed the service
  immediately — the timer appeared to work only because the Activity kept the process alive.
  It now stops only after it has seen a real session.
- Focus progress is written to Room once a minute, so a process death costs seconds.
- Reopening the app while a session runs navigates straight to the timer.

Foreground service type is `specialUse` (there is no timer type, and `shortService` caps at
a few minutes). Phase 4 should add `microphone` once loudness monitoring lands.

## Environment monitoring

`EnvironmentMonitor` turns readings into warnings; the sensors themselves are lifecycle-aware
observers attached to `FocusSessionService`, so they are unregistered when the session ends.

- **Conditions are judged on a 1 s timer, not per reading.** `TYPE_LIGHT` is an *on-change*
  sensor: once the room is dark it reports once and goes quiet, so a sustain window driven by
  readings alone never elapses. Readings only update "latest"; the timer decides.
- **Non-finite readings are dropped.** SQLite has no NaN — Android binds it as NULL, which
  violates the non-null `value` column and crashes the insert. A physically impossible sensor
  injection (`acceleration 0:0:0`) produced NaN through sensor fusion and found this.
- **Nothing is judged while paused.** Using the phone during a break is expected.
- Thresholds and sustain/cooldown windows live in `EnvironmentThresholds`, and the debounce
  rules are in `ConditionTracker`, which is clock-free and unit-tested.
- Movement is the only full-screen interruption; light and noise are toasts.
- **Rotation counts as movement.** A phone turned smoothly in the hand barely accelerates, so
  the gyroscope catches handling that linear acceleration misses. Both feed one warning and
  share its cooldown, since they are two symptoms of the same thing.
- **Light warns only on darkness** (`lux < DARK_LUX`), never on brightness.

Current thresholds: dark below 15 lux for 20 s, loud above 70 dB for 15 s, movement above
2.5 m/s² or rotation above 1.0 rad/s with **no** sustain window, each with a 120 s cooldown.
Samples are stored every 10 s.

Movement is treated as an instantaneous event on purpose. Picking up a phone is a burst of a
few hundred milliseconds, so a sustain window meant it effectively never fired, and a
once-a-second look at the *latest* value stepped straight over the spike. Spiky signals
(motion, noise) are therefore evaluated on the **peak since the last tick**; ambient light,
which is a floor rather than a ceiling, still uses the latest reading.

Note for testing: dragging the accelerometer in the emulator's Extended Controls only
*rotates* the device. Gravity's direction changes but its magnitude does not, so linear
acceleration stays near zero. Inject values instead:

```powershell
adb emu sensor set acceleration 1:16:4   # then back to 0:9.8:0 — a pick-up
adb emu sensor set gyroscope 0:0:2       # then back to 0:0:0   — a turn
adb emu sensor set light 2               # darkness; there is no light control in the UI
```

The Extended Controls panel has no ambient-light slider at all, which is why the light
warning appears not to work when tested by hand.

**Noise is unverified.** The emulator's microphone only ever returns silence, so
`MicrophoneNoiseSource` has never produced a non-zero reading. It sits behind the `NoiseSource`
interface for that reason. The dB figure is relative, not calibrated SPL, so the 70 dB
threshold will need tuning against a real device.

## Build phases

| # | Phase | State |
|---|---|---|
| 0 | Scaffold: Gradle, theme, nav skeleton | ✅ done |
| 1 | Room entities, DAOs, repository | ✅ done |
| 2 | Real screens: forms, lists, modals, conflict detection | ✅ done |
| 3 | Foreground service: timer, pauses, overtime | ✅ done |
| 4 | Sensors → warning engine | ✅ done |
| 5 | AlarmManager scheduling + notifications | |
| 6 | OpenAI rating | |
| 7 | Landscape, animations, polish | |

## Known issues

- **Landscape on the running-session screen.** The dial is a fixed 224 dp, so in landscape
  there is not enough height for header + dial + pause readout + buttons. `SpaceBetween`
  squeezes the buttons to a few pixels and clips their labels. The prototype solves this by
  shrinking the circle to 148 px and tightening the gaps in landscape; do the same, driven
  by `WindowSizeClass` or the available height. Deferred to Phase 7. Rotation itself is
  safe — the session keeps running, since the engine is a singleton.

Phases 0-3 produce a genuinely usable focus timer; everything after is additive.

## Working agreement

- Work phase by phase; commit at the end of each phase.
- The user is new to Kotlin — explain non-obvious choices, keep code readable.
- Prefer correct solutions over curriculum-only ones, but say so when deviating.
