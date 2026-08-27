# Fix IllegalStateException in MediaCodec EventHandler

The application is experiencing a `java.lang.IllegalStateException: Handler (android.media.MediaCodec$EventHandler) sending message to a Handler on a dead thread`. This typically happens when a `MediaCodec` instance (managed by `ExoPlayer`) tries to send a callback to a thread that has already been shut down during the player's release process.

## Proposed Changes

### [Playback Component]

#### [MODIFY] [PlaybackService.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/player/PlaybackService.kt)
- Update `onDestroy()` to follow the correct Media3 teardown sequence: release the `MediaSession` first, then the underlying `AudioPlayer`.
- Remove redundant `audioPlayer.stop()` call in `onDestroy()` since `release()` handles it.

#### [MODIFY] [AndroidAudioPlayer.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/player/AndroidAudioPlayer.kt)
- Add a `isReleased` flag to prevent operations after the player has been shut down.
- Refine the `release()` method to:
    - Cancel any ongoing crossfade jobs immediately.
    - Remove listeners before releasing the players.
    - Release `ExoPlayer` instances without redundant `stop()` calls, ensuring they are released only if not already null.
    - Set player references to null immediately after release.
- Ensure all player-related calls (like `play`, `pause`, `stop`, `release`) check the `isReleased` flag.

## Verification Plan

### Automated Tests
- I will verify that the project still builds successfully after these changes.
- I will run the existing unit tests to ensure no regressions in playback logic.
    - `gradle :androidApp:assembleDebug`

### Manual Verification
- Deploy the app to a device/emulator.
- Start music playback, perform crossfades, and then close the app (swipe away from recent apps) to trigger the `PlaybackService.onDestroy()` flow.
- Monitor Logcat for the `IllegalStateException`.
