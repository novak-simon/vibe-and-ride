package com.novaksimon.vibeandride.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaksimon.vibeandride.database.SupabaseProfileRepository
import com.novaksimon.vibeandride.database.SupabaseSongRepository
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import com.novaksimon.vibeandride.workout.WorkoutViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class MusicUiState(
    val currentSong: Song? = null,
    val isMusicAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val debugInfo: List<SongRecommendation>? = null,
    val isLiked: Boolean = false,
)


class MusicViewModel(
    playbackController: PlaybackController,
) : ViewModel() {

    private lateinit var workoutViewModel: WorkoutViewModel
    private val musicEngine: MusicEngine = MusicEngine(
        playbackController,
        SupabaseSongRepository(),
        SupabaseProfileRepository()
    )
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Synchronize MusicEngine state to MusicUiState
            combine(
                musicEngine.state,
                playbackController.isPlayingFlow
            ) { musicState, isPlaying ->
                val targetIndex = if (musicState.currentIntervalIndex == -1) 0 else musicState.currentIntervalIndex
                
                MusicUiState(
                    currentSong = musicState.currentSong,
                    isMusicAvailable = musicState.recommendationIndex.getOrNull(targetIndex) ?: false,
                    isPlaying = isPlaying,
                    debugInfo = musicState.currentDebug,
                    isLiked = musicState.isLiked
                )
            }.collect { 
                _uiState.value = it 
            }
        }
    }

    fun initWorkoutViewModel(workoutViewModel: WorkoutViewModel) {
        this.workoutViewModel = workoutViewModel
        viewModelScope.launch {
            workoutViewModel.uiState
                .map { it.workoutState }
                .distinctUntilChanged { old, new ->
                    old?.status == new?.status && 
                    old?.mode == new?.mode && 
                    old?.structuredState?.intervalIndex == new?.structuredState?.intervalIndex
                }
                .collect { sessionState ->
                    if (sessionState == null) return@collect
                    
                    if (sessionState.status == WorkoutStatus.NOT_STARTED || sessionState.status == WorkoutStatus.READY) {
                        musicEngine.sessionStarted(sessionState)
                    } else if (sessionState.status == WorkoutStatus.RUNNING) {
                        musicEngine.update(sessionState)
                    }
                    
                    if (sessionState.structuredState != null && sessionState.structuredState.remainingInInterval < 40.seconds) {
                        musicEngine.precomputeNextRecommendation(sessionState)
                    }
                }
        }
    }
    fun skip() {
        musicEngine.skip()
    }

    fun pauseResume() {
        musicEngine.pauseResume()
    }

    fun play() {
        musicEngine.play()
    }

    fun pause() {
        musicEngine.pause()
    }

    fun songLiked(liked: Boolean) {
        println("SONG LIKED!!!")
        _uiState.value = _uiState.value.copy(isLiked = liked)
        musicEngine.songLiked(liked)
    }
}
