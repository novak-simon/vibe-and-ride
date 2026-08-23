package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song

class ProfileMusicService(
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

        val duration =
            playbackController.durationSeconds() ?: 0f

        if (duration <= 0f) {
            // If duration is unknown, we assume it's not ending soon to avoid premature preloading.
            return 3600f
        }

        val remaining = duration - position

        return remaining
    }

    fun crossfadeTo(
        song: Song,
        durationMs: Long = 1000L
    ) {
        informedAboutTrackEnd = false

        playbackController.preloadSource(song)

        playbackController.crossfadeToPreloaded(
            song = song,
            durationMs = durationMs
        )
    }
}
