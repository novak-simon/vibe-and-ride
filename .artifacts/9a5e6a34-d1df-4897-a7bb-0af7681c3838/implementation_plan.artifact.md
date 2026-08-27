# Fix Infinite Recursion in SupabaseProfileRepository

The `SupabaseProfileRepository` class is experiencing a `StackOverflowError` (infinite recursion) because its methods have the same names as the top-level database functions they are intended to delegate to. Even with the package prefix, the calls are resolving to the member functions themselves.

## Proposed Changes

### Database Layer

#### [MODIFY] [SupabaseProfileRepository.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/database/SupabaseProfileRepository.kt)

I will introduce import aliases for the top-level functions in `ProfileRepository.kt` and `ProfileSongStatsRepository.kt` to clearly distinguish them from the member functions of `SupabaseProfileRepository`.

- Add import aliases:
    - `com.novaksimon.vibeandride.database.getProfileForRecommendation as getProfileForRecommendationInternal`
    - `com.novaksimon.vibeandride.database.getStatByProfileAndSongId as getStatByProfileAndSongIdInternal`
    - `com.novaksimon.vibeandride.database.changeLikeState as changeLikeStateInternal`
    - `com.novaksimon.vibeandride.database.incrementStat as incrementStatInternal`
    - `com.novaksimon.vibeandride.database.setStatDatetime as setStatDatetimeInternal`
- Update the method bodies to use these aliases.

## Verification Plan

### Automated Tests
- I will attempt to run the existing tests for the recommendation or database layer if available.
- Specifically, I'll look for tests that exercise `SupabaseProfileRepository`.

### Manual Verification
- Since I cannot run the full app and observe the "AndroidRuntime" tag directly in a live loop, I will rely on the code analysis and the fact that disambiguating recursive calls is the standard fix for this type of stack trace.
- I will verify the code compiles by checking for syntax errors after the change.
