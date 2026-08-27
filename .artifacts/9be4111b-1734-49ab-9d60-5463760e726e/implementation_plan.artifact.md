# UI Redesign and Navigation Shell Implementation

Redesign the Ride screen and add two new screens (Home, Settings) with bottom navigation in the vibe-and-ride Kotlin Multiplatform app.

## User Review Required

> [!IMPORTANT]
> The navigation is implemented using a simple `sealed class` and `mutableStateOf` in `MainActivity` to avoid adding new dependencies (like NavHost) and to maintain the existing ViewModel initialization pattern.

## Proposed Changes

### Shared Module (Data & ViewModel)

#### [MODIFY] [ProfileSongStatsRepository.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/database/ProfileSongStatsRepository.kt)
- Add `getLikedSongsWithDetails` function to fetch real liked songs from Supabase.

#### [NEW] [HomeViewModel.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/ui/HomeViewModel.kt)
- Create `HomeViewModel` to handle static placeholder data for "Recent" and "Suggested" workouts, and real "Liked Songs" data.

---

### Android App Module (UI)

#### [MODIFY] [MainActivity.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/MainActivity.kt)
- Implement `Scaffold` with `NavigationBar` for bottom navigation.
- Switch between `HomeScreen`, `WorkoutScreen`, and `SettingsScreen` based on selected tab.
- Initialize `HomeViewModel`.

#### [NEW] [HomeScreen.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/ui/HomeScreen.kt)
- Implement the Home screen with "Recent workouts", "Suggested workouts" (placeholders), and "Liked songs" (real data).

#### [NEW] [SettingsScreen.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/ui/SettingsScreen.kt)
- Implement a static Settings screen with a list of navigation stubs.

#### [MODIFY] [WorkoutScreenContent.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/ui/WorkoutScreenContent.kt)
- Complete redesign of the Workout (Ride) tab layout:
    - New header and interval info row (Previous/Current/Next).
    - Interval power/cadence profile chart.
    - Unified Start/Pause controls.
    - Restyled music mini-player.
    - Dark Material3 theme compliance.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.
- Run `gradle_build(":androidApp:assembleDebug")`.

### Manual Verification
- Deploy to Android device/emulator.
- Verify Bottom Navigation works and switches between Home, Ride, and Settings.
- Verify Home screen displays placeholder workouts and real liked songs.
- Verify Ride screen shows the new redesigned layout and controls.
- Verify Settings screen shows the static list.
