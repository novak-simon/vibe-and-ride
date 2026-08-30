package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.api.getAudiusStreamUrl
import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.model.VibeAndRideError
import com.novaksimon.vibeandride.recommendation.SongRepository
import com.novaksimon.vibeandride.util.VibeLog
private const val TAG = "SupabaseSongRepository"

class SupabaseSongRepository : SongRepository {

    override suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int,
        limit: Int
    ): Result<List<SongV2>> {
        return try {
            val songs = com.novaksimon.vibeandride.database.getSongsAroundTargetCadence(
                targetAlgorithmCadence,
                algorithmCadenceToleration,
                limit
            )
            Result.success(songs)
        } catch (e: Exception) {
            VibeLog.e(TAG, "Failed to get songs around cadence: $targetAlgorithmCadence", e)
            Result.failure(
                VibeAndRideError.DataError.DatabaseError(
                    "getSongsAroundTargetCadence failed",
                    e
                )
            )
        }
    }

    override suspend fun fetchSongs(limit: Int): Result<List<SongV2>> {
        return try {
            VibeLog.d(TAG, "Fetching songs with limit: $limit")
            val songs = com.novaksimon.vibeandride.database.fetchSongs(limit)
            Result.success(songs)
        } catch (e: Exception) {
            VibeLog.e(TAG, "Failed to fetch songs", e)
            Result.failure(VibeAndRideError.DataError.DatabaseError("fetchSongs failed", e))
        }
    }

    override suspend fun getAudioUrlV2(song: SongV2): Result<String> {
        try {
            var url: String? = null
            if (song.provider == 1) {
                url = getAudiusStreamUrl(
                    song.audiusProviderId
                        ?: return Result.failure(VibeAndRideError.MusicError.AudioUrlNotFound(song.id.toString()))
                )
            } else if (song.provider == 2) {
                song.jamendoProviderId.let { id ->
                    url = if (id != null) {
                        getJamendoAudioUrlById(id)
                    } else {
                        null
                    }
                }
            }
            return if (url != null) {
                Result.success(url)
            } else {
                Result.failure(VibeAndRideError.MusicError.AudioUrlNotFound(song.id.toString()))
            }
        } catch (e: Exception) {
            VibeLog.e(TAG, "Failed to get audio URL for song ${song.id}", e)
            return Result.failure(
                VibeAndRideError.DataError.DatabaseError(
                    "getAudioUrlV2 failed",
                    e
                )
            )
        }
    }

    override suspend fun getArtistNamesByIds(ids: List<Int>): Result<Map<Int, String>> {
        return try {
            val names = com.novaksimon.vibeandride.database.getArtistNamesByIds(ids)
            Result.success(names)
        } catch (e: Exception) {
            VibeLog.e(TAG, "Failed to batch fetch artists", e)
            Result.failure(
                VibeAndRideError.DataError.DatabaseError(
                    "getArtistNamesByIds failed",
                    e
                )
            )
        }
    }
}


