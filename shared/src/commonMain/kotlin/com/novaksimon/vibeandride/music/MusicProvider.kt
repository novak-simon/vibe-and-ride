package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.database.getCurrentUser
import com.novaksimon.vibeandride.database.incrementStat
import com.novaksimon.vibeandride.database.setStatDatetime
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.RecommendationEngine
import com.novaksimon.vibeandride.recommendation.RecommendationResult
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.workout.WorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class MusicProvider(val uiState: MutableStateFlow<MusicUiState>, audioPlayer: AudioPlayer) {
    private val service = UserMusicService(audioPlayer)
    private val recommender = RecommendationEngine(service)
    private var currentDebug: List<SongRecommendation> = emptyList()
    private var futureDebug: List<SongRecommendation> = emptyList()
    private var workout: Workout? = null
    private var recommendationBuffer: MutableList<RecommendationResult?> = mutableListOf()
    private var recommendationIndex: MutableList<Boolean> = mutableListOf()
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recommendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recommendSemaphore = Semaphore(4)
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    private var songIndex: Int = 0
    private var currentSong: Song? = null
    private var currentIntervalIndex: Int = -1
    private var startMark: TimeMark? = null

    fun workoutSelected(state: WorkoutState) {
        currentIntervalIndex = -1
        workout = state.workout
        var intervalCount = 0
        workout?.intervals?.let { intervalCount = workout!!.intervals!!.size }
        recommendationBuffer = MutableList(intervalCount) { null }
        recommendationIndex = MutableList(intervalCount) { false }
        println("Interval index on workoutSelected: ${state.intervalIndex}")
        if (state.intervalIndex != null) {
            precompute(state.currentInterval ?: return, state.intervalIndex)
            precompute(state.workout?.intervals?.get(state.intervalIndex + 1) ?: return, state.intervalIndex + 1)
        }
    }

    suspend fun play(intervalIndex: Int?) {
        if (intervalIndex == null) return
        println("interval index: $currentIntervalIndex, new interval index: $intervalIndex")
        if (currentIntervalIndex != intervalIndex) {
            currentDebug = futureDebug
            uiState.value = uiState.value.copy(debugInfo = currentDebug)
            currentIntervalIndex = intervalIndex
            songIndex = 0
            incrementStatsOnTrackLeave()
            nextTrack()
        }

        else if (service.approachingTrackEnd()) {
            println("Time for new song")
            incrementStatsOnTrackLeave()
            nextTrack()
        }
    }
    suspend fun incrementStatsOnTrackLeave(skipped: Boolean = false) {
        println("current song: $currentSong")
        if (skipped) {
            currentSong?.id?.let { incrementStat("skip_count",
                currentSong!!.id!!, userId = getCurrentUser()?.id ?: return) }
        }
        else {
            currentSong?.id?.let {
                incrementStat(
                    "play_count",
                    currentSong!!.id!!, userId = getCurrentUser()?.id ?: return
                )
            }
        }
        currentSong?.id.let { setStatDatetime("last_played", currentSong!!.id!!, userId = getCurrentUser()?.id ?: return) }
    }

    fun nextTrack() {
        println("Next track")
        println(recommendationBuffer)
        val song = recommendationBuffer[currentIntervalIndex]!!.topSongs[songIndex]
        uiState.value = uiState.value.copy(currentSong = song)
        currentSong = song
        val increment = service.playNext(song)
        songIndex += increment
    }

    suspend fun skip() {
        incrementStatsOnTrackLeave(skipped = true)
        nextTrack()
    }

    fun precomputeNextRecommendation(workoutState: WorkoutState) {
        if (workoutState.intervalIndex != null) {
            if (recommendationIndex[workoutState.intervalIndex + 1]) return
            precompute(workoutState.workout?.intervals?.get(workoutState.intervalIndex + 1) ?: return, workoutState.intervalIndex + 1,)
        }
    }

    fun precompute(interval: Interval, intervalIndex: Int) {
        recommendScope.launch {
            val recommendation = recommendSemaphore.withPermit {
                println("Recommending songs for interval: $intervalIndex")
                recommendationIndex[intervalIndex] = true
                recommender.recommend(interval)
            }

            recommendationBuffer[intervalIndex] = recommendation
            futureDebug = recommendation.debugInfo


            println("Recommendation complete")
            println(recommendation)

        }
    }
}
