package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.SongRepository
import com.novaksimon.vibeandride.util.VibeLog

class ProfileMusicService(
    private val playbackController: PlaybackController,
    private val songRepository: SongRepository
) {
    private val TAG = "ProfileMusicService"

    private var informedAboutTrackEnd = false
    private var currentSong: Song? = null
    private var preloadedAudioUrl: String? = null
    private var preloadedSongId: Int? = null

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

    fun currentPosition(): Float {
        return playbackController.positionSeconds() ?: 0f
    }

    suspend fun preloadSong(song: Song): Boolean {
        val audioUrl: String? = getAudioUrl(song)
        if (!audioUrl.isNullOrEmpty()) {
            preloadedAudioUrl = audioUrl
            preloadedSongId = song.id
            playbackController.preloadSource(song, audioUrl)
            return true
        } else {
            VibeLog.w(TAG, "Failed to preload song ${song.id}: audio URL is null or empty")
            return false
        }
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

        return if (remaining <= 0f) {
            informedAboutTrackEnd = true
            0f
        } else {
            remaining
        }
    }

    suspend fun crossfadeTo(
        song: Song,
        durationMs: Long = 1000L
    ): Boolean {
        informedAboutTrackEnd = false
        currentSong = song

        // Only use preloaded URL if it matches the song we are transitioning to
        val audioUrl = if (preloadedSongId == song.id) {
            preloadedAudioUrl ?: getAudioUrl(song)
        } else {
            getAudioUrl(song)
        }

        // Clear preload state after use (or discard)
        preloadedAudioUrl = null
        preloadedSongId = null

        if (audioUrl == null) {
            VibeLog.e(TAG, "Failed to crossfade to song ${song.id}: audio URL is null")
            return false
        }

        playbackController.preloadSource(song, audioUrl)

        playbackController.crossfadeToPreloaded(
            song = song,
            audioUrl = audioUrl,
            durationMs = durationMs
        )
        return true
    }

    suspend fun getAudioUrl(song: Song): String? {
        return songRepository.getAudioUrl(song).getOrNull()
    }
}
