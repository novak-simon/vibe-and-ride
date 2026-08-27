# Vibe & Ride --- Development Roadmap & Project Management Plan

**Document purpose:** Persistent project-management context for future
ChatGPT instances and AI coding assistants.

**Project:** Vibe & Ride\
**Current platform:** Android, Kotlin Multiplatform, Compose,
Media3/ExoPlayer, Supabase/Postgres\
**Supporting project:** `vibe-and-ride-utils` Python offline
data/analysis pipeline

------------------------------------------------------------------------

# 1. Project Role Structure

The project is intentionally operated with three roles:

## Human --- Project Manager / Product Owner

The human developer makes the important product and algorithm decisions,
reviews the code directly when desired, and regularly tests the
application.

Responsibilities: - Decide product behavior and priorities. - Design and
decide recommendation algorithms. - Review implementation. - Test the
application manually. - Approve architectural changes. - Decide when a
feature is ready to move forward.

## ChatGPT --- Architect / Planner / Reviewer

ChatGPT acts as the layer between the project manager and the coding
worker.

Responsibilities: - Understand the existing architecture before
proposing changes. - Decide how the project should evolve. - Break work
into very small, independently testable tasks. - Generate prompts for
the Gemini coding assistant. - Review Gemini's reports. - Identify
regressions, architectural problems, and missing tests. - Discuss
product and technical decisions with the project manager. - Maintain
continuity across sessions using this roadmap and the project files.

ChatGPT should NOT silently make product or algorithm decisions that
belong to the project manager.

## Gemini in Android Studio --- Coding Worker

Gemini performs the actual implementation.

Every implementation prompt generated for Gemini should instruct it
to: 1. Implement only the specified task. 2. Break the task into the
smallest sensible testable fractions. 3. Inspect the relevant existing
code before changing it. 4. Avoid unrelated refactoring. 5. Add or
update tests for the implemented behavior. 6. Run the relevant
tests/build checks. 7. Report exactly what was changed. 8. Report
exactly what was tested and the results. 9. Report any uncertainty,
incomplete work, or unrelated problems discovered. 10. Stop after the
requested task rather than continuing into later roadmap items.

The project manager will paste Gemini's report back to ChatGPT. ChatGPT
then evaluates the result and decides the next task.

------------------------------------------------------------------------

# 2. Core Product

Vibe & Ride is a cycling/workout music application.

The central differentiator is:

> Music is selected according to both the rider's musical preferences
> and their workout/cadence.

The current application uses workout information and cadence-to-BPM
matching to select suitable songs.

Current music sources: - Jamendo - Audius

Potential future source: - Apple Music, dependent on validating demand
and economics.

The current application is Android-only.

------------------------------------------------------------------------

# 3. Current High-Level Architecture

## Android application

Responsibilities: - Compose UI - ViewModels - Android-specific
playback - Media3 / ExoPlayer - MediaSession - foreground/background
playback - future BLE integration

## KMP shared module

Currently contains: - models - Supabase repository/data access -
recommendation engine - workout scheduling - music orchestration -
playback abstractions

Target direction: - domain logic should become independent from Android
and Supabase implementation details. - Android-specific playback should
remain in the Android layer.

## Python utilities

`vibe-and-ride-utils` is an offline/manual data pipeline.

Responsibilities: - Jamendo importing - Audius importing - audio
downloading - BPM/beat analysis - waveform analysis - start/end trim
analysis - database administration/import

It is NOT part of the live Android application.

## Supabase

Important tables: - `songs` - `artists` - `albums` -
`jamendo_song_data` - `audius_song_data` - `workouts` - `profiles` -
`profile_song_stats`

`songs_depricated` is legacy and should not be used by current
application logic.

------------------------------------------------------------------------

# 4. Architectural Target

The project should gradually move toward this dependency direction:

    UI / ViewModels
          |
          v
    Domain / Application Logic
          |
          +----------------+
          |                |
          v                v
    Repository APIs    Platform APIs
          |                |
          v                v
      Supabase        Android Media3

The key principle is:

> Domain code decides WHAT should happen. Platform/data implementations
> decide HOW it happens.

Examples:

-   `MusicEngine` decides that a transition to song B should happen.
-   Android playback decides how to perform the transition with
    ExoPlayer.
