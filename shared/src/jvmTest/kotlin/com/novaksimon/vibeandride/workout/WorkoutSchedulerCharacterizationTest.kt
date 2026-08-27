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
        scheduler.mode = WorkoutMode.Structured(workout)
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
            intervals = intervals,
            sportType = true,
            tags = emptyList(),
            description = ""
        )
    }

    @Test
    fun testInitialState() {
        val state = scheduler.getState()
        assertNull(state.mode)
        assertEquals(WorkoutStatus.NOT_STARTED, state.status)
        assertNull(state.structuredState)
        assertEquals(Duration.ZERO, state.totalElapsed)
        assertNull(state.currentCadence)
        assertNull(state.currentPower)
    }

    @Test
    fun testWorkoutSelection() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        val state = scheduler.getState()
        assertTrue(state.mode is WorkoutMode.Structured)
        assertEquals(workout, (state.mode).workout)
        assertEquals(WorkoutStatus.NOT_STARTED, state.status)
        assertEquals(0, state.structuredState?.intervalIndex)
        assertEquals(workout.intervals[0], state.structuredState?.currentInterval)
        assertEquals(40.seconds, state.structuredState?.totalRemaining)
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
        assertEquals(0, state.structuredState?.intervalIndex)
    }

    @Test
    fun testFreeRideMode() {
        scheduler.startFreeRide()
        assertEquals(WorkoutMode.FreeRide, scheduler.mode)
        assertEquals(WorkoutStatus.READY, scheduler.status)
        
        scheduler.start()
        assertEquals(WorkoutStatus.RUNNING, scheduler.status)
        
        scheduler.updateLiveCadence(85.0)
        scheduler.updateLivePower(150.0)
        
        val state = scheduler.getState()
        assertEquals(85.0, state.currentCadence)
        assertEquals(150.0, state.currentPower)
        assertNull(state.structuredState)
    }

    @Test
    fun testIntervalProgression() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        // Interval 1: 0-10s
        scheduler.resync(5.seconds)
        var state = scheduler.getState()
        assertEquals(0, state.structuredState?.intervalIndex)
        assertEquals(5.seconds, state.structuredState?.elapsedInInterval)
        
        // Interval 2: 10-30s
        scheduler.resync(15.seconds)
        state = scheduler.getState()
        assertEquals(1, state.structuredState?.intervalIndex)
        assertEquals(5.seconds, state.structuredState?.elapsedInInterval) // 15 - 10
        
        // Interval 3: 30-40s
        scheduler.resync(35.seconds)
        state = scheduler.getState()
        assertEquals(2, state.structuredState?.intervalIndex)
        assertEquals(5.seconds, state.structuredState?.elapsedInInterval) // 35 - 30
        
        // After final interval
        scheduler.resync(45.seconds)
        state = scheduler.getState()
        assertNull(state.structuredState)
        assertEquals(WorkoutStatus.FINISHED, state.status)
    }

    @Test
    fun testEndOfWorkout() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        // Exactly at end (40.0s)
        scheduler.resync(40.0.seconds)
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.FINISHED, state.status)
        assertNull(state.structuredState)
        assertEquals(40.seconds, state.totalElapsed)
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
        assertNull(state.mode)
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
    fun testEmptyWorkout() {
        val workout = Workout(id = 2, name = "Empty", author = "Test", intervals = emptyList(), sportType = true, tags = emptyList(), description = "")
        setWorkout(scheduler, workout)
        
        assertEquals(0, workout.totalDuration)
        val state = scheduler.getState()
        assertEquals(WorkoutStatus.NOT_STARTED, state.status)
        assertNull(state.structuredState)
        
        scheduler.musicPrepared()
        scheduler.start()
        assertEquals(WorkoutStatus.FINISHED, scheduler.getState().status)
    }

    @Test
    fun testGetStateConsistency() {
        val workout = createSimpleWorkout()
        setWorkout(scheduler, workout)
        
        val checkConsistency = { time: Duration ->
            scheduler.resync(time)
            val state = scheduler.getState()
            
            if (state.structuredState != null) {
                val sumInterval = state.structuredState.elapsedInInterval + state.structuredState.remainingInInterval
                assertEquals(state.structuredState.currentInterval.duration.seconds.inWholeMilliseconds, sumInterval.inWholeMilliseconds, "Interval consistency at $time")
            }
        }
        
        checkConsistency(0.seconds)
        checkConsistency(5.seconds)
        checkConsistency(10.seconds)
        checkConsistency(25.seconds)
        checkConsistency(39.seconds)
        checkConsistency(40.seconds)
    }
}
