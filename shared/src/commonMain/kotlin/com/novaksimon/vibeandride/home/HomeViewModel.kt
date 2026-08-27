package com.novaksimon.vibeandride.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaksimon.vibeandride.database.getLikedSongsWithDetails
import com.novaksimon.vibeandride.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    val recentWorkouts = listOf(
        WorkoutPlaceholder("Power Climb", "45 min", "4/5"),
        WorkoutPlaceholder("Endurance Base", "90 min", "5/5"),
        WorkoutPlaceholder("Sprints", "30 min", "3/5")
    )

    val suggestedWorkouts = listOf(
        WorkoutPlaceholder("HIIT Blast", "20 min", "4/5"),
        WorkoutPlaceholder("Tempo Ride", "60 min", "4/5"),
        WorkoutPlaceholder("Recovery Spin", "30 min", "5/5")
    )

    init {
        loadLikedSongs()
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
