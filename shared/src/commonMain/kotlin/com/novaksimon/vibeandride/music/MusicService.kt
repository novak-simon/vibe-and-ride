package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song


class UserMusicService {

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
}