package com.novaksimon.vibeandride.server.import

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import com.novaksimon.vibeandride.model.*
import com.novaksimon.vibeandride.server.IdRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray

@Serializable
data class JamendoResponse(
    val results: List<JamendoTrack>
)

@Serializable
data class JamendoTrack(
    val id: String,
    val name: String,
    val duration: Int,
    val artist_name: String,
    val album_name: String? = null,
    val release_date: String? = null,
    val audio: String? = null,
    val audiodownload: String? = null,
    val image: String? = null,
    val shareurl: String? = null,
    val musicinfo: JamendoMusicInfo? = null,
    val waveform: String? = null
)

@Serializable
data class JamendoMusicInfo(
    val speed: String? = null,
    val tags: JamendoTags? = null
)

@Serializable
data class JamendoTags(
    val genres: List<String>? = null,
    val vartags: List<String>? = null
)

class JamendoImporter(
    private val supabase: SupabaseClient,
    private val jamendoClientId: String
) {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun import(limit: Int = 10, boost: String = "popularity_total") {
        println("JamendoImporter: Fetching songs...")
        val response: JamendoResponse = httpClient.get("https://api.jamendo.com/v3.0/tracks/") {
            parameter("client_id", jamendoClientId)
            parameter("format", "jsonpretty")
            parameter("limit", limit)
            parameter("include", "musicinfo")
            parameter("boost", boost)
            parameter("groupby", "artist_id")
        }.body()

        println("JamendoImporter: Found ${response.results.size} tracks.")

        response.results.forEach { track ->
            try {
                processTrack(track)
            } catch (e: Exception) {
                println("JamendoImporter: Error processing track ${track.id}: ${e.message}")
            }
        }
    }

    private suspend fun processTrack(track: JamendoTrack) {
        val jamendoId = track.id.toLong()
        
        // Check if exists
        val existing = supabase.from("jamendo_song_data")
            .select(Columns.list("song_id")) {
                filter { eq("jamendo_id", jamendoId) }
            }
            .decodeSingleOrNull<RawJamendoSongData>()

        if (existing != null) {
            println("JamendoImporter: Song ${track.name} already exists, skipping.")
            return
        }

        val artistId = findOrCreate("artists", track.artist_name)
        val albumId = findOrCreate("albums", track.album_name)

        val tags = track.musicinfo?.tags?.vartags ?: emptyList()
        val genres = track.musicinfo?.tags?.genres ?: emptyList()

        val songData = buildJsonObject {
            put("title", track.name)
            put("artist_id", artistId)
            put("album_id", albumId)
            put("duration", track.duration)
            put("genre", genres.joinToString(", "))
            put("mood", tags.firstOrNull())
            put("release_date", track.release_date)
            put("image", track.image)
            put("share_url", track.shareurl ?: "")
            putJsonArray("tags") {
                tags.forEach { add(it) }
            }
        }

        val createdSong = supabase.from("songs")
            .insert(songData) { select() }
            .decodeSingle<RawSong>()

        val songId = createdSong.id

        val peaks = try {
            track.waveform?.let {
                val jsonWaveform = Json.parseToJsonElement(it).jsonObject
                jsonWaveform["peaks"]?.jsonArray?.map { peak -> peak.toString().toInt() }
            }
        } catch (e: Exception) {
            null
        }

        val jamendoData = buildJsonObject {
            put("song_id", songId)
            put("jamendo_id", jamendoId)
            put("audio_url", track.audio)
            put("download_url", track.audiodownload)
            if (peaks != null) {
                putJsonArray("waveform") {
                    peaks.forEach { add(it) }
                }
            }
        }

        supabase.from("jamendo_song_data").insert(jamendoData)
        println("JamendoImporter: Inserted ${track.name}")
    }

    private suspend fun findOrCreate(table: String, name: String?): Int? {
        val trimmedName = name?.trim() ?: return null
        if (trimmedName.isEmpty()) return null

        val existing = supabase.from(table)
            .select(Columns.list("id")) {
                filter { ilike("name", trimmedName) }
            }
            .decodeSingleOrNull<IdRow>()
        
        if (existing != null) return existing.id

        val created = supabase.from(table)
            .insert(buildJsonObject { put("name", trimmedName) }) { select() }
            .decodeSingleOrNull<IdRow>()

        return created?.id
    }
}
