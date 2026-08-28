package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.recommendation.SongRepository

class SupabaseSongRepository : SongRepository {
    override suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int,
        limit: Int
    ): List<Song> {
        return com.novaksimon.vibeandride.database.getSongsAroundTargetCadence(
            targetAlgorithmCadence,
            algorithmCadenceToleration,
            limit
        )
    }

    override suspend fun fetchSongs(limit: Int): List<Song> {
        return com.novaksimon.vibeandride.database.fetchSongs(limit)
    }

    override suspend fun getAudioUrl(song: Song): String? {
        val isJamendo = song.shareUrl.contains("jamendo.com")
        val isAudius = song.shareUrl.contains("audius.co")

        if (isJamendo) {
            return try {
                getJamendoAudioUrlBySongId(song.id)
            } catch (e: Exception) {
                println("SupabaseSongRepository: Failed to get Jamendo URL for song ${song.id} (${song.title}): ${e.message}")
                null
            }
        }

        if (isAudius) {
            return try {
                getAudiusAudioUrlBySongId(song.id)
            } catch (e: Exception) {
                println("SupabaseSongRepository: Failed to get Audius URL for song ${song.id} (${song.title}): ${e.message}")
                null
            }
        }

        // Fallback for older data or unknown sources
        val jamendo = try { 
            getJamendoAudioUrlBySongId(song.id) 
        } catch (e: Exception) { 
            println("SupabaseSongRepository: Fallback Jamendo failed for song ${song.id}: ${e.message}")
            null 
        }
        if (jamendo != null) return jamendo
        
        val audius = try { 
            getAudiusAudioUrlBySongId(song.id) 
        } catch (e: Exception) { 
            println("SupabaseSongRepository: Fallback Audius failed for song ${song.id}: ${e.message}")
            null 
        }
        if (audius != null) return audius

        return null
    }
}
