package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.SongRepository
import kotlinx.coroutines.flow.Flow

class ProfileMusicService(
    private val playbackController: PlaybackController,
    private val songRepository: SongRepository
) {

    val isPlayingFlow: Flow<Boolean> = playbackController.isPlayingFlow

    private var informedAboutTrackEnd = false
    private var currentSong: Song? = null
    private var preloadedAudioUrl: String? = null

    suspend fun fetchSongs(limit: Int): List<Song> {
        return songRepository.fetchSongs(limit)
    }

    suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int = 5,
        limit: Int = 20
    ): List<Song> {
        return songRepository.getSongsAroundTargetCadence(
            targetAlgorithmCadence,
            algorithmCadenceToleration,
            limit
        )
    }

    fun pauseResume() {
        if (playbackController.isPlaying()) {
            playbackController.pause()
        } else {
            playbackController.play()
        }
    }

    fun play() {
        playbackController.play()
    }

    fun pause() {
        playbackController.pause()
    }

    fun isPlaying(): Boolean {
        return playbackController.isPlaying()
    }

    suspend fun preloadSong(song: Song): Boolean {
        val audioUrl: String? = getAudioUrl(song)
        if (!audioUrl.isNullOrEmpty()) {
            preloadedAudioUrl = audioUrl
            playbackController.preloadSource(song, audioUrl)
            return true
        }
        else return false
    }

    fun timeUntilTrackEnd(): Float {
        if (informedAboutTrackEnd) {
            return 0f
        }

        val position =
            playbackController.positionSeconds() ?: 0f

        val song = currentSong ?: return 3600f
        val duration = song.duration.toFloat()
        val endTime = song.endTime

        val treatedEnd = duration - endTime
        val remaining = treatedEnd - position

        return if (remaining < 0f) 0f else remaining
    }

    suspend fun crossfadeTo(
        song: Song,
        durationMs: Long = 1000L
    ): Boolean {
        informedAboutTrackEnd = false
        currentSong = song
        val audioUrl = preloadedAudioUrl ?: getAudioUrl(song) ?: return false
        playbackController.preloadSource(song, audioUrl)

        playbackController.crossfadeToPreloaded(
            song = song,
            audioUrl = audioUrl,
            durationMs = durationMs
        )
        return true
    }

    suspend fun getAudioUrl(song: Song): String? {
        return songRepository.getAudioUrl(song)
    }
}
