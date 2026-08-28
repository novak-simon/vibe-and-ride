package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.workout.WorkoutMode
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import com.novaksimon.vibeandride.workout.StructuredState
import com.novaksimon.vibeandride.music.MusicState
import com.novaksimon.vibeandride.model.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationEngineV2Test {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private val fakeSongRepository = FakeSongRepository()
    private val fakeProfileRepository = FakeProfileRepository()
    
    private fun createEngine() = RecommendationEngineV2(
        songRepository = fakeSongRepository,
        profileRepository = fakeProfileRepository,
        scope = testScope,
        ioDispatcher = testDispatcher,
        defaultDispatcher = testDispatcher
    )
    
    @Test
    fun testInitialFetch() = runTest(testDispatcher) {
        val engine = createEngine()
        val sessionState = WorkoutSessionState(
            mode = WorkoutMode.FreeRide,
            status = WorkoutStatus.READY,
            totalElapsed = 0.seconds,
            currentCadence = 90.0
        )
        
        // sessionStarted should trigger fetch
        engine.sessionStarted(sessionState)
        
        assertNotNull(engine.state.value.current)
        assertNull(engine.state.value.future)
        assertEquals(0, engine.state.value.currentIntervalIndex)
    }

    @Test
    fun testFreeRideNoFutureFetch() = runTest(testDispatcher) {
        val engine = createEngine()
        val sessionState = WorkoutSessionState(
            mode = WorkoutMode.FreeRide,
            status = WorkoutStatus.RUNNING,
            totalElapsed = 0.seconds,
            currentCadence = 90.0
        )
        
        engine.sessionStarted(sessionState)
        testScheduler.advanceUntilIdle()
        
        // Even if we are "near end" (though FreeRide has no end in update logic), 
        // V2 should not fetch future.
        engine.update(sessionState, MusicState(mode = WorkoutMode.FreeRide), 10f)
        testScheduler.advanceUntilIdle()
        
        assertNull(engine.state.value.future, "FreeRide should never fetch a future interval")
    }
    
    @Test
    fun testStructuredWorkoutPrecomputeFuture() = runTest(testDispatcher) {
        val intervals = listOf(
            Interval(duration = 60.0, power = 0.5f, cadence = 90.0, type = "a"),
            Interval(duration = 60.0, power = 0.8f, cadence = 100.0, type = "b")
        )
        val workout = Workout(id = 1, name = "Test", author = "Author", intervals = intervals)
        
        val engine = createEngine()
        val sessionState = WorkoutSessionState(
            mode = WorkoutMode.Structured(workout),
            status = WorkoutStatus.RUNNING,
            totalElapsed = 10.seconds,
            structuredState = StructuredState(
                intervalIndex = 0,
                currentInterval = intervals[0],
                elapsedInInterval = 10.seconds,
                remainingInInterval = 50.seconds,
                totalRemaining = 110.seconds
            )
        )
        
        engine.sessionStarted(sessionState)
        engine.update(sessionState, MusicState(), 100f)
        
        assertNotNull(engine.state.value.current)
        assertNull(engine.state.value.future)
        
        // Approach interval end
        val nearEndState = sessionState.copy(
            structuredState = sessionState.structuredState?.copy(
                elapsedInInterval = 30.seconds,
                remainingInInterval = 30.seconds
            )
        )
        engine.update(nearEndState, MusicState(), 30f)
        
        assertNotNull(engine.state.value.future)
    }

    @Test
    fun testIntervalChangeShiftsBuffers() = runTest(testDispatcher) {
        val intervals = listOf(
            Interval(duration = 60.0, power = 0.5f, cadence = 90.0, type = "a"),
            Interval(duration = 60.0, power = 0.8f, cadence = 100.0, type = "b")
        )
        val workout = Workout(id = 1, name = "Test", author = "Author", intervals = intervals)
        
        val engine = createEngine()
        
        // 1. Initial state
        val s1 = WorkoutSessionState(
            mode = WorkoutMode.Structured(workout),
            status = WorkoutStatus.RUNNING,
            totalElapsed = 0.seconds,
            structuredState = StructuredState(
                intervalIndex = 0,
                currentInterval = intervals[0],
                elapsedInInterval = 0.seconds,
                remainingInInterval = 60.seconds,
                totalRemaining = 120.seconds
            )
        )
        engine.sessionStarted(s1)
        engine.update(s1, MusicState(), 60f)
        
        // 2. Precompute future
        val s2 = s1.copy(structuredState = s1.structuredState?.copy(
            elapsedInInterval = 30.seconds,
            remainingInInterval = 30.seconds
        ))
        engine.update(s2, MusicState(), 30f)
        val futureRec = engine.state.value.future
        assertNotNull(futureRec)
        
        // 3. Change interval
        val s3 = s1.copy(structuredState = s1.structuredState?.copy(
            intervalIndex = 1,
            currentInterval = intervals[1],
            elapsedInInterval = 0.seconds,
            remainingInInterval = 60.seconds,
            totalRemaining = 60.seconds
        ))
        engine.update(s3, MusicState(), 60f)
        
        assertEquals(1, engine.state.value.currentIntervalIndex)
        assertEquals(futureRec, engine.state.value.current)
        assertNull(engine.state.value.future)
    }
}
