package com.novaksimon.vibeandride.server.analysis

import com.novaksimon.vibeandride.database.SupabaseSongRepository
import com.novaksimon.vibeandride.database.getSongV2ById
import com.novaksimon.vibeandride.util.VibeLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

@Serializable
data class SongId(
    val id: Long
)
class AudioFeatureExtraction(
    private val supabase: SupabaseClient
) {
    private val audioFeatureExtractor =
        AudioFeatureExtractor(scriptPath = "C:/Users/novak/Documents/vibe-and-ride/audioAnalysis/audio_features.py")
    private val songRepository = SupabaseSongRepository()

    suspend fun analyzeUnanalyzedSongs() {
        val songs = supabase
            .postgrest.rpc("get_unanalyzed_song_ids")
            .decodeList<SongId>()

        val songIds = songs.map { it.id }
        for (songId in songIds) {
            analyzeSong(songId.toInt())
        }
    }
    suspend fun analyzeSong(songId: Int) {
        val song =
            getSongV2ById(songId) ?: throw IllegalArgumentException("Song with ID $songId not found")
        val streamUrl = songRepository.getAudioUrlV2(song).getOrNull()
        if (streamUrl != null) {
            val audioFeatures = audioFeatureExtractor.extract(streamUrl, song.duration)
            audioFeatures.songId = songId
            println("Audio features: $audioFeatures")
            VibeLog.i("AudioFeatureExtraction", "Uploading audio features: $audioFeatures")
            VibeLog.i("AudioFeatureExtraction", "Uploading audio features to Supabase")
            supabase
                .from("audio_features")
                .upsert(audioFeatures)
        } else VibeLog.e("AudioFeatureExtraction", "FAILED TO GET STREAM URL")

    }
}