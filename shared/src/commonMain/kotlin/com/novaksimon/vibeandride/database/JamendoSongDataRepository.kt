package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawJamendoSongData
import io.github.jan.supabase.postgrest.postgrest

suspend fun getJamendoAudioUrlById(id: Int): String? {
    val rawData = client.postgrest["jamendo_song_data"]
        .select {
            filter { eq("jamendo_id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawJamendoSongData>()
    return rawData?.audioUrl
}