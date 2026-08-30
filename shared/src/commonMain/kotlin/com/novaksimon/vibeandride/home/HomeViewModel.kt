package com.novaksimon.vibeandride.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaksimon.vibeandride.database.SupabaseClientProvider
import com.novaksimon.vibeandride.database.getLikedSongsWithDetails
import com.novaksimon.vibeandride.database.getSuggestedWorkouts
import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.util.VibeLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class WorkoutPlaceholder(
    val name: String,
    val duration: String,
    val rating: String
)
private const val TAG = "HomeViewModel"
class HomeViewModel : ViewModel() {

    private val _likedSongs = MutableStateFlow<List<SongV2>>(emptyList())
    val likedSongs: StateFlow<List<SongV2>> = _likedSongs.asStateFlow()

    private val _isLoadingLikedSongs = MutableStateFlow(false)
    val isLoadingLikedSongs: StateFlow<Boolean> = _isLoadingLikedSongs.asStateFlow()

    private val _suggestedWorkouts = MutableStateFlow<List<Workout>>(emptyList())
    val suggestedWorkouts: StateFlow<List<Workout>> = _suggestedWorkouts.asStateFlow()

    val recentWorkouts = listOf(
        WorkoutPlaceholder("Power Climb", "45 min", "4/5"),
        WorkoutPlaceholder("Endurance Base", "90 min", "5/5"),
        WorkoutPlaceholder("Sprints", "30 min", "3/5")
    )

    init {
        viewModelScope.launch {
            SupabaseClientProvider.isInitialized.collectLatest { initialized ->
                if (initialized) {
                    loadData()
                }
            }
        }
        startPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(60.seconds)
                if (SupabaseClientProvider.isInitialized.value) {
                    loadData(silent = true)
                }
            }
        }
    }

    fun loadData(silent: Boolean = false) {
        loadLikedSongs(silent)
        loadSuggestedWorkouts()
    }

    fun loadSuggestedWorkouts() {
        viewModelScope.launch {
            try {
                val newWorkouts = getSuggestedWorkouts()
                if (newWorkouts != _suggestedWorkouts.value) {
                    _suggestedWorkouts.value = newWorkouts
                }
            } catch (e: Exception) {
                VibeLog.e(TAG, "Error loading suggested workouts", e)
            }
        }
    }

    fun loadLikedSongs(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent && _likedSongs.value.isEmpty()) {
                _isLoadingLikedSongs.value = true
            }
            try {
                val newSongs = getLikedSongsWithDetails()
                if (newSongs != _likedSongs.value) {
                    _likedSongs.value = newSongs
                }
            } catch (e: Exception) {
                VibeLog.e(TAG, "Error loading liked songs", e)
            } finally {
                _isLoadingLikedSongs.value = false
            }
        }
    }
}
