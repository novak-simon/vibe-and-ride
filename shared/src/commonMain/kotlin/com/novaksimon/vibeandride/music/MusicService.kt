package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song

class UserMusicService(
    private val playbackController: PlaybackController
) {

    private var informedAboutTrackEnd = false

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

        playbackController.setSource(
            song.audioUrl,
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

    fun approachingTrackEnd(): Boolean {
        if (informedAboutTrackEnd) {
            return false
        }

        val position =
            playbackController.positionSeconds() ?: 0f

        val duration =
            playbackController.durationSeconds() ?: 0f

        val remaining = duration - position

        val result = remaining < 4f && remaining > 3f

        if (result) {
            informedAboutTrackEnd = true
        }

        return result
    }

    fun crossfadeTo(
        song: Song,
        durationMs: Long = 3000L
    ) {
        informedAboutTrackEnd = false

        playbackController.preloadSource(
            song.audioUrl,
            0f
        )

        playbackController.crossfadeToPreloaded(
            durationMs
        )
    }
}