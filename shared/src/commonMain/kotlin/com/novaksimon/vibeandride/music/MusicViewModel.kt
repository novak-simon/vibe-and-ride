package com.novaksimon.vibeandride.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaksimon.vibeandride.model.Song
import kotlinx.coroutines.launch
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.workout.WorkoutStatus
import com.novaksimon.vibeandride.workout.WorkoutViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

data class MusicUiState(
    val currentSong: Song? = null,
    val isMusicAvailable: Boolean = false,
    val debugInfo: List<SongRecommendation>? = null,
    val isLiked: Boolean = false,
)


class MusicViewModel(
    playbackController: PlaybackController,
) : ViewModel() {

    private lateinit var workoutViewModel: WorkoutViewModel
    private val _uiState = MutableStateFlow(MusicUiState())
    private val musicProvider: MusicProvider = MusicProvider(_uiState, playbackController)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    fun initWorkoutViewModel(workoutViewModel: WorkoutViewModel) {
        this.workoutViewModel = workoutViewModel
        viewModelScope.launch {
            workoutViewModel.uiState
                .map { it.workoutState }
                .distinctUntilChanged()
                .collect { workoutState ->
                    if (workoutState?.status == WorkoutStatus.NOT_STARTED) {
                        musicProvider.workoutSelected(workoutState)
                    } else if (workoutState?.status == WorkoutStatus.RUNNING) {

                        musicProvider.play(
                            workoutState.intervalIndex,
                            workoutState.remainingInInterval
                        )
                    }
                    workoutState?.remainingInInterval?.let {
                        if (it < 40.seconds) {
                            musicProvider.precomputeNextRecommendation(workoutState)
                        }
                    }
                }
        }
    }
    fun skip() {
        viewModelScope.launch {
            musicProvider.skip()
        }
    }

    fun pauseResume() {
        viewModelScope.launch {
            musicProvider.pauseResume()
        }
    }

    fun play() {
        viewModelScope.launch {
            musicProvider.play()
        }
    }

    fun pause() {
        viewModelScope.launch {
            musicProvider.pause()
        }
    }

    fun songLiked(liked: Boolean) {
        _uiState.value = _uiState.value.copy(isLiked = liked)
        musicProvider.songLiked(liked)
    }
}