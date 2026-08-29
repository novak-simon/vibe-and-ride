package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.RecommendationV2State
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
 */
data class MusicState(
    val mode: WorkoutMode? = null,
    val workout: Workout? = null,
    val songIndex: Int = 0,
    val currentSong: Song? = null,
    val isLiked: Boolean = false,
    val currentIntervalIndex: Int = -1,
    val preloadedSongId: Int? = null,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val songStartPosition: Float = 0f,
    val playbackErrorCount: Int = 0,
    val lastTransitionTimeMillis: Long = 0,
    val recommendationV2: RecommendationV2State = RecommendationV2State(),
    val failedSongIds: Set<Int> = emptySet(),
)
