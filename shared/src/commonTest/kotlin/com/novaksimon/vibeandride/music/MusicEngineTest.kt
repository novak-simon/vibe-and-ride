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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.time.Duration.Companion.milliseconds
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
        val state = MusicState(playbackState = PlaybackState.PLAYING)
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
            mode = WorkoutMode.Structured(createWorkout()),
            playbackState = PlaybackState.PLAYING
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
            mode = WorkoutMode.Structured(workout),
            playbackState = PlaybackState.PLAYING
        )
        val session = createSessionState(mode = WorkoutMode.Structured(workout))

        val decision = engine.decidePreload(state, 10f, 20f, session)
        assertIs<PreloadDecision.Preload>(decision)
        assertEquals(song2, (decision as PreloadDecision.Preload).song)
        assertEquals(0, decision.newSongIndex)
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
        val state = MusicState(songIndex = 1, playbackState = PlaybackState.TRANSITIONING)
        val nextState = engine.finalizeTrackTransition(state, song)
        
        assertEquals(song, nextState.currentSong)
        assertEquals(2, nextState.songIndex)
        assertEquals(PlaybackState.PLAYING, nextState.playbackState)
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

    @Test
    fun testUpdate_NoPrematureSongIndexUpdateOnNextIntervalPreload() = runTest {
        val song1 = createSong(1, 180)
        val song2 = createSong(2, 180)
        val songRepository = FakeSongRepository()
        
        // Interval 0 recommendations
        songRepository.songsAroundTargetCadenceResults.add(listOf(song1))
        songRepository.songsAroundTargetCadenceResults.add(emptyList())
        songRepository.songsAroundTargetCadenceResults.add(emptyList())
        
        // Interval 1 recommendations
        songRepository.songsAroundTargetCadenceResults.add(listOf(song2))
        songRepository.songsAroundTargetCadenceResults.add(emptyList())
        songRepository.songsAroundTargetCadenceResults.add(emptyList())
        
        songRepository.fetchSongsResults.add(listOf(song1))
        songRepository.fetchSongsResults.add(listOf(song2))
        
        val playbackController = FakePlaybackController()
        
        val engine = MusicEngine(
            playbackController = playbackController,
            songRepository = songRepository,
            profileRepository = FakeProfileRepository(),
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )
        
        val intervals = listOf(
            com.novaksimon.vibeandride.model.Interval(60.0, 0.5f, 90.0, "s"),
            com.novaksimon.vibeandride.model.Interval(60.0, 0.5f, 90.0, "s")
        )
        val workout = createWorkout(intervals)
        
        val sessionState = createSessionState(
            mode = WorkoutMode.Structured(workout),
            structuredState = StructuredState(0, workout.intervals[0], 0.seconds, 60.seconds, 120.seconds)
        )
        engine.sessionStarted(sessionState)
        
        // Wait for all recommendations to be ready
        while (engine.state.value.recommendationIndex.size < 2 || !engine.state.value.recommendationIndex.all { it }) {
            engine.precomputeNextRecommendation(engine.state.value.mode?.let { createSessionState(mode = it, structuredState = StructuredState(0, intervals[0], 0.seconds, 60.seconds, 120.seconds)) } ?: sessionState)
            delay(1.milliseconds)
        }
        
        // Start first track
        engine.nextTrack(newInterval = true, intervalIndex = 0, firstSong = true)
        assertEquals(1, engine.state.value.songIndex)
        assertEquals(0, engine.state.value.currentIntervalIndex)

        // Trigger preload for next interval. 
        // We need remainingInInterval < 30s AND remainingInInterval <= timeUntilTrackEnd + 5s
        val nearEndSessionState = createSessionState(
            mode = WorkoutMode.Structured(workout),
            structuredState = StructuredState(0, workout.intervals[0], 55.seconds, 5.seconds, 65.seconds)
        )
        
        // Mock playback controller to return position near end of song 1 (180s duration)
        playbackController.currentPos = 175f
        
        // Call update. It should decide to preload from interval 1.
        engine.update(nearEndSessionState)
        
        println("PlaybackController calls: ${playbackController.calls}")
        assertEquals(PlaybackState.PRELOADING, engine.state.value.playbackState)
        assertEquals(2, engine.state.value.preloadedSongId)
        
        // CRITICAL CHECK: songIndex should still be 1 (pointing to next song in current interval if any, 
        // but here it's just the index that should NOT have been reset to 0 yet)
        assertEquals(1, engine.state.value.songIndex, "Song index should not be prematurely reset to 0")
        
        // Now trigger the transition to the next interval by reaching the end of interval 0
        val transitionSessionState = createSessionState(
            mode = WorkoutMode.Structured(workout),
            structuredState = StructuredState(0, workout.intervals[0], 60.seconds, 0.seconds, 60.seconds)
        )
        engine.update(transitionSessionState)
        
        // After transition, songIndex should be 1 (it was set to 0 in prepareForNextTrack then incremented in finalizeTrackTransition)
        assertEquals(PlaybackState.PLAYING, engine.state.value.playbackState)
        assertEquals(1, engine.state.value.songIndex)
        assertEquals(1, engine.state.value.currentIntervalIndex)
        assertEquals(2, engine.state.value.currentSong?.id)
    }
}
