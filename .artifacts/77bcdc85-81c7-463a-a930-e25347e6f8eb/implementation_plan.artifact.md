# Music Domain Logic Extraction (Phase 1)

This plan covers the extraction of Preload, Transition, and Precomputation policies from `MusicProvider` into `MusicEngine`. This aligns with Phase 1 of the development roadmap, focusing on making the music system understandable independently of platform-specific infrastructure.

## User Review Required

> [!IMPORTANT]
> This refactor aims to be behavior-neutral. However, the `MusicProvider` currently has some failing tests in its characterization suite related to precomputation. I will maintain the existing logic as closely as possible, but moving it to pure functions in `MusicEngine` will make these behaviors easier to test and debug in isolation.

## Proposed Changes

The changes are grouped into the incremental extraction of three main policies.

### Music Domain

#### [MODIFY] [MusicState.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicState.kt)
* No changes expected to the data structure itself in this step, as it already contains the necessary fields identified in the audit.

#### [MODIFY] [MusicEngine.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicEngine.kt)
* Implement `decidePreload`: Pure logic to determine if a song should be preloaded and which one.
* Implement `decideTransition`: Pure logic to determine if a track transition (next track/interval) should occur.
* Implement `decidePrecompute`: Pure logic to determine if a recommendation precomputation is needed.
* Implement `nextStateAfterTransition`: Pure logic to calculate the next `MusicState` after a transition occurs.

---

### Music Infrastructure

#### [MODIFY] [MusicProvider.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicProvider.kt)
* Refactor `play()` to delegate decision-making to `MusicEngine`.
* Refactor `nextTrack()` to use `MusicEngine` for state transitions.
* Refactor `precomputeNextRecommendation()` to delegate the "when" decision to `MusicEngine`.

## Verification Plan

### Automated Tests
* Update `MusicEngineTest.kt` with comprehensive tests for the new policy methods.
* Run `:shared:allTests` to ensure no regressions in existing characterization tests.
* I will attempt to fix the `ArrayIndexOutOfBoundsException` in the `FakeSongRepository` if it blocks verification of the new logic, as it's a "known issue" identified in the roadmap.

### Manual Verification
* The user will eventually need to verify playback behavior in the app, but this plan focuses on the logical extraction which can be verified via unit tests.
