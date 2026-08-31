package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.recommendation.ProfileRepository
import com.novaksimon.vibeandride.recommendation.SongRepository
import com.novaksimon.vibeandride.recommendation.clamp01
import com.novaksimon.vibeandride.util.VibeLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ProfileStatsState holds the state related to profile statistics for the current track.
 */
data class ProfileStatsState(
    val isLiked: Boolean = false,
)
private const val TAG = "ProfileStatsEngine"

/**
 * ProfileStatsEngine handles tracking of user profile statistics related to music playback.
 * It is designed to be detached from the main MusicEngine and observe or be notified of playback events.
 */
class ProfileStatsEngine(
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val _state = MutableStateFlow(ProfileStatsState())
    val state: StateFlow<ProfileStatsState> = _state.asStateFlow()

    private var currentSong: SongV2? = null
    private var currentPosition: Float = 0f
    
    private var startPosition: Float = 0f
    private var playCountIncremented: Boolean = false
    private var playTimeRecorded: Boolean = false

    /**
     * Called when the engine is updated with the current playback position and timing information.
     */
    fun update(currentPosition: Float, timeUntilTrackEnd: Float, remainingInInterval: Float) {
        this.currentPosition = currentPosition
        val song = currentSong ?: return
        
        if (!playCountIncremented && currentPosition - startPosition >= 10f) {
            playCountIncremented = true
            scope.launch(ioDispatcher) {
                profileRepository.incrementStatInt("play_count", song.id)
            }
        }

        if (!playTimeRecorded && (timeUntilTrackEnd <= 2f || remainingInInterval <= 2f)) {
            playTimeRecorded = true
            val playTimeSeconds = (currentPosition - startPosition).toInt().coerceAtLeast(0)
            if (playTimeSeconds > 0) {
                scope.launch(ioDispatcher) {
                    profileRepository.incrementStatInt("total_play_time", song.id, incrementBy = playTimeSeconds)
                }
            }
        }
    }

    /**
     * Called when a new track starts playing.
     */
    fun handleTrackArrived(song: SongV2, startPos: Float, isRetry: Boolean = false) {
        if (!isRetry || song.id != currentSong?.id) {
            currentSong = song
            playCountIncremented = false
            playTimeRecorded = false
            
        }
        startPosition = startPos

        scope.launch(ioDispatcher) {
            profileRepository.setStatDatetime("last_played_at", song.id)
            
            // Refresh like state for the new song
            val songStats = profileRepository.getStatByProfileAndSongId(songId = song.id)
            _state.update { it.copy(isLiked = songStats?.isLiked ?: false) }
        }
    }

    /**
     * Called when a track stops playing (finished or skipped).
     */
    fun handleTrackLeft(song: SongV2, startPosition: Float, endPosition: Float, skipped: Boolean) {
        val playTimeSeconds = (endPosition - startPosition).toInt().coerceAtLeast(0)
        val alreadyRecorded = playTimeRecorded

        scope.launch(ioDispatcher) {
            if (skipped) {
                val effectiveSongDuration = song.duration - song.startTime - song.endTime
                val skipPercentage = clamp01(playTimeSeconds/effectiveSongDuration)
                val skipCoefficient = skipWeight(skipPercentage)
                VibeLog.d(TAG, "Skip coefficient: $skipCoefficient for song ${song.title} (skip percentage: $skipPercentage)")
                profileRepository.incrementStatFloat("skip_count", song.id, incrementBy = skipCoefficient)
                profileRepository.setStatDatetime("last_skipped_at", song.id)
            }
            
            if (!alreadyRecorded && playTimeSeconds > 0) {
                profileRepository.incrementStatInt("total_play_time", song.id, incrementBy = playTimeSeconds)
            }
        }
    }

    /**
     * Called when the user likes or unlikes a song.
     */
    fun handleSongLiked(song: SongV2, liked: Boolean) {
        _state.update { it.copy(isLiked = liked) }
        scope.launch(ioDispatcher) {
            profileRepository.changeLikeState(
                songId = song.id,
                targetState = liked
            )
        }
    }

    /**
     * Resets the engine state, e.g. at the start of a session.
     */
    fun reset() {
        _state.update { ProfileStatsState() }
        currentSong = null
        currentPosition = 0f
        startPosition = 0f
        playCountIncremented = false
        playTimeRecorded = false
    }

    fun skipWeight(x: Float): Float { // Simplified so it doesn't use power, math equation: f(x) = (-2|x-1/2|)^(7)+1
        val base = -2f * abs(x - 0.5f)
        val base3 = base * base * base
        val base7 = base3 * base3 * base
        return base7 + 1f
    }
    fun updateTasteVector(oldVector: DoubleArray, newSongVector: DoubleArray, alpha: Double = 0.1): DoubleArray {
        return DoubleArray(oldVector.size) { i ->
            oldVector[i] * (1 - alpha) + newSongVector[i] * alpha
        }
    }
}
