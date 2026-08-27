package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawArtistData
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