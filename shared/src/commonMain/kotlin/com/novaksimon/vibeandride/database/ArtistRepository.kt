package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawArtistV2
import io.github.jan.supabase.postgrest.postgrest

suspend fun getArtistNameById(id: Int): String? {
    val rawArtist = client.postgrest["artists_v2"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawArtistV2>()
    return rawArtist?.name
}
