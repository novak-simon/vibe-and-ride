# Fix: Repeated Recommendations and Playback Issues

Address the bug where `sessionStarted` is called repeatedly, resetting the music engine state and causing infinite recommendation loops.

## User Review Required

> [!IMPORTANT]
> The root cause was that `MusicViewModel` was calling `musicEngine.sessionStarted()` every time the workout state updated (every 200ms) while in `READY` status. This cleared the music buffer and triggered new recommendations constantly.

## Proposed Changes

### Music Engine

#### [MODIFY] [MusicEngine.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicEngine.kt)
- Update `sessionStarted` to check if the session is already initialized for the given workout/mode and return early if so.
- This provides a robust safeguard against redundant initialization.

### Music ViewModel

#### [MODIFY] [MusicViewModel.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicViewModel.kt)
- Refine the observation of `workoutViewModel.uiState`.
- Use `distinctUntilChanged` more effectively to avoid processing the same state repeatedly.
- Ensure `sessionStarted` is only called when the workout or mode actually changes.

## Verification Plan

### Automated Tests
- Run existing `MusicEngineTest` and `MusicEngineCharacterizationTest`.
- Add a test case to `MusicEngineCharacterizationTest` that calls `sessionStarted` twice with the same state and verifies that the buffer is NOT cleared the second time.

### Manual Verification
- Check Logcat for "Recommending songs..." spam. It should now only appear once per workout selection.
- Verify that music actually starts playing when "Start" is pressed.
- Verify that "isMusicAvailable" remains true once the first recommendation is finished.
