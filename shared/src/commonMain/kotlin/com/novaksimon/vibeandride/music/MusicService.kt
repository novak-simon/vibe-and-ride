package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import kotlin.time.Duration


class UserMusicService(audioPlayer1: AudioPlayer) {

    private val audioPlayer: AudioPlayer = audioPlayer1

    suspend fun fetchSongs(limit: Int): List<Song> {
        return com.novaksimon.vibeandride.database.fetchSongs(limit)
    }
    suspend fun getPlayableSongs(): List<Song> {
        return com.novaksimon.vibeandride.database.getPlayableSongs()
    }
    suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int = 5,
        limit: Int = 20,
    ): List<Song> {
        return com.novaksimon.vibeandride.database.getSongsAroundTargetCadence(
            targetAlgorithmCadence,
            algorithmCadenceToleration,
            limit
        )
    }
    fun play(song: Song): Int {
        println("playing $song")
        if (song.audioUrl != null) {
            println(song.audioUrl)
            audioPlayer.setSource(song.audioUrl, 0f)
            audioPlayer.play()
            return 1
        }
        return 0
    }
    fun approachingTrackEnd(): Boolean {
        var position = 0f
        var duration = 0f
        audioPlayer.positionSeconds().let { position = audioPlayer.positionSeconds() ?: 0f }
        audioPlayer.durationSeconds().let { duration = audioPlayer.durationSeconds() ?: 0f }
        val value = (duration - position)
        return value < 5 && value > 4
    }
}