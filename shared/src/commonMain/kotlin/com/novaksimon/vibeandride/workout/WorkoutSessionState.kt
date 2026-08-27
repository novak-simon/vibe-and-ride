package com.novaksimon.vibeandride.workout

import com.novaksimon.vibeandride.model.Interval
import kotlin.time.Duration

enum class WorkoutStatus {
    NOT_STARTED, READY, RUNNING, PAUSED, FINISHED
}

data class StructuredState(
    val intervalIndex: Int,
    val currentInterval: Interval,
    val elapsedInInterval: Duration,
    val remainingInInterval: Duration,
    val totalRemaining: Duration,
)

data class WorkoutSessionState(
    val mode: WorkoutMode?,
    val status: WorkoutStatus,
    val totalElapsed: Duration,
    val currentCadence: Double? = null,
    val currentPower: Double? = null,
    val structuredState: StructuredState? = null
)
