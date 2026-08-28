package com.novaksimon.vibeandride.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaksimon.vibeandride.database.SupabaseProfileRepository
import com.novaksimon.vibeandride.database.SupabaseSongRepository
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.util.VibeLog
import com.novaksimon.vibeandride.workout.WorkoutStatus
import com.novaksimon.vibeandride.workout.WorkoutViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class MusicUiState(
    val currentSong: Song? = null,
    val isMusicAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val debugInfo: List<SongRecommendation>? = null,
    val isLiked: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val errorMessage: String? = null
)


class MusicViewModel(
    playbackController: PlaybackController,
) : ViewModel() {

    private val TAG = "MusicViewModel"
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
                val v2State = musicState.recommendationV2
                
                MusicUiState(
                    currentSong = musicState.currentSong,
                    isMusicAvailable = v2State.current != null,
                    isPlaying = isPlaying,
                    debugInfo = v2State.current?.debugInfo,
                    isLiked = musicState.isLiked,
                    playbackState = musicState.playbackState,
                    errorMessage = if (musicState.playbackState == PlaybackState.ERROR) "Playback Error" else null
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
                    val statusChanged = old?.status != new?.status
                    val modeChanged = old?.mode != new?.mode
                    val intervalChanged = old?.structuredState?.intervalIndex != new?.structuredState?.intervalIndex

                    // Only skip emission if NOTHING changed AND we are not in RUNNING state.
                    // When RUNNING, we need every tick to monitor song progress.
                    !statusChanged && !modeChanged && !intervalChanged && new?.status != WorkoutStatus.RUNNING
                }
                .collect { sessionState ->
                    if (sessionState == null) return@collect
                    
                    if (sessionState.status == WorkoutStatus.NOT_STARTED) {
                        musicEngine.sessionStarted(sessionState)
                    } else if (sessionState.mode != musicEngine.state.value.mode) {
                        musicEngine.sessionStarted(sessionState)
                    } else if (sessionState.status == WorkoutStatus.RUNNING) {
                        musicEngine.update(sessionState)
                    }
                }
        }
    }
    fun skip() {
        musicEngine.skip()
    }

    fun play() {
        musicEngine.play()
    }

    fun pause() {
        musicEngine.pause()
    }

    fun songLiked(liked: Boolean) {
        VibeLog.d(TAG, "Song liked: $liked")
        _uiState.value = _uiState.value.copy(isLiked = liked)
        musicEngine.songLiked(liked)
    }
}
