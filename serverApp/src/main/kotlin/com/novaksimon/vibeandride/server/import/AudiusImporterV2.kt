package com.novaksimon.vibeandride.server.import

import com.novaksimon.vibeandride.util.VibeLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.min

private const val TAG = "AudiusImporterV2"
private const val LIMIT = 100
private const val OFFSET_LIMIT = 200

class AudiusImporterV2(private val supabase: SupabaseClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()
    private val trendingEndpoints = listOf(
        "https://api.audius.co/v1/tracks/trending?timeframe=week&limit=100",
        "https://api.audius.co/v1/tracks/trending?timeframe=month&limit=100",
        "https://api.audius.co/v1/tracks/trending?timeframe=year&limit=100",
        "https://api.audius.co/v1/tracks/trending?timeframe=allTime&limit=100",
        "https://api.audius.co/v1/tracks/trending/underground?limit=100",

        )

    suspend fun fetchAudiusSongs(latest: Boolean = false, limit: Int = LIMIT, offset: Int = 0) {
        var fetchedSongs = mutableListOf<AudiusSongFromApi>()
        var index = 0
        var endpoints = mutableListOf<String>()
        if (latest) {
            val iterations = min(limit / LIMIT, OFFSET_LIMIT/LIMIT + 1)
            for (i in 0 until iterations) {
                endpoints.add("https://api.audius.co/v1/tracks/latest?limit=$LIMIT&offset=${i * LIMIT + offset}")
            }
        } else {
            endpoints = trendingEndpoints.toMutableList()
        }

        withContext(Dispatchers.IO) {
            for (endpoint in endpoints) {
                val request = Request.Builder()
                    .url(endpoint)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    VibeLog.e(TAG, "Failed to fetch from $endpoint: ${response.code}")
                    continue
                }

                val responseBody = response.body.string()
                val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject

                val songs = jsonResponse["data"]?.jsonArray ?: continue
                File("C:/Users/novak/Downloads/audius_debug_$index.json").writeText(songs.toString())
                for (song in songs) {
                    val audiusSongFromApi: AudiusSongFromApi = json.decodeFromString<AudiusSongFromApi>(song.toString())
                    fetchedSongs = (fetchedSongs + audiusSongFromApi).distinctBy {it.audiusId} as MutableList<AudiusSongFromApi>
                }
                index++
            }
            var toUpload: List<AudiusSong> = emptyList()
            for (fetched in fetchedSongs) {
                if (!licensePass(fetched.license)) {
                    VibeLog.d(TAG, "fetchAudiusSongs: Skipping song ${fetched.title} due to license ${fetched.license}")
                    continue
                } else if (!fetched.isStreamable) {
                    VibeLog.d(TAG, "fetchAudiusSongs: Skipping song ${fetched.title} due to not being streamable")
                    continue
                } else {
                    VibeLog.d(TAG, "fetchAudiusSongs: Uploading song ${fetched.title}")
                    toUpload = (toUpload + fetched.toAudiusSong()).distinctBy {it.audiusId}
                }
            }
            uploadAudiusSongs(toUpload)
            VibeLog.i(TAG, "fetchAudiusSongs: Uploaded ${toUpload.size} songs")
        }

    }
    suspend fun uploadAudiusSongs(audiusSongs: List<AudiusSong>) {
        supabase.from("audius_songs").upsert(audiusSongs)
    }
    fun licensePass(rawLicense: String? = null): Boolean {
        val license = rawLicense?.lowercase() ?: return true
        if (license.contains("nc") || license.contains("nd")) return false
        return license.contains("cc0") || license.contains("cc-by") || license.contains("cc-by-sa") || license.contains("creative commons attribution") || license.contains("creative commons attribution-sharealike") || license.contains("public domain")
    }

}