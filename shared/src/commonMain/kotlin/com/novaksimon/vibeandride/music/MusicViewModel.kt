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

data class MusicUiState(
    val currentSong: Song? = null,
    val isMusicAvailable: Boolean = true,
    val debugInfo: List<SongRecommendation>? = null,
)


class MusicViewModel(workoutViewModel: WorkoutViewModel) : ViewModel() {

    private val musicProvider: MusicProvider = MusicProvider()
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workoutViewModel.uiState
            .map { it.workoutState }
            .distinctUntilChanged()
            .collect { workoutState ->
                if (workoutState?.status == WorkoutStatus.NOT_STARTED) {
                    workoutLoaded()
                }
            }
        }
    }

    private fun workoutLoaded() {

    }


    fun skip() {
        viewModelScope.launch {
            musicProvider.skip()
        }
    }
}