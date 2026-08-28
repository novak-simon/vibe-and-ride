package com.novaksimon.vibeandride.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.novaksimon.vibeandride.MainActivity
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
    val context = LocalContext.current

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
        onAdjustTime = { workoutViewModel.adjustTime(it) },
        onLikeSong = { musicViewModel.songLiked(it) },
        onUploadWorkout = onUploadWorkout,
        onPickerVisibilityChange = { workoutViewModel.setPickerVisible(it) },
        onStartFreeRide = { workoutViewModel.startFreeRide() },
        onToggleBle = {
            if (workoutUiState.bleStatus == com.novaksimon.vibeandride.workout.CadenceSourceStatus.IDLE || 
                workoutUiState.bleStatus == com.novaksimon.vibeandride.workout.CadenceSourceStatus.ERROR) {
                (context as? MainActivity)?.requestBlePermissions()
            } else {
                workoutViewModel.toggleBle()
            }
        },
        onClearError = {
            workoutViewModel.clearError()
        }
    )
}
