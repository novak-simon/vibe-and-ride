# Synchronize Music UI Playback State

Synchronize the pause/resume button in the workout UI with the actual music playback state, ensuring it updates even when changes occur externally (e.g., via notification or headphone controls).

## Proposed Changes

### [Music Library]

#### [MODIFY] [PlaybackController.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/PlaybackController.kt)
- Add `val isPlayingFlow: Flow<Boolean>` to the `PlaybackController` interface to expose real-time playback state.

#### [MODIFY] [AndroidPlaybackController.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/player/AndroidPlaybackController.kt)
- Implement `isPlayingFlow` using a `MutableStateFlow`.
- Register a `MediaController.Listener` to the `MediaController`.
- Update `isPlayingFlow` in `onIsPlayingChanged` and `onEvents` (for initial sync).

#### [MODIFY] [ProfileMusicService.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/ProfileMusicService.kt)
- Expose `isPlayingFlow` from the `PlaybackController`.

#### [MODIFY] [MusicProvider.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicProvider.kt)
- Collect `isPlayingFlow` from `ProfileMusicService` within a coroutine scope.
- Update the `isPlaying` property in `MusicUiState` whenever the flow emits a new value.
- Remove redundant manual state updates in `play()`, `pause()`, and `pauseResume()`.

## Verification Plan

### Automated Tests
- Since this involves hardware/service interactions, automated testing will be limited to unit tests for `MusicProvider` if possible, but manual verification is preferred for the full flow.

### Manual Verification
1. Start a workout in the app.
2. Play music.
3. Pause music using the phone's notification shade or lock screen controls.
4. Verify that the pause button in the Workout UI changes to a play button.
5. Resume music using the notification shade.
6. Verify that the play button in the Workout UI changes to a pause button.
7. Repeat using headphone controls if available.
