package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.AudioFeatures
import io.github.jan.supabase.postgrest.postgrest

suspend fun getAudioFeaturesById(id: Int): AudioFeatures? {
    val rawFeatures = client.postgrest["audio_features"]
        .select {
            filter { eq("song_id", id) }
            limit(1)
        }
        .decodeSingleOrNull<AudioFeatures>()
    return rawFeatures
}