-   `RecommendationEngine` decides which song is best.
-   A repository decides how songs/stats are retrieved.
-   `WorkoutSession` represents workout state without knowing whether
    cadence came from Zwift data or Bluetooth.

------------------------------------------------------------------------

# 5. Priority Order

The roadmap should be executed in this order unless the project manager
explicitly changes priorities.

## Phase 0 --- Project safety and baseline

Goal: Create enough test coverage and understanding that subsequent
architectural changes are safe.

Tasks: 1. Establish a reliable test structure. 2. Add baseline tests
around recommendation behavior. 3. Add baseline tests around workout
scheduling. 4. Add tests for cadence normalization/parsing. 5. Establish
a known-good build/test baseline. 6. Do not intentionally change product
behavior during this phase.

Manual app testing should happen after the baseline is established.

------------------------------------------------------------------------

# 6. Phase 1 --- Separate Music Domain Logic

Goal:

Make the music system understandable independently of Android/Media3.

Current problem: Music orchestration contains multiple responsibilities
and shares mutable state across coroutine scopes. Playback-specific
operations are exposed too deeply.

Target concepts:

    MusicEngine
    RecommendationEngine
    MusicRepository
    PlaybackController
    MusicState

`MusicEngine` should own high-level music behavior such as: - current
song - next song - play/pause/skip - reaction to workout/cadence
changes - recommendation requests - transition decisions - recording
appropriate playback events

`RecommendationEngine` should focus on scoring/selecting songs.

`PlaybackController` should expose high-level playback behavior rather
than exposing implementation details of the dual-player system.

Important: Do not attempt a large rewrite in one task.

Suggested sequence: 1. Add tests describing current MusicProvider
behavior. 2. Identify and document current state. 3. Extract pure
state/domain types. 4. Introduce `MusicEngine` incrementally. 5. Move
orchestration responsibilities one group at a time. 6. Keep existing
playback implementation working. 7. Run tests after every small
extraction. 8. Only remove old APIs once replacement behavior is
verified.

------------------------------------------------------------------------

# 7. Phase 2 --- Repository Boundaries

Goal:

Prevent domain logic from depending directly on Supabase.

Introduce interfaces such as:

    SongRepository
    WorkoutRepository
    ProfileRepository
    SongStatsRepository

Concrete implementations can remain Supabase-specific.

Desired dependency:

    RecommendationEngine
          |
          v
    SongRepository
          |
          v
    Supabase

Benefits: - easier testing - less coupling - easier database changes -
easier future offline/local caching - recommendation tests without a
real database

Suggested sequence: 1. Add interfaces around existing repository
behavior. 2. Make existing Supabase repositories implement them. 3.
Change one consumer at a time. 4. Add fake/in-memory repositories for
tests. 5. Verify behavior is unchanged. 6. Remove unnecessary direct
Supabase access from domain code.

------------------------------------------------------------------------

# 8. Phase 3 --- Playback Architecture

This is a HIGH-RISK area.

Current implementation: - two ExoPlayers - one active slot - one
preloaded slot - crossfade through volume manipulation - MediaSession
must switch to the active player - custom MediaSession commands are used
for preload/transition operations

Known problems: - crossfade timing assumptions are inconsistent. -
`AndroidAudioPlayer` contains a polling-based transition mechanism. -
higher-level music logic assumes transition completion timing. - some
preload commands exist but are not exposed through
`AndroidPlaybackController`. - MediaSession state and active-player
state must remain synchronized. - mutable playback state is spread
across several components.

Target: A clearly defined playback state machine.

Conceptually:

    IDLE
      |
      v
    PLAYING(A)
      |
      v
    PRELOADING(B)
      |
      v
    TRANSITIONING(A -> B)
      |
      v
    PLAYING(B)

Transition completion should be an explicit event/state change rather
than inferred from a hardcoded delay.

Important: - Do not rewrite the entire playback system at once. - Add
tests around the state machine before major changes. - Keep
Media3-specific code isolated to Android. - Manually test playback after
every meaningful playback change.

Manual test is required after: - changing player ownership - changing
MediaSession attachment - changing crossfade behavior - changing preload
behavior - changing service lifecycle - changing foreground/background
behavior

------------------------------------------------------------------------

# 9. Phase 4 --- Workout Domain Redesign

