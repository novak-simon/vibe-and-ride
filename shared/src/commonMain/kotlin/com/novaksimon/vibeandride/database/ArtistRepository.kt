package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawArtistData
import com.novaksimon.vibeandride.util.VibeLog
import io.github.jan.supabase.postgrest.postgrest

suspend fun getArtistNameById(id: Int): String? {
    val rawArtist = client.postgrest["artists"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawArtistData>()
    return rawArtist?.name
}

suspend fun getArtistNamesByIds(ids: List<Int>): Map<Int, String> {
    if (ids.isEmpty()) return emptyMap()
    return try {
        val rawArtists = client.postgrest["artists"]
            .select {
                filter {
                    isIn("id", ids.distinct())
                }
            }
            .decodeList<RawArtistData>()
        rawArtists.associate { (it.id ?: 0) to (it.name ?: "Unknown Artist") }
    } catch (e: Exception) {
        VibeLog.e("ArtistRepository", "Failed to batch fetch artists", e)
        emptyMap()
    }
}
