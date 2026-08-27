package com.novaksimon.vibeandride.workout

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Workout
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class WorkoutSchedulerCharacterizationTest {

    private lateinit var scheduler: WorkoutScheduler

    @BeforeTest
    fun setup() {
        scheduler = WorkoutScheduler()
    }

    private fun setWorkout(scheduler: WorkoutScheduler, workout: Workout) {
        val method = scheduler::class.java.getDeclaredMethod("initializeWithWorkout", Workout::class.java)
        method.isAccessible = true
        method.invoke(scheduler, workout)
    }

    private fun createSimpleWorkout(intervals: List<Interval> = listOf(
        Interval(10.0, 100f, 90.0, "warmup"),
        Interval(20.0, 200f, 100.0, "work"),
        Interval(10.0, 100f, 90.0, "cooldown")
    )): Workout {
        return Workout(
            id = 1,
            name = "Test Workout",
            author = "Test Author",
            intervals = intervals
        )
    }

    @Test
    fun testInitialState() {
        val state = scheduler.getState()
        assertNull(state.workout)
        assertEquals(WorkoutStatus.NOT_STARTED, state.status)
        assertNull(state.intervalIndex)
        assertNull(state.currentInterval)
        assertEquals(Duration.ZERO, state.elapsedInInterval)
        assertEquals(Duration.ZERO, state.remainingInInterval)
        assertEquals(Duration.ZERO, state.intervalLength)
        assertEquals(Duration.ZERO, state.totalElapsed)
        assertEquals(Duration.ZERO, state.totalRemaining)
    }

    @Test
    fun testWorkoutSelection() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        val state = scheduler.getState()
        assertEquals(workout, state.workout)
        assertEquals(WorkoutStatus.NOT_STARTED, state.status)
        assertEquals(0, state.intervalIndex)
        assertEquals(workout.intervals[0], state.currentInterval)
        assertEquals(40.seconds, state.totalRemaining)
        assertEquals(40.seconds, workout.totalDuration.seconds)
    }

    @Test
    fun testStartBehavior() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        scheduler.musicPrepared() // Move to READY
        
        scheduler.start()
        
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.RUNNING, state.status)
        assertTrue(state.totalElapsed >= Duration.ZERO)
        assertEquals(0, state.intervalIndex)
        assertTrue(state.totalRemaining <= 40.seconds)
    }

    @Test
    fun testStartRepeatedly() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        scheduler.musicPrepared()
        scheduler.start()
        
        val elapsed1 = scheduler.getState().totalElapsed
        scheduler.start()
        val elapsed2 = scheduler.getState().totalElapsed
        
        assertEquals(WorkoutStatus.RUNNING, scheduler.status)
        // Should not reset elapsed time
        assertTrue(elapsed2 >= elapsed1)
    }

    @Test
    fun testIntervalProgression() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        // Interval 1: 0-10s
        scheduler.resync(5.seconds)
        var state = scheduler.getState()
        assertEquals(0, state.intervalIndex)
        assertEquals(5.seconds, state.elapsedInInterval)
        
        // Interval 2: 10-30s
        scheduler.resync(15.seconds)
        state = scheduler.getState()
        assertEquals(1, state.intervalIndex)
        assertEquals(5.seconds, state.elapsedInInterval) // 15 - 10
        
        // Interval 3: 30-40s
        scheduler.resync(35.seconds)
        state = scheduler.getState()
        assertEquals(2, state.intervalIndex)
        assertEquals(5.seconds, state.elapsedInInterval) // 35 - 30
        
        // After final interval
        scheduler.resync(45.seconds)
        state = scheduler.getState()
        assertNull(state.intervalIndex)
        assertEquals(WorkoutStatus.FINISHED, state.status)
    }

    @Test
    fun testExactIntervalBoundaries() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        // Just before boundary (9.9s)
        scheduler.resync(9.9.seconds)
        assertEquals(0, scheduler.getState().intervalIndex)
        
        // Exactly at boundary (10.0s)
        // findInterval: if workoutTime < elapsed + intervalDuration
        // 10.0 < 0 + 10.0 is FALSE. So it moves to next.
        scheduler.resync(10.0.seconds)
        assertEquals(1, scheduler.getState().intervalIndex)
        
        // Just after boundary (10.1s)
        scheduler.resync(10.1.seconds)
        assertEquals(1, scheduler.getState().intervalIndex)
    }

    @Test
    fun testEndOfWorkout() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        // Exactly at end (40.0s)
        scheduler.resync(40.0.seconds)
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.FINISHED, state.status)
        assertNull(state.intervalIndex)
        assertNull(state.currentInterval)
        assertEquals(40.seconds, state.totalElapsed)
        assertEquals(Duration.ZERO, state.totalRemaining)
    }

    @Test
    fun testPauseBehavior() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        scheduler.musicPrepared()
        scheduler.start()
        
        scheduler.resync(5.seconds)
        scheduler.pauseResume() // This will capture elapsed time
        
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.PAUSED, state.status)
        // Allowing a tiny bit of drift between resync and pauseResume
        assertTrue(state.totalElapsed >= 5.seconds)
        assertTrue(state.totalElapsed < 5.1.seconds)
        
        assertEquals(WorkoutStatus.PAUSED, scheduler.status)
    }

    @Test
    fun testResumeBehavior() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        scheduler.musicPrepared()
        scheduler.start()
        scheduler.resync(5.seconds)
        scheduler.pauseResume() // PAUSED
        
        val pausedElapsed = scheduler.getState().totalElapsed
        scheduler.pauseResume() // RUNNING
        
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.RUNNING, state.status)
        assertTrue(state.totalElapsed >= pausedElapsed)
    }

    @Test
    fun testResetBehavior() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        scheduler.musicPrepared()
        scheduler.start()
        scheduler.resync(15.seconds)
        
        scheduler.reset()
        
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.NOT_STARTED, state.status)
        assertEquals(Duration.ZERO, state.totalElapsed)
        // Reset doesn't clear the workout itself in the current implementation
        assertEquals(workout, state.workout)
        // But status is NOT_STARTED, so interval index is recalculated from 0
        assertEquals(0, state.intervalIndex)
    }

    @Test
    fun testResyncForwardAndBackward() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        // Forward
        scheduler.resync(25.seconds)
        assertEquals(1, scheduler.getState().intervalIndex)
        
        // Backward
        scheduler.resync(5.seconds)
        assertEquals(0, scheduler.getState().intervalIndex)
    }

    @Test
    fun testAdjustTime() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        scheduler.resync(10.seconds)
        
        // Positive delta
        scheduler.adjustTime(5.seconds)
        assertEquals(15.seconds, scheduler.getState().totalElapsed)
        
        // Negative delta
        scheduler.adjustTime((-10).seconds)
        assertEquals(5.seconds, scheduler.getState().totalElapsed)
    }

    @Test
    fun testNegativeResync() {
        assertFailsWith<IllegalArgumentException> {
            scheduler.resync((-1).seconds)
        }
    }

    @Test
    fun testZeroDurationWorkout() {
        val workout = createSimpleWorkout(emptyList()) // totalDuration will be 0
        setWorkout(scheduler, workout)
        scheduler.musicPrepared()
        
        scheduler.start()
        
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.FINISHED, state.status)
        assertEquals(Duration.ZERO, state.totalElapsed)
        assertNull(state.intervalIndex)
    }

    @Test
    fun testEmptyWorkout() {
        // Workout with no intervals
        val workout = Workout(id = 2, name = "Empty", author = "Test", intervals = emptyList())
        setWorkout(scheduler, workout)
        
        assertEquals(0, workout.totalDuration)
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.NOT_STARTED, state.status)
        assertNull(state.intervalIndex)
        
        scheduler.musicPrepared()
        scheduler.start()
        assertEquals(WorkoutStatus.FINISHED, scheduler.getState().status)
    }

    @Test
    fun testPauseResumeEdgeCases() {
        // Before workout selection
        scheduler.pauseResume()
        assertEquals(WorkoutStatus.RUNNING, scheduler.status) // Current behavior: starts even if no workout!
        
        setup()
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        // NOT_STARTED
        assertEquals(WorkoutStatus.NOT_STARTED, scheduler.status)
        scheduler.pauseResume()
        assertEquals(WorkoutStatus.RUNNING, scheduler.status)
        
        // READY (via musicPrepared)
        setup()
        setWorkout(scheduler, workout)
        scheduler.musicPrepared()
        assertEquals(WorkoutStatus.READY, scheduler.status)
        scheduler.pauseResume()
        assertEquals(WorkoutStatus.RUNNING, scheduler.status)
        
        // FINISHED
        scheduler.resync(100.seconds)
        assertEquals(WorkoutStatus.FINISHED, scheduler.status)
        scheduler.pauseResume()
        assertEquals(WorkoutStatus.RUNNING, scheduler.status) // Resumes even if finished!
    }

    @Test
    fun testGetStateConsistency() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        val checkConsistency = { time: Duration ->
            scheduler.resync(time)
            val state = scheduler.getState()
            
            if (state.currentInterval != null) {
                val sumInterval = state.elapsedInInterval + state.remainingInInterval
                // Use tolerance for Double/Float precision issues if any, 
                // but here it's all seconds from Double.
                assertEquals(state.intervalLength.inWholeMilliseconds, sumInterval.inWholeMilliseconds, "Interval consistency at $time")
            }
            
            val sumTotal = state.totalElapsed + state.totalRemaining
            assertEquals(40.seconds.inWholeMilliseconds, sumTotal.inWholeMilliseconds, "Total consistency at $time")
        }
        
        checkConsistency(0.seconds)
        checkConsistency(5.seconds)
        checkConsistency(10.seconds)
        checkConsistency(25.seconds)
        checkConsistency(39.seconds)
        checkConsistency(40.seconds)
    }
}
