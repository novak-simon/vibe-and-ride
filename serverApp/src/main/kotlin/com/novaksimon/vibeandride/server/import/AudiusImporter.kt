package com.novaksimon.vibeandride.server.import

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import com.novaksimon.vibeandride.model.*
import com.novaksimon.vibeandride.server.IdRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

@Serializable
data class AudiusTrendingResponse(
    val data: List<AudiusTrack>
)

@Serializable
data class AudiusTrack(
    val id: String,
    val title: String,
    val duration: Int,
    val bpm: Int? = null,
    val genre: String? = null,
    val mood: String? = null,
    val tags: String? = null,
    val release_date: String? = null,
    val user: AudiusUser,
    val play_count: Long? = null,
    val repost_count: Long? = null,
    val favorite_count: Long? = null,
    val artwork: JsonElement? = null,
    val track_cid: String? = null,
    val permalink: String? = null
)

@Serializable
data class AudiusUser(
    val id: String,
    val name: String,
    val handle: String
)

class AudiusImporter(
    private val supabase: SupabaseClient,
    private val appName: String = "VIBE_AND_RIDE"
) {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun importTrending(limit: Int = 50) {
        println("AudiusImporter: Fetching trending tracks...")
        val response: AudiusTrendingResponse = httpClient.get("https://api.audius.co/v1/tracks/trending") {
            parameter("app_name", appName)
        }.body()

        println("AudiusImporter: Found ${response.data.size} tracks.")

        response.data.take(limit).forEach { track ->
            try {
                processTrack(track)
            } catch (e: Exception) {
                println("AudiusImporter: Error processing track ${track.id}: ${e.message}")
            }
        }
    }

    private suspend fun processTrack(track: AudiusTrack) {
        // Check if exists
        val existing = supabase.from("audius_song_data")
            .select(Columns.list("song_id")) {
                filter { eq("audius_id", track.id) }
            }
            .decodeSingleOrNull<RawAudiusSongData>()

        if (existing != null) {
            println("AudiusImporter: Song ${track.title} already exists, skipping.")
            return
        }

        val artistId = findOrCreateArtist(track.user.name)

        val tagsList = track.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val songData = buildJsonObject {
            put("title", track.title)
            put("artist_id", artistId)
            put("duration", track.duration)
            put("genre", track.genre)
            put("mood", track.mood)
            put("release_date", track.release_date)
            put("bpm", track.bpm)
            put("share_url", "https://audius.co${track.permalink}")
            putJsonArray("tags") {
                tagsList.forEach { add(it) }
            }
        }

        val createdSong = supabase.from("songs")
            .insert(songData) { select() }
            .decodeSingle<RawSong>()

        val songId = createdSong.id

        val audiusData = buildJsonObject {
            put("song_id", songId)
            put("audius_id", track.id)
            put("track_cid", track.track_cid)
            put("audius_user_id", track.user.id)
            put("title", track.title)
            put("artist", track.user.name)
            put("release_date", track.release_date)
            put("duration", track.duration.toString())
            put("play_count", track.play_count)
            put("repost_count", track.repost_count)
            putJsonArray("audius_tags") {
                tagsList.forEach { add(it) }
            }
            put("cover_art_url", extractArtworkUrl(track.artwork))
        }

        supabase.from("audius_song_data").insert(audiusData)
        println("AudiusImporter: Inserted ${track.title}")
    }

    private suspend fun findOrCreateArtist(name: String): Int? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null

        val existing = supabase.from("artists")
            .select(Columns.list("id")) {
                filter { ilike("name", trimmedName) }
            }
            .decodeSingleOrNull<IdRow>()
        
        if (existing != null) return existing.id

        val created = supabase.from("artists")
            .insert(buildJsonObject { put("name", trimmedName) }) { select() }
            .decodeSingleOrNull<IdRow>()

        return created?.id
    }

    private fun extractArtworkUrl(artwork: JsonElement?): String? {
        if (artwork == null || artwork is JsonNull) return null
        return try {
            val obj = artwork.jsonObject
            obj["1000x1000"]?.jsonPrimitive?.content
                ?: obj["480x480"]?.jsonPrimitive?.content
                ?: obj["150x150"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            artwork.jsonPrimitive.contentOrNull
        }
    }
}
