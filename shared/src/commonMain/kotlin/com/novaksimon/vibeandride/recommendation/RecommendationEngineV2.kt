package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.util.VibeLog
import com.novaksimon.vibeandride.workout.WorkoutMode
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import com.novaksimon.vibeandride.music.MusicState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * RecommendationEngineV2 is a reactive orchestrator for music recommendations.
 * It manages current and future interval recommendations based on the workout session state.
 */
private const val TAG = "RecommendationEngineV2"
class RecommendationEngineV2(
    songRepository: SongRepository,
    profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val recommender = RecommendationEngine(
        songRepository,
        profileRepository,
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher
    )
    private val _state = MutableStateFlow(RecommendationV2State())
    val state: StateFlow<RecommendationV2State> = _state.asStateFlow()
    
    private val semaphore = Semaphore(2)

    /**
     * Resets the engine state for a new session and triggers initial fetch.
     */
    fun sessionStarted(sessionState: WorkoutSessionState) {
        val mode = sessionState.mode ?: return
        val currentIntervalIndex = sessionState.structuredState?.intervalIndex ?: 0
        
        VibeLog.i(TAG, "Session started. Initializing V2 state. Interval index: $currentIntervalIndex")
        _state.update { 
            RecommendationV2State(currentIntervalIndex = currentIntervalIndex) 
        }

        // Trigger initial fetch for current interval
        val interval = when (mode) {
            is WorkoutMode.Structured -> mode.workout.intervals.getOrNull(currentIntervalIndex)
            is WorkoutMode.FreeRide -> Interval(
                duration = 86400.0, 
                power = 0.5f, 
                cadence = sessionState.currentCadence ?: 90.0, 
                type = "FreeRide"
            )
        }
        if (interval != null) {
            VibeLog.i(TAG, "Session started. Triggering initial fetch for interval $currentIntervalIndex")
            fetchCurrent(interval)
        }
    }

    /**
     * Updates the recommendation state based on the current workout and music state.
     */
    fun update(
        sessionState: WorkoutSessionState,
        musicState: MusicState,
        timeUntilTrackEnd: Float
    ) {
        val mode = sessionState.mode ?: return
        val structuredState = sessionState.structuredState
        val currentIntervalIndex = structuredState?.intervalIndex ?: 0
        
        // 1. Detect interval change and shift buffers
        if (currentIntervalIndex != _state.value.currentIntervalIndex && _state.value.currentIntervalIndex != -1) {
            VibeLog.i(TAG, "Interval changed: ${_state.value.currentIntervalIndex} -> $currentIntervalIndex. Shifting buffers.")
            _state.update {
                it.copy(
                    current = if (currentIntervalIndex == it.currentIntervalIndex + 1) it.future else null,
                    future = null,
                    currentIntervalIndex = currentIntervalIndex
                )
            }
        } else if (_state.value.currentIntervalIndex == -1) {
             _state.update { it.copy(currentIntervalIndex = currentIntervalIndex) }
        }

        // 2. Initial fetch for current interval if missing
        if (_state.value.current == null && !_state.value.isLoadingCurrent) {
            val interval = when (mode) {
                is WorkoutMode.Structured -> mode.workout.intervals.getOrNull(currentIntervalIndex)
                is WorkoutMode.FreeRide -> Interval(duration = 86400.0, power = 0.5f, cadence = sessionState.currentCadence ?: 90.0, type = "FreeRide")
            }
            if (interval != null) {
                VibeLog.i(TAG, "Fetching current recommendation for interval $currentIntervalIndex")
                fetchCurrent(interval)
            }
        }

        // 3. Precompute future interval for structured workouts
        if (mode is WorkoutMode.Structured) {
            val remainingInInterval = structuredState?.remainingInInterval?.inWholeMilliseconds?.toFloat()?.div(1000f) ?: Float.MAX_VALUE
            val shouldPrecomputeFuture = remainingInInterval < 40f
            
            if (shouldPrecomputeFuture && _state.value.future == null && !_state.value.isLoadingFuture) {
                val nextIntervalIndex = currentIntervalIndex + 1
                val nextInterval = mode.workout.intervals.getOrNull(nextIntervalIndex)
                if (nextInterval != null) {
                    VibeLog.i(TAG, "Fetching future recommendation for interval $nextIntervalIndex (remaining: $remainingInInterval)")
                    fetchFuture(nextInterval, musicState)
                }
            }
        }
    }

    private fun fetchCurrent(interval: Interval) {
        _state.update { it.copy(isLoadingCurrent = true) }
        scope.launch {
            try {
                val result = semaphore.withPermit { recommender.recommend(interval) }
                _state.update { it.copy(current = result, isLoadingCurrent = false) }
            } catch (e: Exception) {
                VibeLog.e(TAG, "Error fetching current recommendation", e)
                _state.update { it.copy(isLoadingCurrent = false) }
            }
        }
    }

    private fun fetchFuture(interval: Interval, musicState: MusicState) {
        _state.update { it.copy(isLoadingFuture = true) }
        scope.launch {
            try {
                val result = semaphore.withPermit { recommender.recommend(interval, candidateProvider="v1", musicState=musicState) }
                _state.update { it.copy(future = result, isLoadingFuture = false) }
            } catch (e: Exception) {
                VibeLog.e(TAG, "Error fetching future recommendation", e)
                _state.update { it.copy(isLoadingFuture = false) }
            }
        }
    }

    /**
     * Selects a song from the current or future buffer.
     */
    fun selectSong(intervalIndex: Int, songIndex: Int): Song? {
        if (intervalIndex < 0) return null
        val s = _state.value
        return when (intervalIndex) {
            s.currentIntervalIndex -> {
                s.current?.topSongs?.getOrNull(songIndex)
            }
            s.currentIntervalIndex + 1 -> {
                s.future?.topSongs?.getOrNull(songIndex)
            }
            else -> {
                null
            }
        }
    }
}
