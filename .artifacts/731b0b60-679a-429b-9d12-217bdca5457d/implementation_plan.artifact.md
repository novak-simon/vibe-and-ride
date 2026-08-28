# Phase 10: Error Handling & Observability

Improve the application's stability and maintainability by introducing structured logging, explicit domain error types, and better error handling in repositories and ViewModels. This phase aims to replace broad `catch (e: Exception)` blocks and `println` logging with a more robust system that provides better diagnostics and user-facing feedback.

## User Review Required

> [!IMPORTANT]
> This change introduces a custom logging abstraction `VibeLog`. While it currently maps to `Logcat` on Android and `println` elsewhere, it can be extended to support external crash reporting (like Firebase Crashlytics) in the future.

> [!NOTE]
> We will be introducing `Result` wrappers or specific domain exceptions. This might change some method signatures in repositories.

## Proposed Changes

### [Common Domain & Infrastructure]

#### [NEW] [VibeLog.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/util/VibeLog.kt)
Create an `expect` object `VibeLog` for cross-platform logging (D, I, W, E levels).

#### [NEW] [VibeLog.android.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/androidMain/kotlin/com/novaksimon/vibeandride/util/VibeLog.android.kt)
`actual` implementation using `android.util.Log`.

#### [NEW] [VibeLog.jvm.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/jvmMain/kotlin/com/novaksimon/vibeandride/util/VibeLog.jvm.kt)
`actual` implementation using `println`.

#### [NEW] [VibeAndRideError.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/model/VibeAndRideError.kt)
Define a sealed class hierarchy for domain-specific errors.
- `MusicError`: Issues with playback, recommendation, or metadata.
- `WorkoutError`: Issues with parsing, cadence sources, or session state.
- `DataError`: Issues with Supabase, network, or local persistence.

---

### [Repositories]

#### [MODIFY] [SupabaseSongRepository.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/database/SupabaseSongRepository.kt)
Replace `println` with `VibeLog.e`. Refactor `getAudioUrl` to provide more context on failure.

#### [MODIFY] [ProfileSongStatsRepository.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/database/ProfileSongStatsRepository.kt)
Improve error handling in Supabase operations.

---

### [ViewModels]

#### [MODIFY] [WorkoutViewModel.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/workout/WorkoutViewModel.kt)
Update `WorkoutUiState` to include an optional `error` field. Handle repository failures by updating the UI state rather than just printing.

#### [MODIFY] [MusicViewModel.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/music/MusicViewModel.kt)
Implement better error propagation from `MusicEngine` and repositories. Handle "no music available" scenarios more gracefully.

---

### [Android UI]

#### [MODIFY] [WorkoutScreenContent.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/ui/WorkoutScreenContent.kt)
Display error messages to the user (e.g., using `Snackbar` or specific error banners).

## Verification Plan

### Automated Tests
- `gradle_build(":shared:assemble")` to ensure KMP structure is correct.
- `gradle_build(":shared:test")` to verify that existing tests pass with the new logging.
- Add new unit tests for `VibeAndRideError` mapping in `SupabaseSongRepository`.

### Manual Verification
1. **Logcat Inspection**: Verify that logs appear with correct tags and levels on Android.
2. **Network Interruption**: Disable Wi-Fi/Data and attempt to load a workout or song. Verify that a user-friendly error message is shown.
3. **Invalid Workout File**: Attempt to upload a malformed `.zwo` file and verify the error is reported to the user.
4. **Song Playback Failure**: Simulate a 404 on a song URL (if possible via debug tools) and verify `MusicEngine` attempts to skip to the next song.