Goal:

Support both structured workouts and free ride without patching the
existing scheduler.

Current architecture assumes:

    Workout
      -> intervals
      -> current interval
      -> target cadence

This is suitable for structured workouts but not free ride.

Target concept:

    WorkoutMode
      |
      +-- Structured(workout)
      |
      +-- FreeRide

And a common:

    WorkoutSessionState

Possible state information: - mode - elapsed time - current cadence -
current power - current interval when applicable - session status

Critical principle:

> Music should consume a workout/cadence context, not assume that every
> ride consists of predefined intervals.

This allows:

    Zwift workout
          \
           -> WorkoutSession -> MusicEngine
          /
    BLE live cadence

Suggested sequence: 1. Add tests describing current scheduler behavior.
2. Introduce a generic workout/session state. 3. Represent structured
workouts through that state. 4. Preserve existing behavior. 5. Add
FreeRide as a mode without BLE initially. 6. Feed simulated/live cadence
into the same session abstraction. 7. Only then integrate BLE.

------------------------------------------------------------------------

# 10. Phase 5 --- BLE Integration

Goal: Read cadence from compatible smart trainers.

Initial scope: - read-only cadence. - target Wahoo trainers. - no
trainer control. - no resistance/power control initially unless
explicitly decided later.

The project should treat BLE as a data source.

Target conceptual architecture:

    BluetoothCadenceSource
            |
            v
      WorkoutSession
            |
            v
        MusicEngine

The music system should NOT directly communicate with Bluetooth APIs.

Suggested sequence: 1. Define a platform-independent cadence source
interface. 2. Create a fake cadence source for tests. 3. Implement
Android BLE discovery/connection. 4. Read cadence only. 5. Expose
cadence as a Flow/state stream. 6. Connect it to WorkoutSession. 7.
Manually test with real hardware. 8. Integrate FreeRide music behavior.

------------------------------------------------------------------------

# 11. Phase 6 --- Recommendation System

This is a HIGH-RISK product/algorithm area.

The project manager wants to design the algorithm logic personally.

Therefore: - ChatGPT should help reason about algorithms. - Gemini
should implement clearly specified algorithm changes. - No AI assistant
should silently alter recommendation formulas. - Every scoring change
requires tests before/after.

Known concern: The current skip penalty appears logically inverted. The
existing formula can cause skipping to stop reducing recommendation
score once play counts become sufficiently high.

This should NOT be "fixed" automatically.

Process: 1. Document current formula. 2. Build tests that capture
current behavior. 3. Discuss desired mathematical behavior with the
project manager. 4. Design the new formula together. 5. Add tests
describing the intended formula. 6. Implement only the agreed formula.
7. Compare before/after behavior on representative data. 8. Manually
test recommendation behavior in the app.

Also document cadence folding explicitly.

Current inconsistency: - candidate filtering uses folded algorithm
cadence. - final scoring uses raw cadence. - octave matching currently
compensates for this in practice.

This should be clarified and intentionally designed rather than
accidentally preserved.

------------------------------------------------------------------------

# 12. Phase 7 --- Workout Parsing

Known issue: The Kotlin ZWO parser currently loses: - PowerLow -
PowerHigh - CadenceLow - CadenceHigh

This causes some Warmup/Ramp/Cooldown segments to become zero-valued.

The Python parser contains related logic, creating duplicated
implementations.

Preferred approach: - establish one documented workout normalization
specification. - avoid fixing Kotlin and Python independently without
considering their shared behavior. - decide whether the Kotlin parser
should become the primary runtime implementation and Python parsing
should reuse a compatible specification.

Python parser also has an issue where optional tags can produce an
`UnboundLocalError`.

Before fixing that: - verify whether the Python workout parser is still
actively used. - if not, document it as legacy rather than spending
priority on it.

------------------------------------------------------------------------

# 13. Phase 8 --- Music Provider Integrations

## Jamendo

Currently the more established source.

Keep it working while architecture is refactored.

## Audius

Known independent problems:

1.  Kotlin stream URL retrieval uses the track metadata endpoint and
    expects a `stream.url` field. The streaming endpoint is separate.
2.  Python `audius_track_to_song()` currently leaves audio/download URLs
    as `None`, preventing analysis downloads.

