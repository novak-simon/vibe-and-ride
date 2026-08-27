package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.ProfileRepository
import com.novaksimon.vibeandride.recommendation.RecommendationEngine
import com.novaksimon.vibeandride.recommendation.SongRepository
import com.novaksimon.vibeandride.workout.WorkoutState
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
 *
 * It decides WHAT should happen (song selection, transitions, preloading)
 * and coordinates with infrastructure components (recommender, playback, repositories)
 * to make it happen.
 *
 * It maintains the [MusicState] as the single source of truth for the session.
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

    fun workoutSelected(workoutState: WorkoutState) {
        val intervalCount = workoutState.workout?.intervals?.size ?: 0
        _state.value = _state.value.copy(
            currentIntervalIndex = -1,
            preloadedSongId = null,
            isTransitioning = false,
            workout = workoutState.workout,
            recommendationBuffer = List(intervalCount) { null },
            recommendationIndex = List(intervalCount) { false }
        )
        activePrecomputations.clear()
        println("MusicEngine: Workout selected. Interval index: ${workoutState.intervalIndex}")
        if (workoutState.intervalIndex != null) {
            precompute(workoutState.workout?.intervals?.getOrNull(workoutState.intervalIndex) ?: return, workoutState.intervalIndex)
        }
    }

    suspend fun play(intervalIndex: Int?, remainingInterval: Duration) {
        if (intervalIndex == null || _state.value.isTransitioning) return

        val timeUntilTrackEnd = service.timeUntilTrackEnd()
        val remainingInInterval = remainingInterval.inWholeMilliseconds.toFloat() / 1000f

        // Preload logic
        val preloadDecision = decidePreload(_state.value, remainingInInterval, timeUntilTrackEnd)
        if (preloadDecision is PreloadDecision.Preload) {
            val songToPreload = preloadDecision.song
            if (preloadDecision.newSongIndex != null) {
                _state.value = _state.value.copy(songIndex = preloadDecision.newSongIndex)
            }

            println("MusicEngine: preloading song ${songToPreload.title} (ID: ${songToPreload.id})")
            if (!service.preloadSong(songToPreload)) {
                _state.value = _state.value.copy(songIndex = _state.value.songIndex + 1)
                return
            }
            _state.value = _state.value.copy(hasPreloadedSong = true, preloadedSongId = songToPreload.id)
        }

        // Transition logic
        val transitionDecision = decideTransition(
            state = _state.value,
            targetIntervalIndex = intervalIndex,
            remainingInInterval = remainingInInterval,
            timeUntilTrackEnd = timeUntilTrackEnd,
            isPlaying = service.isPlaying()
        )

        if (transitionDecision is TransitionDecision.NextTrack) {
            println("MusicEngine: engine decided transition: $transitionDecision")
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
            if (newInterval) {
                println("MusicEngine: INTERVAL CHANGED, new index: $intervalIndex")
            }

            val song = selectSongFromBuffer(_state.value, _state.value.currentIntervalIndex, _state.value.songIndex) ?: run {
                if (_state.value.recommendationBuffer.getOrNull(_state.value.currentIntervalIndex) == null) {
                    println("MusicEngine: No recommendation ready for interval ${_state.value.currentIntervalIndex}")
                } else {
                    println("MusicEngine: No more songs in recommendation for interval ${_state.value.currentIntervalIndex} (index ${_state.value.songIndex})")
                }
                return
            }
            
            if (!service.crossfadeTo(song, 1000L)) {
                println("MusicEngine: Invalid URL in NEXT TRACK: ${song.title}")
                _state.value = _state.value.copy(
                    songIndex = _state.value.songIndex + 1,
                    isTransitioning = false
                )
                // Add a small delay to avoid tight loop consuming recommendation buffer on network/data issues
                delay(1.seconds)
                nextTrack(
                    newInterval = false,
                    intervalIndex = _state.value.currentIntervalIndex,
                    skipped = false,
                    firstSong = false
                )
                return
            }

            _state.value = finalizeTrackTransition(_state.value, song)

            statsScope.launch {
                val songStats: ProfileSongStats? = profileRepository.getStatByProfileAndSongId(songId = song.id)
                _state.value = _state.value.copy(isLiked = songStats?.isLiked ?: false)
            }

            // Wait for crossfade to complete before allowing another transition
            delay(2.seconds)

            engineScope.launch {
                delay(3.seconds)
                _state.value = _state.value.copy(hasPreloadedSong = false)
            }
            incrementStatsOnTrackArrive()
            println("MusicEngine: transition to ${song.title} complete")
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
        val nextIndex = decidePrecompute(_state.value, workoutState.intervalIndex, activePrecomputations)
        if (nextIndex != null) {
            val interval = workoutState.workout?.intervals?.getOrNull(nextIndex)
            if (interval != null) {
                precompute(interval, nextIndex)
            }
        }
    }

    fun precompute(interval: Interval, intervalIndex: Int) {
        activePrecomputations.add(intervalIndex)
        recommendScope.launch {
            val recommendation = recommendSemaphore.withPermit {
                println("MusicEngine: Recommending songs for interval: $intervalIndex")
                recommender.recommend(interval)
            }

            val currentBuffer = _state.value.recommendationBuffer.toMutableList()
            if (intervalIndex < currentBuffer.size) {
                currentBuffer[intervalIndex] = recommendation
            }

            // Update recommendationIndex to signal readiness
            val currentReady = _state.value.recommendationIndex.toMutableList()
            if (intervalIndex < currentReady.size) {
                currentReady[intervalIndex] = true
            }

            _state.value = _state.value.copy(
                recommendationBuffer = currentBuffer,
                futureDebug = recommendation.debugInfo,
                futureIntervalSong = recommendation.topSongs.firstOrNull(),
                recommendationIndex = currentReady
            )

            println("MusicEngine: Recommendation complete for interval: $intervalIndex")
        }
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

    /**
     * Given the current state and requested indices, decides which song should be selected.
     *
     * This logic is pure and does not perform any side effects.
     */
    fun selectSongFromBuffer(state: MusicState, intervalIndex: Int, songIndex: Int): Song? {
        if (intervalIndex < 0) return null
        val recommendation = state.recommendationBuffer.getOrNull(intervalIndex) ?: return null
        return recommendation.topSongs.getOrNull(songIndex)
    }

    /**
     * Decides whether a song should be preloaded based on current playback and interval state.
     */
    fun decidePreload(
        state: MusicState,
        remainingInInterval: Float,
        timeUntilTrackEnd: Float
    ): PreloadDecision {
        val remaining = min(remainingInInterval, timeUntilTrackEnd)
        val intervalIndex = state.currentIntervalIndex

        if (remaining < 30f && !state.hasPreloadedSong) {
            val songToPreload = if (remainingInInterval <= timeUntilTrackEnd + 5f) {
                // Interval is ending soon, preload first song of next interval
                selectSongFromBuffer(state, intervalIndex + 1, 0) ?: state.futureIntervalSong
            } else {
                // Track is ending, but interval still has time, preload next song in current interval
                selectSongFromBuffer(state, intervalIndex, state.songIndex)
            }

            val newSongIndex = if (remainingInInterval <= timeUntilTrackEnd + 5f) 0 else null

            if (songToPreload != null && 
                songToPreload.id != state.preloadedSongId && 
                songToPreload.id != state.currentSong?.id
            ) {
                return PreloadDecision.Preload(songToPreload, newSongIndex)
            }
        }
        return PreloadDecision.None
    }

    /**
     * Decides whether a track transition should occur based on current playback and interval state.
     */
    fun decideTransition(
        state: MusicState,
        targetIntervalIndex: Int,
        remainingInInterval: Float,
        timeUntilTrackEnd: Float,
        isPlaying: Boolean
    ): TransitionDecision {
        if (state.isTransitioning) return TransitionDecision.None

        if (remainingInInterval < 1f) {
            return TransitionDecision.NextTrack(newInterval = true, intervalIndex = targetIntervalIndex + 1)
        }

        if (timeUntilTrackEnd < 1f) {
            return TransitionDecision.NextTrack(newInterval = false)
        }

        if (state.currentIntervalIndex == -1) {
            return TransitionDecision.NextTrack(
                firstSong = true, 
                newInterval = true, 
                intervalIndex = targetIntervalIndex
            )
        }

        // Auto-start if playback stopped unexpectedly but we are not yet at the end of the track.
        // We only do this if we don't have a current song yet. 
        // If we HAVE a current song and it's not playing, we assume it's paused by the user 
        // unless it's at the very end (handled above).
        if (!isPlaying && state.currentSong == null) {
            return TransitionDecision.NextTrack(newInterval = false)
        }

        return TransitionDecision.None
    }

    /**
     * Prepares the state for a track transition.
     */
    fun prepareForNextTrack(
        state: MusicState,
        newInterval: Boolean,
        intervalIndex: Int
    ): MusicState {
        return if (newInterval) {
            state.copy(
                currentDebug = state.futureDebug,
                currentIntervalIndex = intervalIndex,
                songIndex = 0
            )
        } else {
            state
        }
    }

    /**
     * Finalizes the state after a successful track transition.
     */
    fun finalizeTrackTransition(
        state: MusicState,
        song: Song
    ): MusicState {
        return state.copy(
            currentSong = song,
            songIndex = state.songIndex + 1
        )
    }

    /**
     * Decides whether a recommendation precomputation should occur for the next interval.
     * Returns the index of the interval to precompute, or null if no precomputation is needed.
     */
    fun decidePrecompute(
        state: MusicState,
        currentIntervalIndex: Int?,
        activePrecomputations: Set<Int>
    ): Int? {
        if (currentIntervalIndex == null) return null
        val nextIndex = currentIntervalIndex + 1

        if (state.recommendationIndex.getOrNull(nextIndex) == true || 
            activePrecomputations.contains(nextIndex)
        ) {
            return null
        }

        val intervalCount = state.workout?.intervals?.size ?: 0
        if (nextIndex >= 0 && nextIndex < intervalCount) {
            return nextIndex
        }

        return null
    }
}

/**
 * Represents the decision made by the MusicEngine regarding preloading.
 */
sealed class PreloadDecision {
    object None : PreloadDecision()
    data class Preload(val song: Song, val newSongIndex: Int? = null) : PreloadDecision()
}

/**
 * Represents the decision made by the MusicEngine regarding transitions.
 */
sealed class TransitionDecision {
    object None : TransitionDecision()
    data class NextTrack(
        val newInterval: Boolean = false,
        val intervalIndex: Int = 0,
        val firstSong: Boolean = false
    ) : TransitionDecision()
}
