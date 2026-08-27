# Fix Audio Stickiness on Song Skip

The issue where skipping a song updates the UI but continues playing the old (or previously preloaded) audio is caused by "sticky" state in `ProfileMusicService`. The service retains the last preloaded audio URL and uses it for the next crossfade without verifying if it matches the song being transitioned to.

## Proposed Changes

### [Music Component]

#### [MODIFY] [ProfileMusicService.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/ProfileMusicService.kt)
- Add a `preloadedSongId: Int?` field to track which song the `preloadedAudioUrl` belongs to.
- Update `preloadSong(song: Song)` to store both the `audioUrl` and the `song.id`.
- Update `crossfadeTo(song: Song, ...)` to:
    - Only use the `preloadedAudioUrl` if `preloadedSongId` matches the incoming `song.id`.
    - Always clear `preloadedAudioUrl` and `preloadedSongId` after they are consumed or discarded in this method. This ensures that a subsequent skip will fetch a fresh URL instead of reusing an outdated one.

## Verification Plan

### Automated Tests
- I will verify that the project builds successfully.
- I will check if any existing tests cover this flow (though a unit test for this might require mocking the `PlaybackController` and verifying sequence of calls).
    - `gradle :androidApp:assembleDebug`

### Manual Verification
- Deploy to a device/emulator.
- Start a workout.
- Perform a skip. Verify audio changes to the next song.
- Perform another skip immediately (after the transition delay). Verify audio changes again.
- Verify that natural transitions at the end of a song or interval still work correctly.
