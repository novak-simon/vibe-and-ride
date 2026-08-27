package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.FakePlaybackController
import com.novaksimon.vibeandride.recommendation.FakeProfileRepository
import com.novaksimon.vibeandride.recommendation.FakeSongRepository
import com.novaksimon.vibeandride.recommendation.RecommendationResult
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.workout.StructuredState
import com.novaksimon.vibeandride.workout.WorkoutMode
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MusicEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val engine = MusicEngine(
        playbackController = FakePlaybackController(),
        songRepository = FakeSongRepository(),
        profileRepository = FakeProfileRepository(),
        mainDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
        defaultDispatcher = testDispatcher
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSong(id: Int) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        releaseDate = "2024",
        genre = emptyList(),
        tags = emptyList(),
        duration = 180,
        bpm = 120,
        startTime = 0f,
        endTime = 0f,
        image = "",
        shareUrl = ""
    )

    private fun createSessionState(
        mode: WorkoutMode? = null,
        status: WorkoutStatus = WorkoutStatus.RUNNING,
        cadence: Double? = null,
        structuredState: StructuredState? = null
    ) = WorkoutSessionState(
        mode = mode,
        status = status,
        totalElapsed = 0.seconds,
        currentCadence = cadence,
        structuredState = structuredState
    )
    
    private fun createWorkout(intervals: List<com.novaksimon.vibeandride.model.Interval> = emptyList()) = 
        com.novaksimon.vibeandride.model.Workout(
            id = 1, name = "w", author = "a", sportType = true, tags = emptyList(), description = "", intervals = intervals
        )

    @Test
    fun testSelectSongFromBuffer_ValidIndices() {
        val song1 = createSong(1)
        val song2 = createSong(2)
        val rec0 = RecommendationResult(topSongs = listOf(song1), debugInfo = emptyList())
        val rec1 = RecommendationResult(topSongs = listOf(song2), debugInfo = emptyList())
        
        val state = MusicState(
            recommendationBuffer = listOf(rec0, rec1),
            mode = WorkoutMode.Structured(createWorkout())
        )

        assertEquals(song1, engine.selectSongFromBuffer(state, 0, 0))
        assertEquals(song2, engine.selectSongFromBuffer(state, 1, 0))
    }

    @Test
    fun testDecidePreload_NoPreloadNeeded() {
        val state = MusicState(hasPreloadedSong = false)
        val session = createSessionState()
        val decision = engine.decidePreload(state, 40f, 40f, session)
        assertIs<PreloadDecision.None>(decision)
    }

    @Test
    fun testDecidePreload_CurrentInterval() {
        val song1 = createSong(1)
        val song2 = createSong(2)
        val rec0 = RecommendationResult(topSongs = listOf(song1, song2), debugInfo = emptyList())
        val state = MusicState(
            currentIntervalIndex = 0,
            songIndex = 1,
            currentSong = song1,
            recommendationBuffer = listOf(rec0),
            mode = WorkoutMode.Structured(createWorkout())
        )
        val session = createSessionState()

        val decision = engine.decidePreload(state, 60f, 20f, session)
        assertIs<PreloadDecision.Preload>(decision)
        assertEquals(song2, (decision as PreloadDecision.Preload).song)
        assertNull(decision.newSongIndex)
    }

    @Test
    fun testDecidePreload_NextInterval() {
        val song1 = createSong(1)
        val song2 = createSong(2)
        val rec0 = RecommendationResult(topSongs = listOf(song1), debugInfo = emptyList())
        val rec1 = RecommendationResult(topSongs = listOf(song2), debugInfo = emptyList())
        val workout = createWorkout()
        val state = MusicState(
            currentIntervalIndex = 0,
            songIndex = 0,
            currentSong = song1,
            recommendationBuffer = listOf(rec0, rec1),
            mode = WorkoutMode.Structured(workout)
        )
        val session = createSessionState(mode = WorkoutMode.Structured(workout))

        val decision = engine.decidePreload(state, 10f, 20f, session)
        assertIs<PreloadDecision.Preload>(decision)
        assertEquals(song2, (decision as PreloadDecision.Preload).song)
        assertEquals(0, decision.newSongIndex)
    }

    @Test
    fun testDecideTransition_NoTransition() {
        val state = MusicState(currentIntervalIndex = 0, isTransitioning = false)
        val session = createSessionState()
        val decision = engine.decideTransition(state, session, 10f, 10f, true)
        assertIs<TransitionDecision.None>(decision)
    }

    @Test
    fun testDecideTransition_IntervalEnding() {
        val workout = createWorkout()
        val state = MusicState(currentIntervalIndex = 0, mode = WorkoutMode.Structured(workout))
        val session = createSessionState(mode = WorkoutMode.Structured(workout), structuredState = StructuredState(0, com.novaksimon.vibeandride.model.Interval(10.0, 0.5f, 90.0, "s"), 10.seconds, 0.seconds, 0.seconds))
        val decision = engine.decideTransition(state, session, 0.5f, 10f, true)
        assertIs<TransitionDecision.NextTrack>(decision)
        val next = decision as TransitionDecision.NextTrack
        assertTrue(next.newInterval)
        assertEquals(1, next.intervalIndex)
    }

    @Test
    fun testDecideTransition_TrackEnding() {
        val state = MusicState(currentIntervalIndex = 0)
        val session = createSessionState()
        val decision = engine.decideTransition(state, session, 10f, 0.5f, true)
        assertIs<TransitionDecision.NextTrack>(decision)
        val next = decision as TransitionDecision.NextTrack
        assertFalse(next.newInterval)
    }

    @Test
    fun testDecideTransition_FirstSong() {
        val state = MusicState(currentIntervalIndex = -1)
        val session = createSessionState(structuredState = StructuredState(0, com.novaksimon.vibeandride.model.Interval(10.0, 0.5f, 90.0, "s"), 0.seconds, 10.seconds, 10.seconds))
        val decision = engine.decideTransition(state, session, 60f, 3600f, true)
        assertIs<TransitionDecision.NextTrack>(decision)
        val next = decision as TransitionDecision.NextTrack
        assertTrue(next.firstSong)
        assertTrue(next.newInterval)
        assertEquals(0, next.intervalIndex)
    }

    @Test
    fun testDecideTransition_NotPlaying() {
        val state = MusicState(currentIntervalIndex = 0)
        val session = createSessionState()
        val decision = engine.decideTransition(state, session, 10f, 10f, false)
        assertIs<TransitionDecision.NextTrack>(decision)
        val next = decision as TransitionDecision.NextTrack
        assertFalse(next.newInterval)
    }

    @Test
    fun testPrepareForNextTrack_NewInterval() {
        val debug = listOf(SongRecommendation(createSong(1), 1.0f))
        val state = MusicState(futureDebug = debug, currentIntervalIndex = 0, songIndex = 5, mode = WorkoutMode.Structured(createWorkout()))
        val nextState = engine.prepareForNextTrack(state, true, 1)
        
        assertEquals(debug, nextState.currentDebug)
        assertEquals(1, nextState.currentIntervalIndex)
        assertEquals(0, nextState.songIndex)
    }

    @Test
    fun testPrepareForNextTrack_FreeRide() {
        val state = MusicState(currentIntervalIndex = 0, songIndex = 5, mode = WorkoutMode.FreeRide)
        val nextState = engine.prepareForNextTrack(state, false, 0)
        
        assertEquals(0, nextState.currentIntervalIndex)
        assertEquals(5, nextState.songIndex)
    }

    @Test
    fun testFinalizeTrackTransition() {
        val song = createSong(2)
        val state = MusicState(songIndex = 1)
        val nextState = engine.finalizeTrackTransition(state, song)
        
        assertEquals(song, nextState.currentSong)
        assertEquals(2, nextState.songIndex)
    }

    @Test
    fun testDecidePrecompute_Needed() {
        val intervals = listOf(
            com.novaksimon.vibeandride.model.Interval(60.0, 0.5f, 90.0, "steady"),
            com.novaksimon.vibeandride.model.Interval(60.0, 0.5f, 90.0, "steady")
        )
        val state = MusicState(
            workout = createWorkout(intervals),
            recommendationIndex = listOf(true, false)
        )
        val index = engine.decidePrecompute(state, 0, emptySet())
        assertEquals(1, index)
    }

    @Test
    fun testDecidePrecompute_AlreadyReady() {
        val state = MusicState(recommendationIndex = listOf(true, true))
        val index = engine.decidePrecompute(state, 0, emptySet())
        assertNull(index)
    }

    @Test
    fun testDecidePrecompute_AlreadyActive() {
        val state = MusicState(recommendationIndex = listOf(true, false))
        val index = engine.decidePrecompute(state, 0, setOf(1))
        assertNull(index)
    }

    @Test
    fun testDecidePrecompute_NoMoreIntervals() {
        val intervals = listOf(com.novaksimon.vibeandride.model.Interval(60.0, 0.5f, 90.0, "steady"))
        val state = MusicState(
            workout = createWorkout(intervals),
            recommendationIndex = listOf(true)
        )
        val index = engine.decidePrecompute(state, 0, emptySet())
        assertNull(index)
    }
}
