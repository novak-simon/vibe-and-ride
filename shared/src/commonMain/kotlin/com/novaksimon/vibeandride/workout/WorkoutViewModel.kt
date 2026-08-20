package com.novaksimon.vibeandride.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.database.listWorkouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class WorkoutUiState(
    val workoutState: WorkoutState? = null,
    val isPickerVisible: Boolean = false,
    val isLoading: Boolean = false,
)

class WorkoutViewModel(
    private val tickInterval: Duration = 200.milliseconds,
) : ViewModel() {

    private val scheduler: WorkoutScheduler = WorkoutScheduler()
    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()
    private val _availableWorkouts = MutableStateFlow<List<Workout>>(emptyList())
    val availableWorkouts: StateFlow<List<Workout>> = _availableWorkouts.asStateFlow()
    private var tickJob: Job? = null

    init {
        viewModelScope.launch {

        }
    }

    fun startWorkout() {
        startTicking()
        scheduler.start()
    }

    fun pauseResume() {
        scheduler.pauseResume()
    }

    fun reset() {
        scheduler.reset()
        stopTicking()
        _uiState.value = WorkoutUiState()
    }

    fun resync(time: Duration) {
        scheduler.resync(time)
    }

    fun loadAvailableWorkouts() {
        viewModelScope.launch(Dispatchers.IO) {
            _availableWorkouts.value = try {
                listWorkouts()
            } catch (e: Exception) {
                println("Failed to load workouts: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    fun uploadWorkoutFromFile(content: String, fileName: String) {
        viewModelScope.launch {
            try {
                val newWorkout = WorkoutParser().parseAndUpload(content, fileName)
                loadAvailableWorkouts()
                selectWorkout(newWorkout.id)
                setPickerVisible(false)
            } catch (e: Exception) {
                println("Failed to upload workout: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun setPickerVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isPickerVisible = visible)
    }

    fun selectWorkout(workoutId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            scheduler.workoutIdSelected(workoutId)
            _uiState.value = _uiState.value.copy(
                workoutState = scheduler.getState(),
                isLoading = false
            )
        }
    }



    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (true) {
                try {
                    tick()
                } catch (e: Exception) {
                    // TODO: proper logging once a KMP logging library is chosen.
                    // Defensive: one bad tick shouldn't kill the whole workout loop.
                    println(e)
                }
                delay(tickInterval)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }
    private fun tick() {
        val state = scheduler.getState()
        _uiState.value = WorkoutUiState(
            workoutState = state,
        )
    }

    @Suppress("EmptySuperCall")
    override fun onCleared() {
        stopTicking()
        super.onCleared()
    }
}