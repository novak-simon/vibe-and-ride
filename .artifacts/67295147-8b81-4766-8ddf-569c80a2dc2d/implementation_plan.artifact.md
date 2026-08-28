# Music Playback Issues Fix

This plan addresses two issues reported in music playback:
1.  **Wrong Preload/Skip Logic**: A song is preloaded, but immediately overwritten by a subsequent preload, leading to the wrong song being played (or skipped).
2.  **Notification/UI Desync**: Notification shows the new song, but audio stops and the app banner shows the old song paused.

## User Review Required

> [!IMPORTANT]
> The changes involve making playback control operations `suspend` all the way to the platform implementation. This ensures the `MusicEngine` stays in the `TRANSITIONING` state until the actual crossfade is complete, preventing UI desync.

## Proposed Changes

### [Music Engine]

#### [MODIFY] [MusicEngine.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicEngine.kt)
- Remove the premature update of `songIndex` in `update()` when a preload is decided for a new interval. The `songIndex` should only be updated when the transition actually occurs.
- This prevents the engine from "forgetting" its place in the current interval if it preloads the first song of the next interval too early.

### [Playback Control Layer]

#### [MODIFY] [PlaybackController.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/PlaybackController.kt)
- Change `crossfadeToPreloaded` to a `suspend` function.

#### [MODIFY] [ProfileMusicService.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/ProfileMusicService.kt)
- Ensure `crossfadeTo` properly awaits the controller's crossfade completion.

#### [MODIFY] [AndroidPlaybackController.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/player/AndroidPlaybackController.kt)
- Update `crossfadeToPreloaded` to be `suspend` and await the `SessionResult` from the `MediaController`.

### [Android Platform Player]

#### [MODIFY] [AndroidAudioPlayer.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/player/AndroidAudioPlayer.kt)
- Change `crossfadeToPreloaded` to a `suspend` function that does the fading in the calling coroutine instead of launching its own.
- Ensure `onActivePlayerChanged` is called only when the switch is definitive.
- Fix potential race conditions where multiple crossfades might overlap.

#### [MODIFY] [PlaybackService.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/player/PlaybackService.kt)
- Handle `COMMAND_CROSSFADE` asynchronously in `onCustomCommand` and wait for the `AndroidAudioPlayer` to finish the crossfade before completing the command future.

## Verification Plan

### Automated Tests
- Run `MusicEngineTest.kt` to ensure standard state transitions are still correct.
- Add a test case to `MusicEngineTest.kt` that simulates preloading for a new interval and verify `songIndex` remains correct until transition.

### Manual Verification
- Deploy to an Android device.
- Start a structured workout.
- Observe behavior when approaching the end of a track and the end of an interval.
- Verify that skipping songs and natural transitions keep the UI, Notification, and Audio in sync.
