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
import com.novaksimon.vibeandride.api.AudiusNodeRegistry
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName

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
    @SerialName("release_date") val releaseDate: String? = null,
    val user: AudiusUser,
    @SerialName("play_count") val playCount: Long? = null,
    @SerialName("repost_count") val repostCount: Long? = null,
    @SerialName("favorite_count") val favoriteCount: Long? = null,
    @SerialName("comment_count") val commentCount: Long?,
    val artwork: JsonElement? = null,
    @SerialName("track_cid") val trackCid: String? = null,
    @SerialName("permalink") val permalink: String? = null,
    @SerialName("is_downloadable") val isDownloadable: Boolean = false,
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
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun importTrending(limit: Int = 50, offset: Int = 0) {
        val endpoints = listOf(
            "tracks/trending",
            "tracks/latest",
            "tracks/trending/underground"
        )

        val maxPageSize = 100 // Reduced from 500 to 100 for better compatibility
        var remaining = limit
        var currentOffset = offset
        var endpointIndex = 0

        println("AudiusImporter: Starting general import of $limit tracks (global starting offset: $offset)...")

        val nodes = AudiusNodeRegistry.getNodes()
        if (nodes.isEmpty()) {
            println("AudiusImporter: No Audius nodes found!")
            return
        }

        while (remaining > 0 && endpointIndex < endpoints.size) {
            val endpoint = endpoints[endpointIndex]
            
            // Trending endpoints usually have a fixed limit of 100 and no offset support
            val isTrending = endpoint.contains("trending")
            val pageSize = if (isTrending) minOf(remaining, 100) else minOf(remaining, maxPageSize)
            val apiOffset = if (isTrending) 0 else currentOffset

            println("AudiusImporter: Fetching batch from '$endpoint' (limit: $pageSize, offset: $apiOffset)...")

            var tracksFetched = false
            // Try nodes sequentially until one works
            for (node in nodes.take(3)) {
                val host = node.replace("https://", "").replace("http://", "")
                val url = "https://$host/v1/$endpoint"

                val responseBody: JsonElement = try {
                    httpClient.get(url) {
                        parameter("app_name", appName)
                        parameter("limit", pageSize)
                        if (apiOffset > 0) parameter("offset", apiOffset)
                    }.body()
                } catch (e: Exception) {
                    println("AudiusImporter: Node $host failed for '$endpoint': ${e.message}")
                    continue
                }

                val tracks = try {
                    when (responseBody) {
                        is JsonArray -> json.decodeFromJsonElement<List<AudiusTrack>>(responseBody)
                        is JsonObject -> {
                            val dataField = responseBody["data"]
                            if (dataField is JsonArray) {
                                json.decodeFromJsonElement<List<AudiusTrack>>(dataField)
                            } else {
                                // Log keys to debug missing 'data'
                                println("AudiusImporter: No 'data' array in $host response for '$endpoint'. Keys: ${responseBody.keys}")
                                if (responseBody.containsKey("error")) {
                                    println("AudiusImporter: API Error from $host: ${responseBody["error"]}")
                                }
                                emptyList()
                            }
                        }
                        else -> emptyList()
                    }
                } catch (e: Exception) {
                    println("AudiusImporter: Failed to decode from node $host: ${e.message}")
                    emptyList()
                }

                if (tracks.isNotEmpty()) {
                    tracks.forEach { track ->
                        try {
                            processTrack(track)
                        } catch (e: Exception) {
                            println("AudiusImporter: Error processing track ${track.id}: ${e.message}")
                        }
                    }

                    remaining -= tracks.size
                    // Only increment the offset for non-trending endpoints that support pagination
                    if (!isTrending) {
                        currentOffset += tracks.size
                    }
                    
                    tracksFetched = true

                    // If we got some tracks but less than requested, or if it's a trending endpoint (fixed 100), move to next
                    if (tracks.size < pageSize || isTrending) {
                        println("AudiusImporter: Endpoint '$endpoint' finished. Moving to next category...")
                        endpointIndex++
                        // Reset currentOffset for the next category if it's a new starting point
                        if (isTrending) {
                            // If we just finished a trending endpoint, we keep the global offset for the first 'paginatable' endpoint
                        } else {
                            currentOffset = 0
                        }
                    }
                    break // Successfully processed a batch from this node
                } else {
                    println("AudiusImporter: Node $host returned empty tracks for '$endpoint'.")
                }
            }

            if (!tracksFetched) {
                println("AudiusImporter: Moving to next category due to fetch failure or empty results for '$endpoint'.")
                endpointIndex++
                currentOffset = 0
            }
        }
        println("AudiusImporter: General import complete. Remaining: $remaining")
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
            put("release_date", track.releaseDate)
            put("bpm", track.bpm)
            put("share_url", "https://audius.co${track.permalink}")
            put("image", extractArtworkUrl(track.artwork))
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
            put("track_cid", track.trackCid)
            put("audius_user_id", track.user.id)
            put("audius_permalink", track.permalink)
            put("audius_mood", track.mood)
            put("audius_favorite_count", track.favoriteCount)
            put("audius_comment_count", track.commentCount)
            put("audius_play_count", track.playCount)
            put("audius_repost_count", track.repostCount)
            putJsonArray("audius_tags") {
                tagsList.forEach { add(it) }
            }
            put("audius_is_downloadable", track.isDownloadable)
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
            println(e)
            artwork.jsonPrimitive.contentOrNull
        }
    }
}
