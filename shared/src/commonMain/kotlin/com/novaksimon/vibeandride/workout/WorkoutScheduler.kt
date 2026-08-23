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

enum class WorkoutStatus {
    NOT_STARTED, READY, RUNNING, PAUSED, FINISHED
}

data class WorkoutState(
    val workout: Workout?,
    var status: WorkoutStatus,
    val intervalIndex: Int?,
    val currentInterval: Interval?,
    val elapsedInInterval: Duration,
    val remainingInInterval: Duration,
    val intervalLength: Duration,
    val totalElapsed: Duration,
    val totalRemaining: Duration,
)

private data class IntervalLookup(
    val index: Int?,
    val interval: Interval?,
    val startOffset: Duration,
)
class WorkoutScheduler() {
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    var workout: Workout? = null
        private set

    var status: WorkoutStatus = WorkoutStatus.NOT_STARTED
        private set

    private var startMark: TimeMark? = null
    private var elapsedBeforeStart: Duration = Duration.ZERO
    private var lastIntervalIndex: Int? = -2 // sentinel distinct from any real index or null

    val isRunning: Boolean get() = status == WorkoutStatus.RUNNING
    fun start() {
        val w = workout ?: return
        if (isRunning) return
        if (w.totalDuration <= 0f) {
            status = WorkoutStatus.FINISHED
            elapsedBeforeStart = Duration.ZERO
            startMark = null
            lastIntervalIndex = -2
            return
        }
        if (status == WorkoutStatus.READY) {
            status = WorkoutStatus.RUNNING
            elapsedBeforeStart = Duration.ZERO
            startMark = timeSource.markNow()
            lastIntervalIndex = -2
        }
    }

    fun pauseResume() {
        if (isRunning) {
            elapsedBeforeStart = currentElapsed()
            startMark = null
            status = WorkoutStatus.PAUSED
        } else {
            startMark = timeSource.markNow()
            status = WorkoutStatus.RUNNING
        }
    }

    fun reset() {
        status = WorkoutStatus.NOT_STARTED
        startMark = null
        elapsedBeforeStart = Duration.ZERO
        lastIntervalIndex = -2
    }

    fun resync(workoutTime: Duration) {
        if (workoutTime < Duration.ZERO) throw IllegalArgumentException("Workout time for resync must be non-negative")
        val total = workout?.totalDuration?.seconds ?: Duration.ZERO
        val clamped = minOf(workoutTime, total)
        val running = isRunning
        elapsedBeforeStart = clamped
        if (running) {
            startMark = timeSource.markNow()
            if (clamped >= total) {
                status = WorkoutStatus.FINISHED
                startMark = null
            }
        } else {
            startMark = null
            status = when {
                clamped >= total -> WorkoutStatus.FINISHED
                status == WorkoutStatus.NOT_STARTED && clamped == Duration.ZERO -> WorkoutStatus.NOT_STARTED
                else -> WorkoutStatus.PAUSED
            }
        }
        lastIntervalIndex = -2
    }

    suspend fun workoutIdSelected(workoutId: Int) {
        val workout = withContext(Dispatchers.IO) {
            getWorkoutById(workoutId)
        }
        if (workout == null) {
            return
        }
        initializeWithWorkout(workout)
    }

    fun musicPrepared() {
        if (status == WorkoutStatus.NOT_STARTED) {
            status = WorkoutStatus.READY
            println("Status of workout: $status")
        }
    }


    fun getState(): WorkoutState {
        val w = workout
        var totalElapsed = Duration.ZERO
        if (status != WorkoutStatus.NOT_STARTED) {
            totalElapsed = currentElapsed()
        }
        val total = w?.totalDuration?.seconds ?: Duration.ZERO

        if (isRunning && totalElapsed >= total) {
            status = WorkoutStatus.FINISHED
            startMark = null
            elapsedBeforeStart = total
            totalElapsed = total
        }


        val lookup = findInterval(totalElapsed)
        val elapsedInInterval: Duration
        val remainingInInterval: Duration
        val intervalLength: Duration

        if (lookup.interval == null) {
            elapsedInInterval = Duration.ZERO
            remainingInInterval = Duration.ZERO
            intervalLength = Duration.ZERO
        } else {
            elapsedInInterval = totalElapsed - lookup.startOffset
            intervalLength = lookup.interval.duration.seconds
            remainingInInterval = maxOf(Duration.ZERO, intervalLength - elapsedInInterval)
        }
        return WorkoutState(
            workout = workout,
            status = status,
            intervalIndex = lookup.index,
            currentInterval = lookup.interval,
            elapsedInInterval = elapsedInInterval,
            remainingInInterval = remainingInInterval,
            intervalLength = intervalLength,
            totalElapsed = totalElapsed,
            totalRemaining = if (w != null) maxOf(Duration.ZERO, total - totalElapsed) else Duration.ZERO,
        )
    }

    private fun initializeWithWorkout(workout: Workout) {
        this.workout = workout
    }

    private fun currentElapsed(): Duration {
        val w = workout
        val total = w?.totalDuration?.seconds ?: Duration.ZERO

        return when {
            isRunning && startMark != null -> minOf(elapsedBeforeStart + startMark!!.elapsedNow(), total)
            w != null -> minOf(elapsedBeforeStart, total)
            else -> elapsedBeforeStart
        }
    }

    private fun findInterval(workoutTime: Duration): IntervalLookup {
        var elapsed = Duration.ZERO
        val w = workout ?: return IntervalLookup(null, null, elapsed)
        w.intervals.forEachIndexed { index, interval ->
            val intervalDuration = interval.duration.seconds
            if (workoutTime < elapsed + intervalDuration) {
                return IntervalLookup(index, interval, elapsed)
            }
            elapsed += intervalDuration
        }
        return IntervalLookup(null, null, elapsed)
    }
}