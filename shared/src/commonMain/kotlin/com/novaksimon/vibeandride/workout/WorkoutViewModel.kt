package com.novaksimon.vibeandride.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.database.incrementWorkoutChosenCount
import com.novaksimon.vibeandride.database.listWorkouts
import com.novaksimon.vibeandride.music.MusicViewModel
import com.novaksimon.vibeandride.util.VibeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class WorkoutUiState(
    val workoutState: WorkoutSessionState? = null,
    val isPickerVisible: Boolean = false,
    val isLoading: Boolean = false,
    val bleStatus: CadenceSourceStatus = CadenceSourceStatus.IDLE,
    val errorMessage: String? = null,
    val showFinishedAnimation: Boolean = false
)

class WorkoutViewModel(
    private val cadenceSource: CadenceSource? = null,
    private val tickInterval: Duration = 200.milliseconds,
) : ViewModel() {

    private val TAG = "WorkoutViewModel"

    private lateinit var musicViewModel: MusicViewModel
    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()
    private val scheduler: WorkoutScheduler = WorkoutScheduler()
    private val _availableWorkouts = MutableStateFlow<List<Workout>>(emptyList())
    val availableWorkouts: StateFlow<List<Workout>> = _availableWorkouts.asStateFlow()
    private var tickJob: Job? = null
    private var animationJob: Job? = null
    private var hasIncrementedChosenCount = false

    init {
        viewModelScope.launch {
            loadAvailableWorkouts()
        }
        
        // Observe Cadence Source
        cadenceSource?.let { source ->
            viewModelScope.launch {
                source.cadence.collect { cadence ->
                    scheduler.updateLiveCadence(cadence)
                    // If we are not ticking (workout not started), we still want to see live cadence
                    if (tickJob == null || !tickJob!!.isActive) {
                        tick()
                    }
                }
            }
            viewModelScope.launch {
                source.power.collect { power ->
                    scheduler.updateLivePower(power)
                    // If we are not ticking (workout not started), we still want to see live power
                    if (tickJob == null || !tickJob!!.isActive) {
                        tick()
                    }
                }
            }
            viewModelScope.launch {
                source.status.collect { status ->
                    _uiState.value = _uiState.value.copy(bleStatus = status)
                    if (status != CadenceSourceStatus.CONNECTED) {
                        scheduler.updateLiveCadence(null)
                        scheduler.updateLivePower(null)
                        tick()
                    }
                }
            }
        }
    }

    fun initMusicViewModel(musicViewModel: MusicViewModel) {
        this.musicViewModel = musicViewModel
        viewModelScope.launch {
            musicViewModel.uiState
                .map { it.isMusicAvailable }
                .distinctUntilChanged()
                .collect { isMusicAvailable ->
                    if (isMusicAvailable) {
                        VibeLog.d(TAG, "Music is available, notifying scheduler")
                        scheduler.musicPrepared()
                        tick()
                    }
                }
        }
    }

    fun startWorkout() {
        startTicking()
        scheduler.start()
    }

    fun pauseResume() {
        scheduler.pauseResume()
    }

    fun adjustTime(delta: Duration) {
        scheduler.adjustTime(delta)
        tick()
    }

    fun loadAvailableWorkouts() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                _availableWorkouts.value = listWorkouts()
            } catch (e: Exception) {
                VibeLog.e(TAG, "Failed to load workouts", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load workouts from server")
                _availableWorkouts.value = emptyList()
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun uploadWorkoutFromFile(content: String, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val newWorkout = WorkoutParser().parseAndUpload(content, fileName)
                loadAvailableWorkouts()
                selectWorkout(newWorkout.id)
                setPickerVisible(false)
            } catch (e: Exception) {
                VibeLog.e(TAG, "Failed to upload workout: $fileName", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to upload workout: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun setPickerVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isPickerVisible = visible)
    }

    fun selectWorkout(workoutId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            scheduler.reset()
            stopTicking()
            _uiState.value = WorkoutUiState(bleStatus = cadenceSource?.status?.value ?: CadenceSourceStatus.IDLE)
            scheduler.workoutIdSelected(workoutId)
            hasIncrementedChosenCount = false
            tick()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun startFreeRide() {
        scheduler.startFreeRide()
        hasIncrementedChosenCount = false
        startWorkout()
        tick()
    }
    fun toggleBle() {
        val source = cadenceSource ?: return
        when (source.status.value) {
            CadenceSourceStatus.IDLE, CadenceSourceStatus.ERROR -> source.startScanning()
            CadenceSourceStatus.SCANNING, CadenceSourceStatus.CONNECTING, CadenceSourceStatus.CONNECTED -> source.disconnect()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (true) {
                try {
                    tick()
                } catch (e: Exception) {
                    VibeLog.e(TAG, "Error during tick", e)
                }
                delay(tickInterval)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun triggerFinishedAnimation() {
        if (animationJob?.isActive == true) return
        animationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showFinishedAnimation = true)
            delay(20.seconds)
            _uiState.value = _uiState.value.copy(showFinishedAnimation = false)
        }
    }

    private fun tick() {
        val state = scheduler.getState()
        val oldStatus = _uiState.value.workoutState?.status
        _uiState.value = _uiState.value.copy(
            workoutState = state,
        )

        if (state.status == WorkoutStatus.FINISHED && oldStatus != WorkoutStatus.FINISHED) {
            triggerFinishedAnimation()
        }

        // Increment chosen_count if workout is more than half finished
        if (!hasIncrementedChosenCount && state.status == WorkoutStatus.RUNNING) {
            val mode = state.mode
            if (mode is WorkoutMode.Structured) {
                val workout = mode.workout
                val totalDuration = workout.totalDuration.seconds
                if (state.totalElapsed > totalDuration / 2) {
                    hasIncrementedChosenCount = true
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            incrementWorkoutChosenCount(workout.id)
                        } catch (e: Exception) {
                            VibeLog.e(TAG, "Failed to increment chosen count for workout ${workout.id}", e)
                        }
                    }
                }
            }
        }
    }

    @Suppress("EmptySuperCall")
    override fun onCleared() {
        stopTicking()
        cadenceSource?.disconnect()
        super.onCleared()
    }
}
