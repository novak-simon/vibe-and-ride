# Workout Redesign Notes

## Goals
- Support both Structured Workouts and Free Ride.
- Decouple MusicEngine from interval-based assumptions.
- Prepare for BLE integration.

## Proposed Data Structures

```kotlin
sealed class WorkoutMode {
    data class Structured(val workout: Workout) : WorkoutMode()
    object FreeRide : WorkoutMode()
}

data class StructuredState(
    val intervalIndex: Int,
    val currentInterval: Interval,
    val elapsedInInterval: Duration,
    val remainingInInterval: Duration,
    val totalRemaining: Duration
)

data class WorkoutSessionState(
    val mode: WorkoutMode,
    val status: WorkoutStatus,
    val totalElapsed: Duration,
    val currentCadence: Double?,
    val currentPower: Double?,
    val structuredState: StructuredState? = null
)
```

## Logic Changes

### WorkoutScheduler
- `workoutIdSelected` becomes `startStructuredWorkout(workoutId)`.
- Add `startFreeRide()`.
- Add `updateLiveCadence(cadence: Double)`.
- `getState()` returns `WorkoutSessionState`.

### MusicEngine
- `workoutSelected(workoutState: WorkoutState)` -> `sessionStarted(sessionState: WorkoutSessionState)`.
- `play(intervalIndex: Int?, remainingInterval: Duration)` -> `update(sessionState: WorkoutSessionState)`.
- Recommendation logic needs to handle null `StructuredState`.
- If `FreeRide`, precompute based on `currentCadence` when track is ending.
