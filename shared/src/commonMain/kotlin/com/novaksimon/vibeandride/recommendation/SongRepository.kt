package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.SongSegment

interface SongRepository {
    suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int,
        limit: Int
    ): Result<List<Song>>

    suspend fun fetchSongs(limit: Int): Result<List<Song>>

    suspend fun getAudioUrl(song: Song): Result<String>

    suspend fun getArtistNamesByIds(ids: List<Int>): Result<Map<Int, String>>

    suspend fun fetchSongSegments(songId: Int): Result<List<SongSegment>>
}

