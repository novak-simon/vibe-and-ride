package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.recommendation.FakeProfileRepository
import com.novaksimon.vibeandride.recommendation.FakeSongRepository
import com.novaksimon.vibeandride.workout.WorkoutState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    override fun play() { isPlayingVal = true }
    override fun pause() { isPlayingVal = false }
    override fun isPlaying(): Boolean = isPlayingVal

    var pos: Float? = 0f
    override fun positionSeconds(): Float? = pos

    var dur: Float? = 180f
    override fun durationSeconds(): Float? = dur

    val calls = mutableListOf<String>()

    override fun setSource(song: Song, audioUrl: String, startTimeSeconds: Float) {
        calls.add("setSource(${song.id})")
    }
    override fun preloadSource(song: Song, audioUrl: String) {
        calls.add("preloadSource(${song.id})")
    }
    override fun crossfadeToPreloaded(song: Song, audioUrl: String, durationMs: Long) {
        calls.add("crossfadeToPreloaded(${song.id})")
    }
    override fun clearPreloaded() {
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
        description = "Description"
    )

    private fun createInterval(duration: Double = 60.0, cadence: Double = 90.0) = Interval(
        duration = duration,
        power = 0.5f,
        cadence = cadence,
        type = "steady"
    )

    private fun createWorkoutState(
        workout: Workout? = null,
        status: WorkoutStatus = WorkoutStatus.NOT_STARTED,
        intervalIndex: Int? = null,
        currentInterval: Interval? = null
    ) = WorkoutState(
        workout = workout,
        status = status,
        intervalIndex = intervalIndex,
        currentInterval = currentInterval,
        elapsedInInterval = 0.seconds,
        remainingInInterval = 60.seconds,
        intervalLength = 60.seconds,
        totalElapsed = 0.seconds,
        totalRemaining = 120.seconds
    )

    @Test
    fun testInitialState() = runTest {
        val engine = createEngine()
        val state = engine.state.value

        assertNull(state.currentSong)
        assertEquals(0, state.recommendationIndex.size)
        assertFalse(state.isTransitioning)
    }

    @Test
    fun testWorkoutSelected_ValidInterval() = runTest {
        val song = createSong(1)
        songRepository.songsAroundTargetCadenceResults.add(listOf(song))
        profileRepository.profile = Profile("p1", emptyList())

        val engine = createEngine()
        val intervals = listOf(createInterval(), createInterval())
        val workout = createWorkout(intervals)
        val state = createWorkoutState(
            workout = workout,
            status = WorkoutStatus.NOT_STARTED,
            intervalIndex = 0,
            currentInterval = intervals[0]
        )

        engine.workoutSelected(state)

        // With UnconfinedTestDispatcher, it should be ready immediately or very quickly
        var attempts = 0
        while (!engine.state.value.recommendationIndex.any { it } && attempts < 100) {
            delay(1.milliseconds)
            attempts++
        }

        assertTrue(engine.state.value.recommendationIndex[0], "Music should be available after precompute")
    }

    @Test
    fun testWorkoutSelected_NullIntervalIndex() = runTest {
        val engine = createEngine()
        val intervals = listOf(createInterval())
        val workout = createWorkout(intervals)
        val state = createWorkoutState(
            workout = workout,
            status = WorkoutStatus.NOT_STARTED,
            intervalIndex = null,
            currentInterval = null
        )

        engine.workoutSelected(state)

        // Should not trigger precompute, and recommendationIndex should stay empty or false
        delay(10.milliseconds)
        assertFalse(engine.state.value.recommendationIndex.any { it })
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
        
        // Need to set workout first so buffers are initialized
        engine.workoutSelected(createWorkoutState(workout = workout))

        val interval = createInterval()
        engine.precompute(interval, 0)
        engine.precompute(interval, 1)

        // Wait for precompute
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
        
        val state = createWorkoutState(
            workout = workout,
            status = WorkoutStatus.RUNNING,
            intervalIndex = 0,
            currentInterval = intervals[0]
        )
        
        engine.workoutSelected(state.copy(status = WorkoutStatus.NOT_STARTED))
        engine.precomputeNextRecommendation(state)
        
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
    fun testPlay_NoIntervalIndex() = runTest {
        val engine = createEngine()
        engine.play(null, 60.seconds)
        // Should do nothing
        assertNull(engine.state.value.currentSong)
    }

    @Test
    fun testNextTrack_InvalidInterval() = runTest {
        val engine = createEngine()
        // currentIntervalIndex is -1. nextTrack(newInterval = false) uses currentIntervalIndex.
        engine.nextTrack(newInterval = false)
        assertNull(engine.state.value.currentSong)
    }

    @Test
    fun testSongLiked_NoCurrentSong() = runTest {
        val engine = createEngine()
        engine.songLiked(true)
        // Should not crash, just returns
    }

    @Test
    fun testSongLiked_WithCurrentSong() = runTest {
        val song = createSong(1)
        val engine = createEngine()
        
        // Use reflection or a way to set state? 
        // Better: select workout and wait for recommendation, then nextTrack
        songRepository.songsAroundTargetCadenceResults.add(listOf(song))
        profileRepository.profile = Profile("p1", emptyList())
        
        val workout = createWorkout(listOf(createInterval()))
        engine.workoutSelected(createWorkoutState(workout = workout, intervalIndex = 0))
        
        // Wait for precompute
        while (!engine.state.value.recommendationIndex.any { it }) delay(10.milliseconds)
        
        engine.nextTrack(newInterval = true, intervalIndex = 0, firstSong = true)
        
        assertEquals(song.id, engine.state.value.currentSong?.id)
        assertFalse(engine.state.value.isLiked)
        
        engine.songLiked(true)
        assertTrue(engine.state.value.isLiked)
    }

    @Test
    fun testPlayPauseDelegation() = runTest {
        val engine = createEngine()
        
        engine.play()
        assertTrue(playbackController.isPlayingVal)
        
        engine.pause()
        assertFalse(playbackController.isPlayingVal)
        
        engine.pauseResume()
        assertTrue(playbackController.isPlayingVal)
    }
}