Decision required: - properly fix Audius, or - deprioritize Audius and
rely mainly on Jamendo.

Do not spend major architecture time on Audius until the product
priority is clear.

------------------------------------------------------------------------

# 14. Phase 9 --- Supabase Security

HIGH-RISK.

The Android application uses a public/anon key.

RLS must be verified directly in Supabase.

Do not infer security from a schema dump.

Current policy concerns include: - temporary broad access on
`profile_song_stats` - anonymous insertion/update behavior - public
workout insertion - public reads

Before production: 1. Inspect actual RLS settings in the Supabase
dashboard. 2. Define intended access for every table. 3. Restrict
user-owned data. 4. Verify anonymous/authenticated behavior. 5. Test
policies from the client perspective. 6. Ensure no public key can modify
arbitrary users' data.

Do not weaken security for convenience without explicitly discussing it.

------------------------------------------------------------------------

# 15. Phase 10 --- Error Handling / Observability

Current code contains broad patterns such as:

    catch (e: Exception) {
        println(...)
    }

This can make production failures appear as "no music" instead of an
observable failure.

Target: - structured logging - explicit error states - useful
user-facing fallback behavior - enough diagnostics to identify
production problems

Potentially introduce: - domain error types - repository errors -
playback errors - logging abstraction

Avoid introducing paid crash-reporting services unless there is a clear
cost/benefit reason.

------------------------------------------------------------------------

# 16. Phase 11 --- Google Play MVP

Do not optimize for a perfect feature set.

MVP should prioritize: - stable workout selection - reliable music
playback - reliable recommendation behavior - basic user preference
tracking - acceptable background playback - no obvious data/security
problems

Before launch: - test fresh installation - test upgrade - test
background playback - test screen-off playback - test pause/resume -
test skip - test crossfade - test network failure - test unavailable
audio - test workout parsing - test profile/stat persistence - test
anonymous/authenticated behavior - verify RLS - verify app lifecycle

Google Play testing requirements should be verified against current
Google Play rules when launch preparation begins.

------------------------------------------------------------------------

# 17. Phase 12 --- Post-Launch Validation

After MVP release:

Add an in-app survey to validate willingness to pay approximately \$1
for premium functionality.

Potential premium feature: - Apple Music integration.

Reason for postponing: - Apple developer membership has recurring
cost. - Apple Music integration is potentially more useful than current
free sources. - There should be evidence of demand before committing to
that cost.

Do not build the premium system before validating the product need.

------------------------------------------------------------------------

# 18. Phase 13 --- iOS

Only after: 1. Android MVP exists. 2. User demand is validated. 3. Apple
Music/premium direction is sufficiently justified.

Important architectural fact:

The current Android playback system cannot simply be reused on iOS.

The following are Android/Media3-specific: - ExoPlayer -
MediaSessionService - foreground service - dual-player implementation

The shared domain logic should be reusable, but the playback
implementation must be rebuilt for iOS.

------------------------------------------------------------------------

# 19. Desktop

Desktop is currently a low priority.

`desktopApp` is only an empty stub.

Do not spend architecture effort making the Android playback
implementation reusable for desktop.

Kotlin/JVM reuse does not mean the Media3/Android playback architecture
transfers automatically.

------------------------------------------------------------------------

# 20. Testing Strategy

Testing is a structural priority.

## Unit tests

Focus on: - recommendation scoring - cadence folding - song selection -
workout state transitions - workout parsing - time adjustment -
profile/stat logic - music state transitions

## Integration tests

Where practical: - repository behavior - Supabase interactions - parsing
-\> database model - music engine -\> repository boundaries

## Manual tests

Required for: - audio playback - MediaSession - crossfade - background
playback - BLE - real trainer connection - end-to-end workout/music
behavior

The project manager should be explicitly told when manual testing is
worthwhile.

------------------------------------------------------------------------

# 21. AI Worker Task Rules

Every Gemini task should be small enough that: - the scope can be
understood in one prompt. - the changes can be reviewed quickly. - tests
can be written for the change. - failure can be isolated. - reverting
the change is easy.

Avoid prompts such as:

    "Refactor the entire music architecture."

Prefer:

    "Introduce the SongRepository interface and make the existing
    SupabaseSongRepository implement it. Do not change behavior.
    Add tests for the interface implementation. Do not modify
    RecommendationEngine yet."

