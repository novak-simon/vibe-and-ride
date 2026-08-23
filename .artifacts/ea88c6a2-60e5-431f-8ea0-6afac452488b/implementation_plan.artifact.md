# Fix IllegalStateException in MediaCodec EventHandler

The application is experiencing a `java.lang.IllegalStateException: Handler (android.media.MediaCodec$EventHandler) sending message to a Handler on a dead thread`. This typically happens when a `MediaCodec` instance (used internally by ExoPlayer) attempts to send a callback to a thread (usually the player's internal playback thread) that has already been shut down during the `release()` process.

## Proposed Changes

### [Component] Playback Engine

#### [MODIFY] [AndroidAudioPlayer.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/player/AndroidAudioPlayer.kt)
- Update `release()` to explicitly `stop()` and `clearMediaItems()` for both players before calling `release()`. This helps ensure that internal components like `MediaCodec` are properly signaled to shut down before the playback thread is terminated.
- Store `Player.Listener` instances to allow explicit removal during `release()`.
- Add `setLooper(Looper.getMainLooper())` to `ExoPlayer.Builder` to ensure callbacks are handled on the main thread, providing more stability during teardown.

#### [MODIFY] [PlaybackService.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/player/PlaybackService.kt)
- Update `onDestroy()` to disconnect the active player from the `MediaSession` before releasing the session and the `AndroidAudioPlayer`. This prevents the session from attempting to interact with players that are in the process of being released.

## Verification Plan

### Automated Tests
- Run the application and perform multiple track changes and crossfades.
- Close the application (swipe away from recent apps) while music is playing and verify that no `IllegalStateException` is logged in Logcat.

### Manual Verification
- Monitor Logcat for the specific error message: `Handler (android.media.MediaCodec$EventHandler) {.*} sending message to a Handler on a dead thread`.
