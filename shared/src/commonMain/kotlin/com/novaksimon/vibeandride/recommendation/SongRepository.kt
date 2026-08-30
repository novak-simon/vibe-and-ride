package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.SongV2

interface SongRepository {
    suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int,
        limit: Int
    ): Result<List<SongV2>>

    suspend fun fetchSongs(limit: Int): Result<List<SongV2>>

    suspend fun getAudioUrlV2(song: SongV2): Result<String>
}
