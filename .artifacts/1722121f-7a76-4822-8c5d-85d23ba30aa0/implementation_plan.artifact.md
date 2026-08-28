# Implementation Plan - Phase 3: Playback State Machine

This plan introduces a formal state machine to the `MusicEngine` to manage playback transitions more reliably. This replaces the current reliance on multiple boolean flags (`isTransitioning`, `hasPreloadedSong`) with a unified `PlaybackState`.

## User Review Required

> [!IMPORTANT]
> This change refactors the core orchestration logic of music playback. While it preserves existing behavior, it significantly changes how state is tracked.

## Proposed Changes

### Music Domain (Shared)

#### [MODIFY] [MusicState.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicState.kt)
- Define `PlaybackState` enum: `IDLE`, `PLAYING`, `PRELOADING`, `TRANSITIONING`, `ERROR`.
- Replace `isTransitioning` and `hasPreloadedSong` with `playbackState: PlaybackState`.

#### [MODIFY] [MusicEngine.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicEngine.kt)
- Update `sessionStarted`, `update`, `nextTrack`, `handlePlaybackError`, and `decideTransition` to respect and update the `playbackState`.
- Ensure state transitions follow the roadmap's conceptual model: `IDLE` -> `PLAYING` -> `PRELOADING` -> `TRANSITIONING` -> `PLAYING`.

#### [MODIFY] [MusicViewModel.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicViewModel.kt)
- Update `MusicUiState` mapping to use `musicState.playbackState`.

## Verification Plan

### Automated Tests
- Update `MusicStateTest.kt` to verify state copy logic with `PlaybackState`.
- Update `MusicEngineTest.kt` to assert correct state transitions during session start, preload, and crossfade.
- Run existing characterization tests to ensure no regressions in recommendation buffer management.

### Manual Verification
- Deploy to Android and verify that track transitions and crossfades still function as expected.
- Verify that UI updates correctly reflect the playing/transitioning status.
