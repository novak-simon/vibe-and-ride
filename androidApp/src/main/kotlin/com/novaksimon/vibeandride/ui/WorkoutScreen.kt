package com.novaksimon.vibeandride.ui

import androidx.compose.runtime.*
import com.novaksimon.vibeandride.music.MusicViewModel
import com.novaksimon.vibeandride.workout.WorkoutViewModel

@Composable
fun WorkoutScreen(
    workoutViewModel: WorkoutViewModel,
    musicViewModel: MusicViewModel,
    onUploadWorkout: () -> Unit
) {
    val workoutUiState by workoutViewModel.uiState.collectAsState()
    val musicUiState by musicViewModel.uiState.collectAsState()
    val availableWorkouts by workoutViewModel.availableWorkouts.collectAsState()
    WorkoutScreenContent(
        workoutUiState = workoutUiState,
        musicUiState = musicUiState,
        availableWorkouts = availableWorkouts,
        onLoadWorkouts = { workoutViewModel.loadAvailableWorkouts() },
        onSelectWorkout = { workoutViewModel.selectWorkout(it) },
        onStart = { workoutViewModel.startWorkout() },
        onMusicPlay = { musicViewModel.play() },
        onMusicPause = { musicViewModel.pause() },
        onPauseResumeBoth = {
            val isPaused = workoutUiState.workoutState?.status == com.novaksimon.vibeandride.workout.WorkoutStatus.PAUSED
            workoutViewModel.pauseResume()
            if (isPaused) {
                musicViewModel.play()
            } else {
                musicViewModel.pause()
            }
        },
        onSkip = { musicViewModel.skip() },
        onReset = { workoutViewModel.reset() },
        onResync = { workoutViewModel.resync(it) },
        onLikeSong = { musicViewModel.songLiked(it) },
        onUploadWorkout = onUploadWorkout,
        onPickerVisibilityChange = { workoutViewModel.setPickerVisible(it) }
    )
}
