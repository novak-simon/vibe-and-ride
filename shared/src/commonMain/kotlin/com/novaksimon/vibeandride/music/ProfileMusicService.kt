package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import kotlinx.coroutines.flow.Flow

class ProfileMusicService(
    private val playbackController: PlaybackController
) {

    val isPlayingFlow: Flow<Boolean> = playbackController.isPlayingFlow

    private var informedAboutTrackEnd = false
    private var currentSong: Song? = null

    suspend fun fetchSongs(limit: Int): List<Song> {
        return com.novaksimon.vibeandride.database
            .fetchSongs(limit)
    }

    suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int = 5,
        limit: Int = 20
    ): List<Song> {
        return com.novaksimon.vibeandride.database
            .getSongsAroundTargetCadence(
                targetAlgorithmCadence,
                algorithmCadenceToleration,
                limit
            )
    }

    fun playNext(song: Song) {

        informedAboutTrackEnd = false
        currentSong = song

        playbackController.setSource(
            song,
            0f
        )

        playbackController.play()

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

    fun preloadSong(song: Song) {
        playbackController.preloadSource(song)
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

    fun crossfadeTo(
        song: Song,
        durationMs: Long = 1000L
    ) {
        informedAboutTrackEnd = false
        currentSong = song

        playbackController.preloadSource(song)

        playbackController.crossfadeToPreloaded(
            song = song,
            durationMs = durationMs
        )
    }
}