Then the next task can consume the result.

Gemini should not: - redesign algorithms without approval. - refactor
unrelated files. - delete old code unless explicitly instructed. -
change database policies unless explicitly instructed. - alter playback
behavior while performing an unrelated refactor. - add dependencies
without explaining why. - claim success without running tests/build
checks.

------------------------------------------------------------------------

# 22. Risk Classification

## Very high risk

Require especially small tasks + tests + manual testing: -
recommendation scoring - Supabase RLS - dual-player playback -
MediaSession - crossfade timing - workout parsing affecting
recommendation inputs - BLE lifecycle/connection logic

## High risk

-   MusicEngine refactor
-   repository refactor
-   profile/stat behavior
-   background service lifecycle

## Medium risk

-   UI changes
-   new screens
-   non-critical database reads
-   import utilities

## Low risk

-   documentation
-   logging improvements with no behavior change
-   test-only additions
-   isolated pure helper functions

------------------------------------------------------------------------

# 23. Current Known Issues

These are not automatically the next tasks. They are tracked issues.

1.  Audius Kotlin streaming endpoint is wrong.
2.  Audius Python importer does not provide audio/download URLs.
3.  Recommendation skip penalty is likely inverted.
4.  Kotlin ZWO parser loses range-based Power/Cadence values.
5.  Python ZWO parser can crash on missing optional tags.
6.  Supabase RLS has not been fully verified.
7.  Kotlin/Python workout and cadence logic is duplicated.
8.  MusicProvider has potentially unsafe shared mutable state across
    coroutine scopes.
9.  Crossfade timing assumptions are inconsistent.
10. Some preload commands are implemented but not exposed through the
    controller.
11. Candidate cadence folding and final scoring cadence handling are
    inconsistent.
12. Test coverage is currently near zero.
13. Broad exception swallowing reduces observability.

------------------------------------------------------------------------

# 24. Immediate Roadmap

The immediate sequence should be:

### Step 1

Establish the testing baseline without changing behavior.

### Step 2

Add characterization tests for RecommendationEngine.

### Step 3

Add characterization tests for WorkoutScheduler and relevant
parsing/normalization.

### Step 4

Begin extracting repository interfaces.

### Step 5

Begin separating MusicEngine from RecommendationEngine.

### Step 6

Design and test a music-session state model.

### Step 7

Gradually isolate Android dual-player playback behind the playback
abstraction.

### Step 8

Redesign WorkoutSession to support both structured and free-ride modes.

### Step 9

Implement BLE cadence as a data source.

### Step 10

Connect BLE/free ride to the music engine.

### Step 11

Review and intentionally redesign recommendation scoring.

### Step 12

Fix/verify parsing and data pipeline issues.

### Step 13

Verify Supabase RLS.

### Step 14

Production hardening and manual testing.

### Step 15

Google Play MVP.

------------------------------------------------------------------------

# 25. Definition of Done for Architectural Changes

An architectural task is not considered complete merely because the
application compiles.

A task should normally have:

-   implementation complete
-   relevant tests added
-   tests passing
-   build passing
-   no unrelated behavior changes
-   Gemini report describing changes
-   known limitations documented
-   manual test identified if appropriate

For high-risk areas, manual application testing is part of completion.

------------------------------------------------------------------------

# 26. Decision Log Principle

Future ChatGPT instances should preserve decisions rather than
repeatedly reconsidering settled architecture.

When an important decision is made, update this document with:

    Decision:
    Date:
    Reason:
    Alternatives considered:
    Consequences:

Examples of decisions worth recording: - recommendation formula -
workout state model - playback state machine - BLE trainer support -
music provider priority - authentication model - Supabase RLS model -
premium/Apple Music strategy - platform priorities

------------------------------------------------------------------------

# 27. Current Strategic Goal

The immediate strategic goal is NOT to add as many features as possible.

It is to turn the existing MVP into a stable, testable architecture that
can safely support:

    Structured workouts
          +
    Free ride
          +
    BLE cadence
          +
    music recommendation
          +
    reliable background playback

without continuously increasing coupling between UI, recommendation
logic, workout logic, Supabase, and Android playback.

The project should evolve through small, verified changes rather than a
large rewrite.
