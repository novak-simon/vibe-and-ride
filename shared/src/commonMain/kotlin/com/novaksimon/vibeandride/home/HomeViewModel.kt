package com.novaksimon.vibeandride.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaksimon.vibeandride.database.getLikedSongsWithDetails
import com.novaksimon.vibeandride.database.getSuggestedWorkouts
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class WorkoutPlaceholder(
    val name: String,
    val duration: String,
    val rating: String
)

class HomeViewModel : ViewModel() {
    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

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
        loadData()
        startPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(30.seconds)
                loadData()
            }
        }
    }

    fun loadData() {
        loadLikedSongs()
        loadSuggestedWorkouts()
    }

    fun loadSuggestedWorkouts() {
        viewModelScope.launch {
            try {
                _suggestedWorkouts.value = getSuggestedWorkouts()
            } catch (e: Exception) {
                println("Error loading suggested workouts: ${e.message}")
            }
        }
    }

    fun loadLikedSongs() {
        viewModelScope.launch {
            _isLoadingLikedSongs.value = true
            try {
                _likedSongs.value = getLikedSongsWithDetails()
            } catch (e: Exception) {
                println("Error loading liked songs: ${e.message}")
            } finally {
                _isLoadingLikedSongs.value = false
            }
        }
    }
}
