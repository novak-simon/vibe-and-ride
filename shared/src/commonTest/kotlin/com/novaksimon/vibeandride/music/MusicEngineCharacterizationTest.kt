package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.recommendation.FakeProfileRepository
import com.novaksimon.vibeandride.recommendation.FakeSongRepository
import com.novaksimon.vibeandride.workout.StructuredState
import com.novaksimon.vibeandride.workout.WorkoutMode
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EngineCharacterizationFakePlaybackController : PlaybackController {
    var isPlayingVal = false
    override val isPlayingFlow: Flow<Boolean> = MutableStateFlow(isPlayingVal)

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errorFlow: Flow<String> = _errorFlow.asSharedFlow()

    fun emitError(message: String) {
        _errorFlow.tryEmit(message)
    }

    override fun play() { isPlayingVal = true }
    override fun pause() { isPlayingVal = false }
    override fun isPlaying(): Boolean = isPlayingVal

    var pos: Float? = 0f
    override fun positionSeconds(): Float? = pos

    var dur: Float? = 180f
    override fun durationSeconds(): Float? = dur

    val calls = mutableListOf<String>()

    override suspend fun setSource(song: Song, audioUrl: String, startTimeSeconds: Float) {
        calls.add("setSource(${song.id})")
    }
    override suspend fun preloadSource(song: Song, audioUrl: String) {
        calls.add("preloadSource(${song.id})")
    }
    override suspend fun crossfadeToPreloaded(song: Song, audioUrl: String, durationMs: Long) {
        calls.add("crossfadeToPreloaded(${song.id})")
    }
    override suspend fun clearPreloaded() {
        calls.add("clearPreloaded")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MusicEngineCharacterizationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val playbackController = EngineCharacterizationFakePlaybackController()
    private val profileRepository = FakeProfileRepository()
    private val songRepository = FakeSongRepository()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createEngine() = MusicEngine(
        playbackController = playbackController,
        profileRepository = profileRepository,
        songRepository = songRepository,
        mainDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
        defaultDispatcher = testDispatcher
    )

    private fun createSong(id: Int, title: String = "Song $id", bpm: Int = 100) = Song(
        id = id,
        title = title,
        artist = "Artist",
        releaseDate = "2024-01-01",
        genre = listOf("pop"),
        tags = listOf("upbeat"),
        duration = 180,
        bpm = bpm,
        startTime = 0f,
        endTime = 0f,
        image = "",
        shareUrl = ""
    )

    private fun createWorkout(intervals: List<Interval>) = Workout(
        id = 1,
        name = "Test Workout",
        author = "Test Author",
        intervals = intervals,
        sportType = true,
        tags = emptyList(),
        description = "Description"
    )

    private fun createInterval(duration: Double = 60.0, cadence: Double = 90.0) = Interval(
        duration = duration,
        power = 0.5f,
        cadence = cadence,
        type = "steady"
    )

    private fun createStructuredSessionState(
        workout: Workout,
        status: WorkoutStatus = WorkoutStatus.NOT_STARTED,
        intervalIndex: Int = 0
    ): WorkoutSessionState {
        val interval = workout.intervals[intervalIndex]
        return WorkoutSessionState(
            mode = WorkoutMode.Structured(workout),
            status = status,
            totalElapsed = 0.seconds,
            currentCadence = interval.cadence,
            currentPower = interval.power.toDouble(),
            structuredState = StructuredState(
                intervalIndex = intervalIndex,
                currentInterval = interval,
                elapsedInInterval = 0.seconds,
                remainingInInterval = interval.duration.seconds,
                totalRemaining = workout.totalDuration.seconds
            )
        )
    }

    @Test
    fun testInitialState() = runTest {
        val engine = createEngine()
        val state = engine.state.value

        assertNull(state.currentSong)
        assertEquals(0, state.recommendationIndex.size)
        assertEquals(PlaybackState.IDLE, state.playbackState)
    }

    @Test
    fun testSessionStarted_Structured() = runTest {
        val song = createSong(1)
        songRepository.songsAroundTargetCadenceResults.add(listOf(song))
        profileRepository.profile = Profile("p1", emptyList())

        val engine = createEngine()
        val intervals = listOf(createInterval(), createInterval())
        val workout = createWorkout(intervals)
        val sessionState = createStructuredSessionState(workout, WorkoutStatus.NOT_STARTED, 0)

        engine.sessionStarted(sessionState)

        var attempts = 0
        while (!engine.state.value.recommendationIndex.any { it } && attempts < 100) {
            delay(1.milliseconds)
            attempts++
        }

        assertTrue(engine.state.value.recommendationIndex[0], "Music should be available after precompute")
    }

    @Test
    fun testSessionStarted_Idempotent() = runTest {
        val song = createSong(1)
        songRepository.songsAroundTargetCadenceResults.add(listOf(song))
        profileRepository.profile = Profile("p1", emptyList())

        val engine = createEngine()
        val intervals = listOf(createInterval())
        val workout = createWorkout(intervals)
        val sessionState = createStructuredSessionState(workout, WorkoutStatus.NOT_STARTED, 0)

        // First call
        engine.sessionStarted(sessionState)
        
        // Wait for recommendation
        while (!engine.state.value.recommendationIndex.any { it }) delay(1.milliseconds)
        
        val bufferBefore = engine.state.value.recommendationBuffer.toList()
        assertTrue(bufferBefore[0] != null)

        // Second call with same state
        engine.sessionStarted(sessionState)
        
        // Buffer should NOT be cleared/reset
        val bufferAfter = engine.state.value.recommendationBuffer
        assertEquals(bufferBefore, bufferAfter, "Buffer should not be reset on redundant sessionStarted call")
    }

    @Test
    fun testSessionStarted_FreeRide() = runTest {
        val song = createSong(1)
        songRepository.songsAroundTargetCadenceResults.add(listOf(song))
        profileRepository.profile = Profile("p1", emptyList())

        val engine = createEngine()
        val sessionState = WorkoutSessionState(
            mode = WorkoutMode.FreeRide,
            status = WorkoutStatus.READY,
            totalElapsed = 0.seconds,
            currentCadence = 95.0
        )

        engine.sessionStarted(sessionState)

        var attempts = 0
        while (!engine.state.value.recommendationIndex.any { it } && attempts < 100) {
            delay(1.milliseconds)
            attempts++
        }

        assertTrue(engine.state.value.recommendationIndex[0], "Music should be available after precompute in FreeRide")
    }

    @Test
    fun testPrecompute_MultipleCalls() = runTest {
        val song1 = createSong(1)
        val song2 = createSong(2)
        songRepository.songsAroundTargetCadenceResults.add(listOf(song1))
        songRepository.songsAroundTargetCadenceResults.add(listOf(song2))
        profileRepository.profile = Profile("p1", emptyList())

        val engine = createEngine()
        val intervals = listOf(createInterval(), createInterval())
        val workout = createWorkout(intervals)
        
        engine.sessionStarted(createStructuredSessionState(workout))

        val interval = createInterval()
        engine.precompute(interval, 0)
        engine.precompute(interval, 1)

        var attempts = 0
        while (!engine.state.value.recommendationIndex.all { it } && attempts < 100) {
            delay(1.milliseconds)
            attempts++
        }

        assertTrue(engine.state.value.recommendationIndex[0])
        assertTrue(engine.state.value.recommendationIndex[1])
    }

    @Test
    fun testPrecomputeNextRecommendation() = runTest {
        val song1 = createSong(1)
        val song2 = createSong(2)
        songRepository.songsAroundTargetCadenceResults.add(listOf(song1))
        songRepository.songsAroundTargetCadenceResults.add(listOf(song2))
        profileRepository.profile = Profile("p1", emptyList())

        val engine = createEngine()
        val intervals = listOf(createInterval(), createInterval())
        val workout = createWorkout(intervals)
        
        val sessionState = createStructuredSessionState(workout, WorkoutStatus.RUNNING, 0)
        
        engine.sessionStarted(sessionState.copy(status = WorkoutStatus.NOT_STARTED))
        engine.precomputeNextRecommendation(sessionState)
        
        var attempts = 0
        while (engine.state.value.recommendationIndex.getOrNull(1) != true && attempts < 100) {
            delay(1.milliseconds)
            attempts++
        }
        assertTrue(engine.state.value.recommendationIndex[1])
    }

    @Test
    fun testPauseResume() = runTest {
        val engine = createEngine()
        
        engine.pauseResume()
        assertTrue(playbackController.isPlayingVal)
        
        engine.pauseResume()
        assertFalse(playbackController.isPlayingVal)
    }

    @Test
    fun testUpdate_NoMode() = runTest {
        val engine = createEngine()
        engine.update(WorkoutSessionState(mode = null, status = WorkoutStatus.NOT_STARTED, totalElapsed = 0.seconds))
        assertNull(engine.state.value.currentSong)
    }

    @Test
    fun testSongLiked_WithCurrentSong() = runTest {
        val song = createSong(1)
        val engine = createEngine()
        
        songRepository.songsAroundTargetCadenceResults.add(listOf(song))
        profileRepository.profile = Profile("p1", emptyList())
        
        val intervals = listOf(createInterval())
        val workout = createWorkout(intervals)
        engine.sessionStarted(createStructuredSessionState(workout))
        
        while (!engine.state.value.recommendationIndex.any { it }) delay(10.milliseconds)
        
        engine.nextTrack(newInterval = true, intervalIndex = 0, firstSong = true)
        
        assertEquals(song.id, engine.state.value.currentSong?.id)
        assertFalse(engine.state.value.isLiked)
        assertEquals(PlaybackState.PLAYING, engine.state.value.playbackState)
        
        engine.songLiked(true)
        assertTrue(engine.state.value.isLiked)
    }

    @Test
    fun testPlaybackErrorRecovery_RetryAndSkip() = runTest {
        val song1 = createSong(1)
        val song2 = createSong(2)
        songRepository.songsAroundTargetCadenceResults.add(listOf(song1, song2))
        profileRepository.profile = Profile("p1", emptyList())

        val engine = createEngine()
        val workout = createWorkout(listOf(createInterval()))
        engine.sessionStarted(createStructuredSessionState(workout))
        
        while (!engine.state.value.recommendationIndex.any { it }) delay(10.milliseconds)
        
        // Start song 1
        engine.nextTrack(newInterval = true, intervalIndex = 0, firstSong = true)
        assertEquals(1, engine.state.value.currentSong?.id)
        assertEquals(0, engine.state.value.playbackErrorCount)
        assertEquals(1, engine.state.value.songIndex)

        // Error 1 -> Retry
        playbackController.emitError("404")
        delay(10.milliseconds)
        assertEquals(1, engine.state.value.playbackErrorCount)
        assertEquals(1, engine.state.value.currentSong?.id, "Should still be song 1 after retry")
        assertEquals(1, engine.state.value.songIndex, "Song index should be back to 1")

        // Error 2 -> Retry
        playbackController.emitError("404")
        delay(10.milliseconds)
        assertEquals(2, engine.state.value.playbackErrorCount)

        // Error 3 -> Retry
        playbackController.emitError("404")
        delay(10.milliseconds)
        assertEquals(3, engine.state.value.playbackErrorCount)

        // Error 4 -> Skip
        playbackController.emitError("404")
        delay(10.milliseconds)
        
        // Should skip to song 2
        assertEquals(2, engine.state.value.currentSong?.id)
        assertEquals(0, engine.state.value.playbackErrorCount)
    }
}
