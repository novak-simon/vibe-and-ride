package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.database.changeLikeState
import com.novaksimon.vibeandride.database.getStatByProfileAndSongId
import com.novaksimon.vibeandride.database.incrementStat
import com.novaksimon.vibeandride.database.setStatDatetime
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.RecommendationEngine
import com.novaksimon.vibeandride.recommendation.RecommendationResult
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.workout.WorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MusicProvider(val uiState: MutableStateFlow<MusicUiState>, playbackController: PlaybackController) {
    private val service = ProfileMusicService(playbackController)
    private val recommender = RecommendationEngine(service)
    private var currentDebug: List<SongRecommendation> = emptyList()
    private var futureDebug: List<SongRecommendation> = emptyList()
    private var workout: Workout? = null
    private var recommendationBuffer: MutableList<RecommendationResult?> = mutableListOf()
    private val recommendationIndex = MutableStateFlow<List<Boolean>>(emptyList())
    private val activePrecomputations = mutableSetOf<Int>()
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recommendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val waitScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val recommendSemaphore = Semaphore(4)
    private var songIndex: Int = 0
    private var currentSong: Song? = null
    private var futureIntervalSong: Song? = null
    private var currentIntervalIndex: Int = -1
    private var preloadedSongId: Int? = null
    private var hasPreloadedSong: Boolean = false
    private var isTransitioning = false

    init {
        waitScope.launch {
            service.isPlayingFlow.collect { isPlaying ->
                uiState.value = uiState.value.copy(isPlaying = isPlaying)
            }
        }
    }

    fun workoutSelected(state: WorkoutState) {
        currentIntervalIndex = -1
        preloadedSongId = null
        isTransitioning = false
        workout = state.workout
        var intervalCount = 0
        workout?.intervals?.let { intervalCount = workout!!.intervals.size }
        recommendationBuffer = MutableList(intervalCount) { null }
        activePrecomputations.clear()
        recommendationIndex.value = List(intervalCount) { false }
        println("Interval index on workoutSelected: ${state.intervalIndex}")
        if (state.intervalIndex != null) {
            precompute(state.currentInterval ?: return, state.intervalIndex)
        }
        waitScope.launch {
            val firstIndex = state.intervalIndex ?: return@launch
            println("Waiting for music availability for interval $firstIndex...")
            recommendationIndex.first { readyList ->
                val ready = readyList.getOrNull(firstIndex) == true
                if (ready) println("Music ready for interval $firstIndex")
                ready
            }
            println("MusicProvider: Music is available for interval $firstIndex, setting isMusicAvailable = true")
            uiState.value = uiState.value.copy(isMusicAvailable = true)
        }
    }

    suspend fun play(intervalIndex: Int?, remainingInterval: Duration) {
        if (intervalIndex == null || isTransitioning) return
        
        val timeUntilTrackEnd = service.timeUntilTrackEnd()
        val remainingInInterval = remainingInterval.inWholeMilliseconds.toFloat() / 1000f
        val remaining = min(remainingInInterval, timeUntilTrackEnd)

        // Preload logic
        if (remaining < 30f && !hasPreloadedSong) {
            val songToPreload: Song?
            if (remainingInInterval <= timeUntilTrackEnd + 5f) {
                // Interval is ending soon (or before this track ends), preload first song of next interval
                songIndex = 0
                songToPreload = recommendationBuffer.getOrNull(intervalIndex + 1)?.topSongs?.getOrNull(songIndex) ?: futureIntervalSong
            } else {
                // Track is ending, but interval still has time, preload next song in current interval
                songToPreload = recommendationBuffer.getOrNull(intervalIndex)?.topSongs?.getOrNull(songIndex)
            }

            if (songToPreload != null && songToPreload.id != preloadedSongId && songToPreload.id != currentSong?.id) {
                println("MusicProvider: preloading song ${songToPreload.title} (ID: ${songToPreload.id})")
                if (!service.preloadSong(songToPreload)){
                    songIndex += 1
                    return
                }
                hasPreloadedSong = true
                preloadedSongId = songToPreload.id
            }
        }

        // Transition logic
        if (remainingInInterval < 1f) {
            println("MusicProvider: switching to next track (new interval: true, index: ${intervalIndex + 1})")
            nextTrack(newInterval = true, intervalIndex = intervalIndex + 1)
        }
        else if (timeUntilTrackEnd < 1f) {
            println("MusicProvider: switching to next track (new interval: false)")
            nextTrack()
        }
        else if (currentIntervalIndex == -1) {
            println("MusicProvider: starting first track of workout for interval $intervalIndex")
            nextTrack(firstSong = true, newInterval = true, intervalIndex = intervalIndex)
        } else if (!service.isPlaying()) {
            nextTrack(newInterval = false)
        }
    }

    fun incrementStatsOnTrackLeave(skipped: Boolean = false) {
        val song = currentSong ?: return
        statsScope.launch {
            if (skipped) {
                incrementStat("skip_count", song.id)
                setStatDatetime("last_skipped_at", song.id)
            }
            incrementStat("play_count", song.id)
        }
    }
    fun incrementStatsOnTrackArrive() {
        val song = currentSong ?: return
        statsScope.launch {
            setStatDatetime("last_played_at", song.id)
        }
    }

    suspend fun nextTrack(newInterval: Boolean = false, intervalIndex: Int = 0, skipped: Boolean = false, firstSong: Boolean = false) {
        if (isTransitioning) return
        isTransitioning = true
        try {
            if (!firstSong) incrementStatsOnTrackLeave(skipped = skipped)
            if (newInterval) {
                println("INTERVAL CHANGED, new index: $intervalIndex")
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
            if (!service.crossfadeTo(song, 1000L)) {
                songIndex += 1
                println("Invalid URL in NEXT TRACK")
                isTransitioning = false
                return
            }
            statsScope.launch {
                val songStats: ProfileSongStats? = getStatByProfileAndSongId(songId = song.id)
                println("Current song: $song")
                uiState.value = uiState.value.copy(
                    currentSong = song,
                    isLiked = songStats?.isLiked ?: false,
                    isPlaying = true
                )
            }
            currentSong = song

            
            // Wait for crossfade to complete before allowing another transition
            delay(2.seconds)
            
            CoroutineScope(Dispatchers.Main).launch {
                delay(3.seconds)
                hasPreloadedSong = false
            }
            songIndex += 1
            incrementStatsOnTrackArrive()
            println("MusicProvider: transition to ${song.title} complete")
        } finally {
            isTransitioning = false
        }
    }

    suspend fun skip() {
        nextTrack(skipped = true)
    }
    fun songLiked(liked: Boolean = true) {
        val song = currentSong ?: return
        statsScope.launch {
            changeLikeState(songId = song.id, targetState = liked)
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
            val nextIndex = workoutState.intervalIndex + 1
            if (recommendationIndex.value.getOrNull(nextIndex) == true || activePrecomputations.contains(nextIndex)) return
            precompute(workoutState.workout?.intervals?.getOrNull(nextIndex) ?: return, nextIndex)
        }
    }

    fun precompute(interval: Interval, intervalIndex: Int) {
        activePrecomputations.add(intervalIndex)
        recommendScope.launch {
            val recommendation = recommendSemaphore.withPermit {
                println("Recommending songs for interval: $intervalIndex")
                recommender.recommend(interval)
            }

            recommendationBuffer[intervalIndex] = recommendation
            futureDebug = recommendation.debugInfo
            futureIntervalSong = recommendation.topSongs.firstOrNull()

            // Update recommendationIndex to signal readiness
            val currentReady = recommendationIndex.value.toMutableList()
            if (intervalIndex < currentReady.size) {
                currentReady[intervalIndex] = true
                recommendationIndex.value = currentReady
            }

            println("Recommendation complete for interval: $intervalIndex")
        }
    }
}
