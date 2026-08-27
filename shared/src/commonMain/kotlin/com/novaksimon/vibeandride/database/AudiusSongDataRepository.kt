package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.api.getAudiusStreamUrl
import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawAudiusSongData
import com.novaksimon.vibeandride.model.RawJamendoSongData
import io.github.jan.supabase.postgrest.postgrest

suspend fun getAudiusAudioUrlBySongId(songId: Int): String? {
    val rawData = client.postgrest["audius_song_data"]
        .select {
            filter { eq("song_id", songId) }
            limit(1)
        }
        .decodeSingleOrNull<RawAudiusSongData>()
    return getAudiusStreamUrl(rawData?.audiusId ?: return null)
}