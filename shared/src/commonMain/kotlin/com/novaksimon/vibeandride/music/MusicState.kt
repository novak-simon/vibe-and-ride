package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.RecommendationResult
import com.novaksimon.vibeandride.recommendation.SongRecommendation

import com.novaksimon.vibeandride.workout.WorkoutMode

/**
 * Represents the high-level playback state of the music system.
 */
enum class PlaybackState {
    IDLE,
    PLAYING,
    PRELOADING,
    TRANSITIONING,
    ERROR
}

/**
 * Represents the pure application/domain state for the music system.
 *
 * This type is intentionally separate from [MusicUiState] and represents the core
 * domain data independent of UI projection or Android playback implementation details.
 *
 * It is introduced as part of the incremental extraction of music logic from
 * MusicProvider into a more portable representation.
 */
data class MusicState(
    val mode: WorkoutMode? = null,
    val workout: Workout? = null,
    val recommendationBuffer: List<RecommendationResult?> = emptyList(),
    val recommendationIndex: List<Boolean> = emptyList(),
    val songIndex: Int = 0,
    val currentSong: Song? = null,
    val isLiked: Boolean = false,
    val futureIntervalSong: Song? = null,
    val currentIntervalIndex: Int = -1,
    val preloadedSongId: Int? = null,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val songStartPosition: Float = 0f,
    val currentDebug: List<SongRecommendation> = emptyList(),
    val futureDebug: List<SongRecommendation> = emptyList(),
    val playbackErrorCount: Int = 0,
)
