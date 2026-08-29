package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.SongSegment
import com.novaksimon.vibeandride.recommendation.ProfileRepository
import com.novaksimon.vibeandride.recommendation.SongRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ProfileStatsState holds the state related to profile statistics for the current track.
 */
data class ProfileStatsState(
    val isLiked: Boolean = false,
)

/**
 * ProfileStatsEngine handles tracking of user profile statistics related to music playback.
 * It is designed to be detached from the main MusicEngine and observe or be notified of playback events.
 */
class ProfileStatsEngine(
    private val profileRepository: ProfileRepository,
    private val songRepository: SongRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val _state = MutableStateFlow(ProfileStatsState())
    val state: StateFlow<ProfileStatsState> = _state.asStateFlow()

    private var currentSong: Song? = null
    private var segments: List<SongSegment> = emptyList()
    private var currentPosition: Float = 0f
    
    private var startPosition: Float = 0f
    private var playCountIncremented: Boolean = false
    private var playTimeRecorded: Boolean = false
    private var segmentFetchJob: Job? = null

    /**
     * Called when the engine is updated with the current playback position and timing information.
     */
    fun update(currentPosition: Float, timeUntilTrackEnd: Float, remainingInInterval: Float) {
        this.currentPosition = currentPosition
        val song = currentSong ?: return
        
        if (!playCountIncremented && currentPosition - startPosition >= 10f) {
            playCountIncremented = true
            scope.launch(ioDispatcher) {
                profileRepository.incrementStat("play_count", song.id)
            }
        }

        if (!playTimeRecorded && (timeUntilTrackEnd <= 2f || remainingInInterval <= 2f)) {
            playTimeRecorded = true
            val playTimeSeconds = (currentPosition - startPosition).toInt().coerceAtLeast(0)
            if (playTimeSeconds > 0) {
                scope.launch(ioDispatcher) {
                    profileRepository.incrementStat("total_play_time", song.id, incrementBy = playTimeSeconds)
                }
            }
        }
    }

    /**
     * Called when a new track starts playing.
     */
    fun handleTrackArrived(song: Song, startPos: Float, isRetry: Boolean = false) {
        if (!isRetry || song.id != currentSong?.id) {
            currentSong = song
            playCountIncremented = false
            playTimeRecorded = false
            
            segmentFetchJob?.cancel()
            segments = emptyList()
            
            // Fetch segments for the new song
            segmentFetchJob = scope.launch(ioDispatcher) {
                val result = songRepository.fetchSongSegments(song.id)
                if (currentSong?.id == song.id) {
                    segments = result.getOrDefault(emptyList())
                }
            }
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
    fun handleTrackLeft(song: Song, startPosition: Float, endPosition: Float, skipped: Boolean) {
        val playTimeSeconds = (endPosition - startPosition).toInt().coerceAtLeast(0)
        val alreadyRecorded = playTimeRecorded

        scope.launch(ioDispatcher) {
            if (skipped) {
                profileRepository.incrementStat("skip_count", song.id)
                profileRepository.setStatDatetime("last_skipped_at", song.id)
            }
            
            if (!alreadyRecorded && playTimeSeconds > 0) {
                profileRepository.incrementStat("total_play_time", song.id, incrementBy = playTimeSeconds)
            }
        }
    }

    /**
     * Called when the user likes or unlikes a song.
     */
    fun handleSongLiked(song: Song, liked: Boolean) {
        _state.update { it.copy(isLiked = liked) }
        
        // Find current segment
        val currentSegment = segments.find { currentPosition >= it.startTime && currentPosition <= it.endTime }
        
        scope.launch(ioDispatcher) {
            profileRepository.changeLikeState(
                songId = song.id,
                targetState = liked,
                segmentIndex = currentSegment?.segmentIndex
            )
        }
    }

    /**
     * Resets the engine state, e.g. at the start of a session.
     */
    fun reset() {
        _state.update { ProfileStatsState() }
        segmentFetchJob?.cancel()
        currentSong = null
        segments = emptyList()
        currentPosition = 0f
        startPosition = 0f
        playCountIncremented = false
        playTimeRecorded = false
    }
}
