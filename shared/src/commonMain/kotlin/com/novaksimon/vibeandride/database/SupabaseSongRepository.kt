package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.VibeAndRideError
import com.novaksimon.vibeandride.recommendation.SongRepository
import com.novaksimon.vibeandride.util.VibeLog

class SupabaseSongRepository : SongRepository {
    private val TAG = "SupabaseSongRepository"

    override suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int,
        limit: Int
    ): Result<List<Song>> {
        return try {
            val songs = com.novaksimon.vibeandride.database.getSongsAroundTargetCadence(
                targetAlgorithmCadence,
                algorithmCadenceToleration,
                limit
            )
            Result.success(songs)
        } catch (e: Exception) {
            VibeLog.e(TAG, "Failed to get songs around cadence: $targetAlgorithmCadence", e)
            Result.failure(VibeAndRideError.DataError.DatabaseError("getSongsAroundTargetCadence failed", e))
        }
    }

    override suspend fun fetchSongs(limit: Int): Result<List<Song>> {
        return try {
            val songs = com.novaksimon.vibeandride.database.fetchSongs(limit)
            Result.success(songs)
        } catch (e: Exception) {
            VibeLog.e(TAG, "Failed to fetch songs", e)
            Result.failure(VibeAndRideError.DataError.DatabaseError("fetchSongs failed", e))
        }
    }

    override suspend fun getAudioUrl(song: Song): Result<String> {
        val isJamendo = song.shareUrl.contains("jamendo.com")
        val isAudius = song.shareUrl.contains("audius.co")

        try {
            if (isJamendo) {
                val url = getJamendoAudioUrlBySongId(song.id)
                if (url != null) return Result.success(url)
            } else if (isAudius) {
                val url = getAudiusAudioUrlBySongId(song.id)
                if (url != null) return Result.success(url)
            }

            // Fallback
            val jamendo = getJamendoAudioUrlBySongId(song.id)
            if (jamendo != null) return Result.success(jamendo)

            val audius = getAudiusAudioUrlBySongId(song.id)
            if (audius != null) return Result.success(audius)

            VibeLog.w(TAG, "Audio URL not found for song ${song.id} (${song.title})")
            return Result.failure(VibeAndRideError.MusicError.AudioUrlNotFound(song.id.toString()))
        } catch (e: Exception) {
            VibeLog.e(TAG, "Failed to get audio URL for song ${song.id}", e)
            return Result.failure(VibeAndRideError.DataError.DatabaseError("getAudioUrl failed", e))
        }
    }

    override suspend fun getArtistNamesByIds(ids: List<Int>): Result<Map<Int, String>> {
        return try {
            val names = com.novaksimon.vibeandride.database.getArtistNamesByIds(ids)
            Result.success(names)
        } catch (e: Exception) {
            VibeLog.e(TAG, "Failed to batch fetch artists", e)
            Result.failure(VibeAndRideError.DataError.DatabaseError("getArtistNamesByIds failed", e))
        }
    }
}

