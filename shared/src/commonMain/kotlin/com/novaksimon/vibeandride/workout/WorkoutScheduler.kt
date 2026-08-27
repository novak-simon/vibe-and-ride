package com.novaksimon.vibeandride.workout

import com.novaksimon.vibeandride.database.getWorkoutById
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private data class IntervalLookup(
    val index: Int?,
    val interval: Interval?,
    val startOffset: Duration,
)

class WorkoutScheduler {
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    
    var mode: WorkoutMode? = null
        internal set

    var status: WorkoutStatus = WorkoutStatus.NOT_STARTED
        private set

    private var startMark: TimeMark? = null
    private var elapsedBeforeStart: Duration = Duration.ZERO
    
    private var liveCadence: Double? = null
    private var livePower: Double? = null

    val isRunning: Boolean get() = status == WorkoutStatus.RUNNING
    
    fun start() {
        val m = mode ?: return
        if (isRunning) return
        
        val total = when (m) {
            is WorkoutMode.Structured -> m.workout.totalDuration.seconds
            is WorkoutMode.FreeRide -> Duration.INFINITE
        }

        if (total <= Duration.ZERO) {
            status = WorkoutStatus.FINISHED
            elapsedBeforeStart = Duration.ZERO
            startMark = null
            return
        }
        
        if (status == WorkoutStatus.READY) {
            status = WorkoutStatus.RUNNING
            elapsedBeforeStart = Duration.ZERO
            startMark = timeSource.markNow()
        }
    }

    fun pauseResume() {
        if (isRunning) {
            elapsedBeforeStart = currentElapsed()
            startMark = null
            status = WorkoutStatus.PAUSED
        } else if (status == WorkoutStatus.PAUSED || status == WorkoutStatus.READY) {
            startMark = timeSource.markNow()
            status = WorkoutStatus.RUNNING
        }
    }

    fun reset() {
        status = WorkoutStatus.NOT_STARTED
        startMark = null
        elapsedBeforeStart = Duration.ZERO
        mode = null
        liveCadence = null
        livePower = null
    }

    fun adjustTime(delta: Duration) {
        val current = currentElapsed()
        resync(current + delta)
    }

    fun resync(workoutTime: Duration) {
        if (workoutTime < Duration.ZERO) throw IllegalArgumentException("Workout time for resync must be non-negative")

        val total = when (val m = mode) {
            is WorkoutMode.Structured -> m.workout.totalDuration.seconds
            else -> Duration.INFINITE
        }
        
        val clamped = minOf(workoutTime, total)
        val running = isRunning
        elapsedBeforeStart = clamped
        
        if (running) {
            startMark = timeSource.markNow()
            if (clamped >= total && total != Duration.INFINITE) {
                status = WorkoutStatus.FINISHED
                startMark = null
            }
        } else {
            startMark = null
            status = when {
                clamped >= total && total != Duration.INFINITE -> WorkoutStatus.FINISHED
                status == WorkoutStatus.NOT_STARTED && clamped == Duration.ZERO -> WorkoutStatus.NOT_STARTED
                else -> WorkoutStatus.PAUSED
            }
        }
    }

    suspend fun workoutIdSelected(workoutId: Int) {
        val workout = withContext(Dispatchers.IO) {
            getWorkoutById(workoutId)
        }
        if (workout == null) {
            return
        }
        mode = WorkoutMode.Structured(workout)
        status = WorkoutStatus.NOT_STARTED
    }
    
    fun startFreeRide() {
        mode = WorkoutMode.FreeRide
        status = WorkoutStatus.READY
    }
    
    fun updateLiveCadence(cadence: Double) {
        liveCadence = cadence
    }
    
    fun updateLivePower(power: Double) {
        livePower = power
    }

    fun musicPrepared() {
        if (status == WorkoutStatus.NOT_STARTED) {
            status = WorkoutStatus.READY
            println("Status of workout: $status")
        }
    }

    fun getState(): WorkoutSessionState {
        val m = mode
        var totalElapsed = Duration.ZERO
        if (status != WorkoutStatus.NOT_STARTED) {
            totalElapsed = currentElapsed()
        }
        
        val total = when (m) {
            is WorkoutMode.Structured -> m.workout.totalDuration.seconds
            else -> Duration.INFINITE
        }

        if (isRunning && totalElapsed >= total && total != Duration.INFINITE) {
            status = WorkoutStatus.FINISHED
            startMark = null
            elapsedBeforeStart = total
            totalElapsed = total
        }

        val structuredState = if (m is WorkoutMode.Structured) {
            val lookup = findInterval(totalElapsed, m.workout)
            if (lookup.interval != null && lookup.index != null) {
                val elapsedInInterval = totalElapsed - lookup.startOffset
                val intervalLength = lookup.interval.duration.seconds
                StructuredState(
                    intervalIndex = lookup.index,
                    currentInterval = lookup.interval,
                    elapsedInInterval = elapsedInInterval,
                    remainingInInterval = maxOf(Duration.ZERO, intervalLength - elapsedInInterval),
                    totalRemaining = maxOf(Duration.ZERO, total - totalElapsed)
                )
            } else null
        } else null

        return WorkoutSessionState(
            mode = mode,
            status = status,
            totalElapsed = totalElapsed,
            currentCadence = liveCadence ?: (structuredState?.currentInterval?.cadence),
            currentPower = livePower ?: (structuredState?.currentInterval?.power?.toDouble()),
            structuredState = structuredState
        )
    }

    private fun currentElapsed(): Duration {
        val total = when (val m = mode) {
            is WorkoutMode.Structured -> m.workout.totalDuration.seconds
            else -> Duration.INFINITE
        }

        return when {
            isRunning && startMark != null -> minOf(elapsedBeforeStart + startMark!!.elapsedNow(), total)
            else -> minOf(elapsedBeforeStart, total)
        }
    }

    private fun findInterval(workoutTime: Duration, workout: Workout): IntervalLookup {
        var elapsed = Duration.ZERO
        workout.intervals.forEachIndexed { index, interval ->
            val intervalDuration = interval.duration.seconds
            if (workoutTime < elapsed + intervalDuration) {
                return IntervalLookup(index, interval, elapsed)
            }
            elapsed += intervalDuration
        }
        return IntervalLookup(null, null, elapsed)
    }
}
