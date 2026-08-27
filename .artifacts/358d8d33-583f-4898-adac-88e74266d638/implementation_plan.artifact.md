# Phase 7: Workout Parsing Improvements (Repeats & Ranges)

Improve the `WorkoutParser` to correctly handle workout repeats (`IntervalsT`) and verify/fix power/cadence range handling.

## User Review Required

> [!IMPORTANT]
> The parser will now flatten `<IntervalsT>` blocks into individual intervals. A repeat of 5 with an On/Off pair will result in 10 separate intervals in the list. This ensures compatibility with the existing `WorkoutScheduler` without needing to change the domain model.

## Proposed Changes

### Workout Parsing

#### [MODIFY] [WorkoutParser.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/workout/WorkoutParser.kt)
- Add special handling for the `IntervalsT` tag:
    - Parse `Repeat`, `OnDuration`, `OffDuration`, `OnPower`, `OffPower`, `OnCadence`, `OffCadence`.
    - Generate `Repeat` number of interval pairs.
- Refactor attribute reading to distinguish between "missing" and "zero" if necessary, although averaging `PowerLow`/`High` is generally correct for the target cadence/power in this app.
- Ensure `CadenceLow`/`High` are rounded to the nearest integer when averaged.

### Tests

#### [MODIFY] [WorkoutParserCharacterizationTest.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonTest/kotlin/com/novaksimon/vibeandride/workout/WorkoutParserCharacterizationTest.kt)
- Add a test case that parses a ZWO string containing `<IntervalsT>`.
- Verify that the total duration and interval count are correct (e.g., parsing `ZwiftWorkoutAspire2h.zwo` should now yield a ~2h duration).

## Verification Plan

### Automated Tests
- Run `shared:commonTest` specifically focusing on `WorkoutParserCharacterizationTest`.
- Create a new test `testParseRepeats` that uses a snippet from the provided `ZwiftWorkoutAspire2h.zwo`.

### Manual Verification
- Upload the `ZwiftWorkoutAspire2h.zwo` file in the app and verify that the "Total Time" displayed on the UI is correct (~2 hours).
