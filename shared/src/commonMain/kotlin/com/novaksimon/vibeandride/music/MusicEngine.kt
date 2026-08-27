package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.ProfileRepository
import com.novaksimon.vibeandride.recommendation.RecommendationEngine
import com.novaksimon.vibeandride.recommendation.RecommendationResult
import com.novaksimon.vibeandride.recommendation.SongRepository
import com.novaksimon.vibeandride.workout.WorkoutMode
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * MusicEngine is the central orchestrator for music sessions.
 */
class MusicEngine(
    playbackController: PlaybackController,
    private val songRepository: SongRepository,
    private val profileRepository: ProfileRepository,
    private val mainDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) {
    private val service = ProfileMusicService(playbackController, songRepository)
    private val recommender = RecommendationEngine(
        songRepository, 
        profileRepository,
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher
    )
    
    private val engineScope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val statsScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val recommendScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val recommendSemaphore = Semaphore(4)
    private val activePrecomputations = mutableSetOf<Int>()

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    fun sessionStarted(sessionState: WorkoutSessionState) {
        val mode = sessionState.mode
        if (mode == _state.value.mode && _state.value.recommendationBuffer.isNotEmpty()) {
            return
        }

        val workout = if (mode is WorkoutMode.Structured) mode.workout else null
        val intervalCount = workout?.intervals?.size ?: 1 // At least 1 for FreeRide

        _state.value = _state.value.copy(
            mode = mode,
            workout = workout,
            currentIntervalIndex = -1,
            preloadedSongId = null,
            isTransitioning = false,
            recommendationBuffer = List(intervalCount) { null },
            recommendationIndex = List(intervalCount) { false }
        )
        activePrecomputations.clear()
        
        if (mode is WorkoutMode.Structured) {
            val startIndex = sessionState.structuredState?.intervalIndex ?: 0
            precompute(mode.workout.intervals[startIndex], startIndex)
        } else if (mode is WorkoutMode.FreeRide) {
            // Precompute initial recommendation for FreeRide based on current (or default) cadence
            precomputeFreeRide(sessionState.currentCadence ?: 90.0)
        }
    }

    suspend fun update(sessionState: WorkoutSessionState) {
        if (_state.value.isTransitioning) return

        val timeUntilTrackEnd = service.timeUntilTrackEnd()
        val remainingInInterval = sessionState.structuredState?.remainingInInterval?.inWholeMilliseconds?.toFloat()?.div(1000f) ?: Float.MAX_VALUE

        // Preload logic
        val preloadDecision = decidePreload(_state.value, remainingInInterval, timeUntilTrackEnd, sessionState)
        if (preloadDecision is PreloadDecision.Preload) {
            val songToPreload = preloadDecision.song
            if (preloadDecision.newSongIndex != null) {
                _state.value = _state.value.copy(songIndex = preloadDecision.newSongIndex)
            }

            println("MusicEngine: preloading song ${songToPreload.title}")
            if (!service.preloadSong(songToPreload)) {
                _state.value = _state.value.copy(songIndex = _state.value.songIndex + 1)
                return
            }
            _state.value = _state.value.copy(hasPreloadedSong = true, preloadedSongId = songToPreload.id)
        }

        // Transition logic
        val transitionDecision = decideTransition(
            state = _state.value,
            sessionState = sessionState,
            remainingInInterval = remainingInInterval,
            timeUntilTrackEnd = timeUntilTrackEnd,
            isPlaying = service.isPlaying()
        )

        if (transitionDecision is TransitionDecision.NextTrack) {
            nextTrack(
                newInterval = transitionDecision.newInterval,
                intervalIndex = transitionDecision.intervalIndex,
                firstSong = transitionDecision.firstSong
            )
        }
    }

    suspend fun nextTrack(
        newInterval: Boolean = false,
        intervalIndex: Int = 0,
        skipped: Boolean = false,
        firstSong: Boolean = false
    ) {
        if (_state.value.isTransitioning) return
        _state.value = _state.value.copy(isTransitioning = true)
        try {
            if (!firstSong) incrementStatsOnTrackLeave(skipped = skipped)

            _state.value = prepareForNextTrack(_state.value, newInterval, intervalIndex)
            
            val song = selectSongFromBuffer(_state.value, _state.value.currentIntervalIndex, _state.value.songIndex) ?: run {
                println("MusicEngine: No recommendation ready for index ${_state.value.currentIntervalIndex}")
                return
            }
            
            if (!service.crossfadeTo(song, 1000L)) {
                _state.value = _state.value.copy(songIndex = _state.value.songIndex + 1, isTransitioning = false)
                delay(1.seconds)
                nextTrack(newInterval = false, intervalIndex = _state.value.currentIntervalIndex)
                return
            }

            _state.value = finalizeTrackTransition(_state.value, song)

            statsScope.launch {
                val songStats: ProfileSongStats? = profileRepository.getStatByProfileAndSongId(songId = song.id)
                _state.value = _state.value.copy(isLiked = songStats?.isLiked ?: false)
            }

            delay(2.seconds)
            engineScope.launch {
                delay(3.seconds)
                _state.value = _state.value.copy(hasPreloadedSong = false)
            }
            incrementStatsOnTrackArrive()
        } finally {
            _state.value = _state.value.copy(isTransitioning = false)
        }
    }

    fun skip() {
        engineScope.launch {
            nextTrack(skipped = true)
        }
    }

    fun songLiked(liked: Boolean = true) {
        val song = _state.value.currentSong ?: return
        _state.value = _state.value.copy(isLiked = liked)
        statsScope.launch {
            profileRepository.changeLikeState(songId = song.id, targetState = liked)
        }
    }

    fun pauseResume() { service.pauseResume() }
    fun play() { service.play() }
    fun pause() { service.pause() }

    fun precomputeNextRecommendation(sessionState: WorkoutSessionState) {
        if (sessionState.mode is WorkoutMode.Structured) {
            val nextIndex = decidePrecompute(_state.value, sessionState.structuredState?.intervalIndex, activePrecomputations)
            if (nextIndex != null) {
                val interval = sessionState.mode.workout.intervals.getOrNull(nextIndex)
                if (interval != null) precompute(interval, nextIndex)
            }
        }
    }

    fun precompute(interval: Interval, intervalIndex: Int) {
        activePrecomputations.add(intervalIndex)
        recommendScope.launch {
            val recommendation = recommendSemaphore.withPermit {
                recommender.recommend(interval)
            }
            updateBuffer(intervalIndex, recommendation)
        }
    }

    fun precomputeFreeRide(cadence: Double) {
        val interval = Interval(duration = 3600.0, power = 0.5f, cadence = cadence, type = "FreeRide")
        recommendScope.launch {
            val recommendation = recommendSemaphore.withPermit {
                recommender.recommend(interval)
            }
            updateBuffer(0, recommendation)
        }
    }

    private fun updateBuffer(index: Int, recommendation: RecommendationResult) {
        val currentBuffer = _state.value.recommendationBuffer.toMutableList()
        if (index < currentBuffer.size) currentBuffer[index] = recommendation

        val currentReady = _state.value.recommendationIndex.toMutableList()
        if (index < currentReady.size) currentReady[index] = true

        _state.value = _state.value.copy(
            recommendationBuffer = currentBuffer,
            futureDebug = recommendation.debugInfo,
            futureIntervalSong = recommendation.topSongs.getOrNull(0),
            recommendationIndex = currentReady
        )
    }

    private fun incrementStatsOnTrackLeave(skipped: Boolean = false) {
        val song = _state.value.currentSong ?: return
        statsScope.launch {
            if (skipped) {
                profileRepository.incrementStat("skip_count", song.id)
                profileRepository.setStatDatetime("last_skipped_at", song.id)
            }
            profileRepository.incrementStat("play_count", song.id)
        }
    }

    private fun incrementStatsOnTrackArrive() {
        val song = _state.value.currentSong ?: return
        statsScope.launch {
            profileRepository.setStatDatetime("last_played_at", song.id)
        }
    }

    fun selectSongFromBuffer(state: MusicState, intervalIndex: Int, songIndex: Int): Song? {
        val actualIndex = if (state.mode is WorkoutMode.FreeRide) 0 else intervalIndex
        if (actualIndex < 0) return null
        val recommendation = state.recommendationBuffer.getOrNull(actualIndex) ?: return null
        return recommendation.topSongs.getOrNull(songIndex)
    }

    fun decidePreload(
        state: MusicState,
        remainingInInterval: Float,
        timeUntilTrackEnd: Float,
        sessionState: WorkoutSessionState
    ): PreloadDecision {
        val remaining = min(remainingInInterval, timeUntilTrackEnd)
        
        if (remaining < 30f && !state.hasPreloadedSong) {
            val songToPreload = if (state.mode is WorkoutMode.FreeRide) {
                selectSongFromBuffer(state, 0, state.songIndex)
            } else if (remainingInInterval <= timeUntilTrackEnd + 5f) {
                selectSongFromBuffer(state, state.currentIntervalIndex + 1, 0) ?: state.futureIntervalSong
            } else {
                selectSongFromBuffer(state, state.currentIntervalIndex, state.songIndex)
            }

            val newSongIndex = if (state.mode is WorkoutMode.Structured && remainingInInterval <= timeUntilTrackEnd + 5f) 0 else null

            if (songToPreload != null && songToPreload.id != state.preloadedSongId && songToPreload.id != state.currentSong?.id) {
                return PreloadDecision.Preload(songToPreload, newSongIndex)
            }
        }
        return PreloadDecision.None
    }

    fun decideTransition(
        state: MusicState,
        sessionState: WorkoutSessionState,
        remainingInInterval: Float,
        timeUntilTrackEnd: Float,
        isPlaying: Boolean
    ): TransitionDecision {
        if (state.isTransitioning) return TransitionDecision.None

        if (state.mode is WorkoutMode.Structured && remainingInInterval < 1f) {
            return TransitionDecision.NextTrack(newInterval = true, intervalIndex = (sessionState.structuredState?.intervalIndex ?: 0) + 1)
        }

        if (timeUntilTrackEnd < 1f) {
            return TransitionDecision.NextTrack(newInterval = false)
        }

        if (state.currentIntervalIndex == -1) {
            return TransitionDecision.NextTrack(
                firstSong = true, 
                newInterval = true, 
                intervalIndex = sessionState.structuredState?.intervalIndex ?: 0
            )
        }

        if (!isPlaying && state.currentSong == null) {
            return TransitionDecision.NextTrack(newInterval = false)
        }

        return TransitionDecision.None
    }

    fun prepareForNextTrack(state: MusicState, newInterval: Boolean, intervalIndex: Int): MusicState {
        return if (newInterval || state.mode is WorkoutMode.FreeRide) {
            state.copy(
                currentDebug = state.futureDebug,
                currentIntervalIndex = if (state.mode is WorkoutMode.FreeRide) 0 else intervalIndex,
                songIndex = if (newInterval) 0 else state.songIndex
            )
        } else state
    }

    fun finalizeTrackTransition(state: MusicState, song: Song): MusicState {
        return state.copy(currentSong = song, songIndex = state.songIndex + 1)
    }

    fun decidePrecompute(state: MusicState, currentIntervalIndex: Int?, activePrecomputations: Set<Int>): Int? {
        if (currentIntervalIndex == null) return null
        val nextIndex = currentIntervalIndex + 1
        if (state.recommendationIndex.getOrNull(nextIndex) == true || activePrecomputations.contains(nextIndex)) return null
        val intervalCount = state.workout?.intervals?.size ?: 0
        if (nextIndex >= 0 && nextIndex < intervalCount) return nextIndex
        return null
    }
}

sealed class PreloadDecision {
    object None : PreloadDecision()
    data class Preload(val song: Song, val newSongIndex: Int? = null) : PreloadDecision()
}

sealed class TransitionDecision {
    object None : TransitionDecision()
    data class NextTrack(val newInterval: Boolean = false, val intervalIndex: Int = 0, val firstSong: Boolean = false) : TransitionDecision()
}
