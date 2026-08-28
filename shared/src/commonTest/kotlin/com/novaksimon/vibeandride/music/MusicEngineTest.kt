package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.FakePlaybackController
import com.novaksimon.vibeandride.recommendation.FakeProfileRepository
import com.novaksimon.vibeandride.recommendation.FakeSongRepository
import com.novaksimon.vibeandride.workout.StructuredState
import com.novaksimon.vibeandride.workout.WorkoutMode
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MusicEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    
    private val fakeSongRepository = FakeSongRepository()
    private val fakeProfileRepository = FakeProfileRepository()
    private val fakePlaybackController = FakePlaybackController()

    private val engine = MusicEngine(
        playbackController = fakePlaybackController,
        songRepository = fakeSongRepository,
        profileRepository = fakeProfileRepository,
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

    private fun createSong(id: Int, bpm: Int = 120) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        releaseDate = "2024",
        genre = emptyList(),
        tags = emptyList(),
        duration = 180,
        bpm = bpm,
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
    fun testDecidePreload_NoPreloadNeeded() {
        val state = MusicState(playbackState = PlaybackState.PLAYING)
        val session = createSessionState()
        val decision = engine.decidePreload(state, 40f, 40f, session)
        assertIs<PreloadDecision.None>(decision)
    }

    @Test
    fun testDecideTransition_NoTransition() {
        val state = MusicState(currentIntervalIndex = 0, playbackState = PlaybackState.PLAYING)
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
        val decision = engine.decideTransition(state, session, 60f, 86400f, true)
        assertIs<TransitionDecision.NextTrack>(decision)
        val next = decision as TransitionDecision.NextTrack
        assertTrue(next.firstSong)
        assertTrue(next.newInterval)
        assertEquals(0, next.intervalIndex)
    }

    @Test
    fun testPrepareForNextTrack_NewInterval() {
        val state = MusicState(currentIntervalIndex = 0, songIndex = 5, mode = WorkoutMode.Structured(createWorkout()))
        val nextState = engine.prepareForNextTrack(state, true, 1)
        
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
        val state = MusicState(songIndex = 1, playbackState = PlaybackState.TRANSITIONING)
        val nextState = engine.finalizeTrackTransition(state, song)
        
        assertEquals(song, nextState.currentSong)
        assertEquals(2, nextState.songIndex)
        assertEquals(PlaybackState.PLAYING, nextState.playbackState)
    }

    @Test
    fun testUpdate_IntegrationWithV2() = runTest(testDispatcher) {
        val song1 = createSong(1)
        fakeSongRepository.songsAroundTargetCadenceResults.add(listOf(song1))
        fakeSongRepository.fetchSongsResults.add(listOf(song1))
        
        val workout = createWorkout(listOf(com.novaksimon.vibeandride.model.Interval(60.0, 0.5f, 90.0, "s")))
        val sessionState = createSessionState(
            mode = WorkoutMode.Structured(workout),
            structuredState = StructuredState(0, workout.intervals[0], 0.seconds, 60.seconds, 60.seconds)
        )
        
        engine.sessionStarted(sessionState)
        
        // Wait for V2 to fetch
        engine.update(sessionState)
        
        assertNotNull(engine.state.value.recommendationV2.current)
        
        // Start track
        engine.nextTrack(newInterval = true, intervalIndex = 0, firstSong = true)
        assertEquals(song1.id, engine.state.value.currentSong?.id)
        assertEquals(0, engine.state.value.currentIntervalIndex)
    }
}
