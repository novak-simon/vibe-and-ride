# Recommendation Scoring Fix and Roadmap Planning

Correct the inverted skip penalty logic in the `RecommendationEngine` and plan the subsequent architectural steps.

## User Review Required

> [!IMPORTANT]
> The skip penalty formula is being changed from a multiplicative one that erroneously increased with skips to a ratio-based one:
> `skipCoefficient = 2.0 * (playCount / (playCount + skipCount))`
> This ensures that songs with more skips relative to plays are correctly penalized.

## Proposed Changes

### Recommendation Engine

#### [MODIFY] [RecommendationEngine.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/recommendation/RecommendationEngine.kt)
- Update `getProfilePreferenceScore` to use the new skip penalty formula in both `lastPlayedAt == null` and standard blocks.

#### [MODIFY] [RecommendationEngineTest.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonTest/kotlin/com/novaksimon/vibeandride/recommendation/RecommendationEngineTest.kt)
- Add a specific test case `testProfilePreference_SkipPenaltyCorrectness` to verify that increasing skips reduces the score and that the inversion bug is fixed.

## Verification Plan

### Automated Tests
- Run `shared:commonTest` to verify all recommendation tests pass.
- Specifically verify the new `testProfilePreference_SkipPenaltyCorrectness` test.

### Manual Verification
- None required for this logic-only fix, though the Human PM may notice improved variety and better avoidance of skipped tracks in the live app.

---

# Roadmap Planning: Next Steps

After this fix, we will proceed with the following phases:

1. **Phase 4: Workout Domain Redesign**
   - Introduce `WorkoutSession` abstraction.
   - Support "Free Ride" mode.
2. **Phase 7: Workout Parsing Improvements**
   - Fix range-based power/cadence in `WorkoutParser`.
3. **Phase 5: BLE Integration**
   - Implement Bluetooth cadence source.
