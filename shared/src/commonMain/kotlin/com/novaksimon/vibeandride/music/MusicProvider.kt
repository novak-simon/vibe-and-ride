package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.database.changeLikeState
import com.novaksimon.vibeandride.database.getCurrentUser
import com.novaksimon.vibeandride.database.getStatByUserAndSongId
import com.novaksimon.vibeandride.database.incrementStat
import com.novaksimon.vibeandride.database.setStatDatetime
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.UserSongStats
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MusicProvider(val uiState: MutableStateFlow<MusicUiState>, playbackController: PlaybackController) {
    private val service = UserMusicService(playbackController)
    private val recommender = RecommendationEngine(service)
    private var currentDebug: List<SongRecommendation> = emptyList()
    private var futureDebug: List<SongRecommendation> = emptyList()
    private var workout: Workout? = null
    private var recommendationBuffer: MutableList<RecommendationResult?> = mutableListOf()
    private var recommendationIndex: MutableList<Boolean> = mutableListOf()
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recommendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recommendSemaphore = Semaphore(4)
    private var songIndex: Int = 0
    private var currentSong: Song? = null
    private var currentIntervalIndex: Int = -1

    fun workoutSelected(state: WorkoutState) {
        currentIntervalIndex = -1
        workout = state.workout
        var intervalCount = 0
        workout?.intervals?.let { intervalCount = workout!!.intervals.size }
        recommendationBuffer = MutableList(intervalCount) { null }
        recommendationIndex = MutableList(intervalCount) { false }
        println("Interval index on workoutSelected: ${state.intervalIndex}")
        if (state.intervalIndex != null) {
            precompute(state.currentInterval ?: return, state.intervalIndex)
        }
    }

    suspend fun play(intervalIndex: Int?, remainingInterval: Duration) {
        if (intervalIndex == null) return
        println("interval index: $currentIntervalIndex, new interval index: $intervalIndex")
        if (remainingInterval < 3.seconds) {
            println("Skipping to next track because remaining interval is less than 3 seconds")
            nextTrack(newInterval = true, intervalIndex)
        }
        else if (currentIntervalIndex != intervalIndex) {
            nextTrack(newInterval = true, intervalIndex)
        }

        else if (service.approachingTrackEnd()) {
            nextTrack()
        }
    }

    fun incrementStatsOnTrackLeave(skipped: Boolean = false) {
        statsScope.launch {
            val song = currentSong ?: return@launch
            val userId = getCurrentUser().id

            if (skipped) {
                incrementStat("skip_count", song.id, userId = userId)
                setStatDatetime("last_skipped_at", song.id, userId = userId)
            }
            incrementStat("play_count", song.id, userId = userId)

        }
    }
    fun incrementStatsOnTrackArrive() {
        statsScope.launch {
            val song = currentSong ?: return@launch
            val userId = getCurrentUser().id
            setStatDatetime("last_played_at", song.id, userId = userId)
        }
    }

    suspend fun nextTrack(newInterval: Boolean = false, intervalIndex: Int = 0, skipped: Boolean = false) {
        incrementStatsOnTrackLeave(skipped = skipped)
        if (newInterval) {
            currentDebug = futureDebug
            uiState.value = uiState.value.copy(debugInfo = currentDebug)
            currentIntervalIndex = intervalIndex
            songIndex = 0
        }
        val recommendation = recommendationBuffer.getOrNull(currentIntervalIndex) ?: run {
            println("No recommendation ready for interval $currentIntervalIndex")
            return
        }
        val song = recommendation.topSongs.getOrNull(songIndex) ?: run {
            println("No more songs in recommendation for interval $currentIntervalIndex (index $songIndex)")
            return
        }
        val songStats: UserSongStats? = getStatByUserAndSongId(songId = song.id, userId = getCurrentUser().id)
        uiState.value = uiState.value.copy(currentSong = song, isLiked = songStats?.isLiked ?: false)
        currentSong = song
        service.crossfadeTo(song, 1000L)
        songIndex += 1
        incrementStatsOnTrackArrive()
    }

    suspend fun skip() {
        nextTrack(skipped = true)
    }
    fun songLiked(liked: Boolean = true) {
        statsScope.launch {
            changeLikeState(songId = currentSong?.id ?: return@launch, userId = getCurrentUser().id, targetState = liked)
        }
    }

    fun pauseResume() {
        service.pauseResume()
    }

    fun play() {
        service.play()
    }

    fun pause() {
        service.pause()
    }

    fun precomputeNextRecommendation(workoutState: WorkoutState) {
        if (workoutState.intervalIndex != null) {
            if (recommendationIndex[workoutState.intervalIndex + 1]) return
            precompute(workoutState.workout?.intervals?.get(workoutState.intervalIndex + 1) ?: return, workoutState.intervalIndex + 1)
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
