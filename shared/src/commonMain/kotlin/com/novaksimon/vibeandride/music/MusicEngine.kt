package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.ProfileRepository
import com.novaksimon.vibeandride.recommendation.RecommendationEngineV2
import com.novaksimon.vibeandride.recommendation.RecommendationV2State
import com.novaksimon.vibeandride.recommendation.SongRepository
import com.novaksimon.vibeandride.util.VibeLog
import com.novaksimon.vibeandride.workout.WorkoutMode
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * MusicEngine is the central orchestrator for music sessions.
 */
private const val TAG = "MusicEngine"
class MusicEngine(
    playbackController: PlaybackController,
    songRepository: SongRepository,
    private val profileRepository: ProfileRepository,
    mainDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) {

    private val service = ProfileMusicService(playbackController, songRepository)
    
    private val engineScope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val statsScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val recommendScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    
    private val recommenderV2 = RecommendationEngineV2(
        songRepository = songRepository,
        profileRepository = profileRepository,
        scope = recommendScope,
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher
    )

    private val profileStatsEngine = ProfileStatsEngine(
        profileRepository = profileRepository,
        songRepository = songRepository,
        scope = statsScope,
        ioDispatcher = ioDispatcher
    )

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init {
        playbackController.errorFlow
            .filter { it.isNotEmpty() }
            .onEach { error ->
                handlePlaybackError(error)
            }
            .launchIn(engineScope)

        recommenderV2.state.onEach { v2State ->
            _state.update { it.copy(recommendationV2 = v2State) }
        }.launchIn(engineScope)

        profileStatsEngine.state.onEach { statsState ->
            _state.update { it.copy(isLiked = statsState.isLiked) }
        }.launchIn(engineScope)
    }

    fun sessionStarted(sessionState: WorkoutSessionState) {
        val mode = sessionState.mode ?: return
        val workout = if (mode is WorkoutMode.Structured) mode.workout else null

        service.reset()

        _state.update {
            it.copy(
                mode = mode,
                workout = workout,
                currentIntervalIndex = -1,
                currentSong = null,
                songIndex = 0,
                isLiked = false,
                preloadedSongId = null,
                playbackState = PlaybackState.IDLE,
                playbackErrorCount = 0,
                songStartPosition = 0f,
                lastTransitionTimeMillis = 0L,
                recommendationV2 = RecommendationV2State(),
                failedSongIds = emptySet()
            )
        }
        
        profileStatsEngine.reset()
        recommenderV2.sessionStarted(sessionState)
    }

    suspend fun update(sessionState: WorkoutSessionState) {
        if (_state.value.playbackState == PlaybackState.TRANSITIONING) return

        val now = Clock.System.now().toEpochMilliseconds()
        if (now - _state.value.lastTransitionTimeMillis < 2000L) return

        val timeUntilTrackEnd = service.timeUntilTrackEnd()
        val currentPosition = service.currentPosition()
        val remainingInInterval = sessionState.structuredState?.remainingInInterval?.inWholeMilliseconds?.toFloat()?.div(1000f) ?: Float.MAX_VALUE

        profileStatsEngine.update(
            currentPosition = currentPosition,
            timeUntilTrackEnd = timeUntilTrackEnd,
            remainingInInterval = remainingInInterval
        )
        recommenderV2.update(sessionState, _state.value, timeUntilTrackEnd)

        // Preload logic
        val preloadDecision = decidePreload(_state.value, remainingInInterval, timeUntilTrackEnd, sessionState)
        if (preloadDecision is PreloadDecision.Preload) {
            val songToPreload = preloadDecision.song
            
            VibeLog.d(TAG, "MusicEngine: preloading song ${songToPreload.title}")
            if (!service.preloadSong(songToPreload)) {
                VibeLog.w(TAG, "MusicEngine: Preload failed for ${songToPreload.title}, adding to failed list")
                _state.update { it.copy(failedSongIds = it.failedSongIds + songToPreload.id) }
                return
            }
            _state.update {
                it.copy(
                    playbackState = PlaybackState.PRELOADING,
                    preloadedSongId = songToPreload.id
                )
            }
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
        firstSong: Boolean = false,
        isRetry: Boolean = false
    ) {
        if (_state.value.playbackState == PlaybackState.TRANSITIONING) return
        _state.update { it.copy(playbackState = PlaybackState.TRANSITIONING) }
        try {
            if (!firstSong && !isRetry) {
                _state.value.currentSong?.let { song ->
                    profileStatsEngine.handleTrackLeft(
                        song = song,
                        startPosition = _state.value.songStartPosition,
                        endPosition = service.currentPosition(),
                        skipped = skipped
                    )
                }
            }

            _state.update { prepareForNextTrack(it, newInterval, intervalIndex) }
            
            val song = selectSongFromBuffer(_state.value, _state.value.currentIntervalIndex, _state.value.songIndex) ?: run {
                VibeLog.w(TAG, "MusicEngine: No recommendation ready for index ${_state.value.currentIntervalIndex}")
                _state.update { it.copy(playbackState = PlaybackState.IDLE) }
                return
            }
            
            if (!service.crossfadeTo(song, 1000L)) {
                VibeLog.e(TAG, "MusicEngine: Crossfade failed for ${song.title}, skipping to next track")
                _state.update {
                    it.copy(
                        songIndex = it.songIndex + 1,
                        playbackState = PlaybackState.PLAYING
                    )
                }
                delay(1.seconds)
                nextTrack(newInterval = false, intervalIndex = _state.value.currentIntervalIndex)
                return
            }

            _state.update { finalizeTrackTransition(it, song, isRetry) }

            val currentPosition = service.currentPosition()
            profileStatsEngine.handleTrackArrived(song, currentPosition, isRetry)
            _state.update { it.copy(songStartPosition = currentPosition) }
        } catch (e: Exception) {
            VibeLog.e(TAG, "MusicEngine: Error during nextTrack", e)
            _state.update { it.copy(playbackState = PlaybackState.ERROR) }
        }
    }

    fun skip() {
        engineScope.launch {
            nextTrack(skipped = true)
        }
    }

    fun songLiked(liked: Boolean = true) {
        val song = _state.value.currentSong ?: return
        profileStatsEngine.handleSongLiked(song, liked)
    }

    fun pauseResume() { service.pauseResume() }
    fun play() { service.play() }
    fun pause() { service.pause() }

    fun selectSongFromBuffer(state: MusicState, intervalIndex: Int, songIndex: Int, skipFailed: Boolean = true): Song? {
        val actualIndex = if (state.mode is WorkoutMode.FreeRide) 0 else intervalIndex
        if (actualIndex < 0) return null
        
        var offset = 0
        while (offset < 20) {
            val song = recommenderV2.selectSong(actualIndex, songIndex + offset) ?: return null
            if (!skipFailed || !state.failedSongIds.contains(song.id)) return song
            offset++
        }
        return null
    }

    fun decidePreload(
        state: MusicState,
        remainingInInterval: Float,
        timeUntilTrackEnd: Float,
        sessionState: WorkoutSessionState
    ): PreloadDecision {
        if (state.currentIntervalIndex < 0) return PreloadDecision.None

        val remaining = min(remainingInInterval, timeUntilTrackEnd)
        
        if (remaining < 30f && state.playbackState == PlaybackState.PLAYING) {
            val songToPreload = if (state.mode is WorkoutMode.FreeRide) {
                selectSongFromBuffer(state, 0, state.songIndex)
            } else if (remainingInInterval <= timeUntilTrackEnd + 5f) {
                selectSongFromBuffer(state, state.currentIntervalIndex + 1, 0)
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
        if (state.playbackState == PlaybackState.TRANSITIONING) return TransitionDecision.None

        if (state.currentIntervalIndex == -1) {
            return TransitionDecision.NextTrack(
                firstSong = true, 
                newInterval = true, 
                intervalIndex = sessionState.structuredState?.intervalIndex ?: 0
            )
        }

        if (state.mode is WorkoutMode.Structured && remainingInInterval < 1f) {
            return TransitionDecision.NextTrack(newInterval = true, intervalIndex = (sessionState.structuredState?.intervalIndex ?: 0) + 1)
        }

        if (timeUntilTrackEnd < 1f) {
            return TransitionDecision.NextTrack(newInterval = false)
        }

        if (!isPlaying && state.currentSong == null) {
            return TransitionDecision.NextTrack(newInterval = false)
        }

        return TransitionDecision.None
    }

    fun prepareForNextTrack(state: MusicState, newInterval: Boolean, intervalIndex: Int): MusicState {
        return if (newInterval || state.mode is WorkoutMode.FreeRide) {
            state.copy(
                currentIntervalIndex = if (state.mode is WorkoutMode.FreeRide) 0 else intervalIndex,
                songIndex = if (newInterval) 0 else state.songIndex
            )
        } else state
    }

    fun finalizeTrackTransition(state: MusicState, song: Song, isRetry: Boolean = false): MusicState {
        var newIndex = state.songIndex
        while (newIndex < state.songIndex + 20) {
            val s = selectSongFromBuffer(state, state.currentIntervalIndex, newIndex, skipFailed = false)
            if (s == null || s.id == song.id) break
            newIndex++
        }

        return state.copy(
            currentSong = song,
            songIndex = newIndex + 1,
            isLiked = false,
            playbackState = PlaybackState.PLAYING,
            playbackErrorCount = if (isRetry) state.playbackErrorCount else 0,
            lastTransitionTimeMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    private fun handlePlaybackError(message: String) {
        VibeLog.e(TAG, "MusicEngine: Playback error received: $message")
        val state = _state.value

        if (state.playbackState == PlaybackState.TRANSITIONING) {
            VibeLog.d(TAG, "MusicEngine: Ignoring error during transition")
            return
        }

        if (state.playbackState == PlaybackState.PRELOADING) {
            val failedId = state.preloadedSongId
            VibeLog.w(TAG, "MusicEngine: Error during preload for song $failedId. Skipping.")
            _state.update {
                it.copy(
                    playbackState = PlaybackState.PLAYING,
                    preloadedSongId = null,
                    failedSongIds = if (failedId != null) it.failedSongIds + failedId else it.failedSongIds
                )
            }
            return
        }

        val currentCount = state.playbackErrorCount
        _state.update { it.copy(playbackState = PlaybackState.ERROR) }

        if (currentCount < 3) {
            VibeLog.i(TAG, "MusicEngine: Retrying current song (attempt ${currentCount + 1})")
            _state.update {
                it.copy(
                    playbackErrorCount = currentCount + 1,
                    songIndex = (it.songIndex - 1).coerceAtLeast(0)
                )
            }
            engineScope.launch {
                nextTrack(newInterval = false, isRetry = true)
            }
        } else {
            VibeLog.w(TAG, "MusicEngine: Too many retries, skipping current song")
            val currentId = state.currentSong?.id
            _state.update {
                it.copy(
                    failedSongIds = if (currentId != null) it.failedSongIds + currentId else it.failedSongIds
                )
            }
            engineScope.launch {
                nextTrack(skipped = true)
            }
        }
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
