# Implementation Plan - Wait for Music Ready before Workout Start

Ensure that a workout can only be started once the first song is fully loaded and prepared by the `MusicEngine`.

## User Review Required

> [!NOTE]
> This change will keep the "START" button disabled (or in its previous state) until the first song is ready. Users might experience a short delay after selecting a workout before they can start.

## Proposed Changes

### Music Component

#### [MODIFY] [MusicUiState.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicUiState.kt)
- Add `isReady: Boolean` field to `MusicUiState`.

#### [MODIFY] [MusicViewModel.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicViewModel.kt)
- Update the mapping from `MusicState` to `MusicUiState` to set `isReady`.
- In `initWorkoutViewModel`, ensure `prepareFirstTrack` is triggered as soon as a recommendation is available, even if the workout status is `NOT_STARTED`.

### Workout Component

#### [MODIFY] [WorkoutViewModel.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/workout/WorkoutViewModel.kt)
- In `initMusicViewModel`, change the observation from `isMusicAvailable` to `isReady`.
- This ensures `scheduler.musicPrepared()` (which transitions status to `READY`) only happens once the song is actually loaded.

## Verification Plan

### Automated Tests
- Run `WorkoutViewModelTest.kt` to ensure basic flow is still correct.
- Create a new test case in `MusicViewModelTest.kt` (if it exists) or a characterization test to verify that `isReady` becomes true only after `prepareFirstTrack` completes.

### Manual Verification
1. Select a workout.
2. Observe that the "START" button remains disabled (or doesn't appear) for a moment.
3. Observe that once the song title appears/music is ready, the "START" button becomes enabled.
4. Click "START" and verify the music starts playing immediately without delay.
