# Restructure WorkoutScreenContent for Better Music and Workout Control

Distinguish between workout and music controls in the `WorkoutScreenContent`. Add a dedicated music control bar at the bottom (Spotify-like) and workout controls above it.

## User Review Required

> [!NOTE]
> The music control will be placed at the very bottom of the screen. The workout controls (Start, Pause/Resume Workout, Reset, Resync) will be placed immediately above it.

> [!IMPORTANT]
> - "Music Pause/Resume" will call `MusicViewModel.play()` / `MusicViewModel.pause()` which directly interact with the `PlaybackController` (Media3).
> - "Workout Pause/Resume" will trigger BOTH music and workout states, as currently implemented in `onPauseResumeBoth`.

## Proposed Changes

### androidApp

#### [MODIFY] [WorkoutScreenContent.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/ui/WorkoutScreenContent.kt)
- Update `WorkoutScreenContent` parameters to include explicit music control callbacks if necessary, or reuse existing ones.
- Restructure the UI:
    - Keep the top row (Select Workout, Debug).
    - Move workout status and interval information to the upper part of the screen.
    - Create a dedicated "Workout Controls" section above the bottom music bar.
    - Create a "Music Control Bar" at the bottom with:
        - Small album art.
        - Song Title and Artist.
        - Play/Pause icon button.
        - Skip icon button.
        - Like icon button.
- Add Material Icons: `PlayArrow`, `Pause`, `SkipNext`, `Refresh`, `Sync`.

#### [MODIFY] [WorkoutScreen.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/ui/WorkoutScreen.kt)
- Update callbacks passed to `WorkoutScreenContent` to match the new requirements.
- Add `onMusicPauseResume` (or separate play/pause) that calls `musicViewModel` methods.

## Verification Plan

### Manual Verification
- Deploy to Android device/emulator.
- Verify the layout: Workout controls should be above Music controls.
- Verify Music Play/Pause button: Should toggle playback via Media3.
- Verify Music Skip button: Should skip to next recommended song.
- Verify Workout Pause/Resume: Should toggle both workout timer and music playback.
- Verify Start, Reset, and Resync buttons work as expected.
