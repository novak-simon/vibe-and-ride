package com.novaksimon.vibeandride.api

import com.novaksimon.vibeandride.util.VibeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "AudiusApi"
private const val APP_NAME = "VIBE_AND_RIDE"
private const val API_KEY = "0xfe22f3410fff4707efd27f66e6277ce84105868c"
private val AUDIUS_BEARER_TOKEN = System.getProperty("AUDIUS_BEARER_TOKEN") ?: System.getenv("AUDIUS_BEARER_TOKEN") ?: ""



private val httpClient = OkHttpClient.Builder()
    .followRedirects(true)
    .build()

private val json = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class AudiusTrackResponse(
    val data: AudiusTrack? = null
)

@Serializable
data class AudiusTrack(
    val id: String,
    @SerialName("is_streamable")
    val isStreamable: Boolean = true
)

@Serializable
data class AudiusProvidersResponse(
    val success: Boolean,
    val data: List<String>
)

private const val DEFAULT_AUDIUS_HOST = "api.audius.co"

object AudiusNodeRegistry {
    private var nodes: List<String> = emptyList()
    private var lastFetchTime: Long = 0
    private const val CACHE_DURATION = 1000 * 60 * 60 // 1 hour

    suspend fun getNodes(): List<String> {
        if (nodes.isEmpty() || (System.currentTimeMillis() - lastFetchTime) > CACHE_DURATION) {
            refreshNodes()
        }
        return nodes.ifEmpty { listOf(DEFAULT_AUDIUS_HOST) }
    }

    suspend fun refreshNodes() = withContext(Dispatchers.IO) {
        try {
            val url = "https://$DEFAULT_AUDIUS_HOST/v1/discovery-providers?app_name=$APP_NAME"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", API_KEY)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    val providersResponse = json.decodeFromString<AudiusProvidersResponse>(body)
                    if (providersResponse.success) {
                        nodes = providersResponse.data.shuffled()
                        lastFetchTime = System.currentTimeMillis()
                        VibeLog.d(TAG, "AudiusNodeRegistry: Refreshed ${nodes.size} nodes")
                    }
                }
            }
        } catch (e: Exception) {
            VibeLog.e(TAG, "AudiusNodeRegistry: Failed to refresh nodes", e)
        }
    }
}
suspend fun getAudiusStreamUrlV2(audiusId: String): String? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.audius.co/v1/tracks/$audiusId/stream")
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            println(response)
            return@withContext null
        }
        response.request.url.toString() // final URL after redirect, if that's what you want
    }
}

suspend fun debugGetTrackMetadata(trackId: String) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.audius.co/v1/tracks/$trackId")
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
        val body = response.body.string()
        println("Code: ${response.code}")
        println("Message: ${response.message}")
        println("Body: $body")
    }
}

suspend fun getAudiusStreamUrl(
    audiusId: String,
): String? = withContext(Dispatchers.IO) {
    val candidateNodes = AudiusNodeRegistry.getNodes()
    
    // Try up to 3 different nodes
    for (attempt in 0 until minOf(3, candidateNodes.size)) {
        val host = candidateNodes[attempt].replace("https://", "").replace("http://", "")
        try {
            val url = "https://$host/v1/tracks/$audiusId?app_name=$APP_NAME"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", API_KEY)
                .build()

            val streamUrl = httpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (response.isSuccessful) {
                    val trackResponse = try {
                        json.decodeFromString<AudiusTrackResponse>(body)
                    } catch (e: Exception) {
                        VibeLog.e(TAG, "AudiusApi: Failed to decode track response from $host", e)
                        null
                    }
                    
                    val track = trackResponse?.data
                    if (track != null) {
                        if (!track.isStreamable) {
                            VibeLog.w(TAG, "AudiusApi: Track $audiusId is marked as NOT streamable by metadata")
                            return@use null
                        }

                        val potentialUrl = "https://$host/v1/tracks/$audiusId/stream?app_name=$APP_NAME"
                        
                        // Verify the stream URL actually works
                        if (verifyStreamUrl(potentialUrl)) {
                            return@use potentialUrl
                        } else {
                            VibeLog.w(TAG, "AudiusApi: Stream URL verification failed for track $audiusId on node $host")
                        }
                    } else {
                        VibeLog.w(TAG, "AudiusApi: Track data not found in response from $host for $audiusId")
                    }
                } else {
                    VibeLog.w(TAG, "AudiusApi: Failed to get track metadata from $host: ${response.code} ${response.message}")
                }
                null
            }
            
            if (streamUrl != null) return@withContext streamUrl
            
        } catch (e: Exception) {
            VibeLog.w(TAG, "Audius request failed on $host (attempt ${attempt + 1}): ${e.message}")
        }
        
        if (attempt < 2) {
            delay((300L * (attempt + 1)).milliseconds)
        }
    }
    
    return@withContext null
}

private suspend fun verifyStreamUrl(url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        // We use a GET request but only fetch a tiny range to minimize data usage
        // Some nodes might not support Range, so we also handle the 403 explicitly
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Range", "bytes=0-10")
            .addHeader("x-api-key", API_KEY)
            .build()
            
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 403) {
                val body = response.body.string()
                if (body.contains("track not streamable")) {
                    VibeLog.w(TAG, "verifyStreamUrl: 403 Forbidden - Track not streamable ($url)")
                    return@use false
                }
            } else if (response.code == 503) {
                VibeLog.w(TAG, "verifyStreamUrl: 503 Service Unavailable ($url)")
                AudiusNodeRegistry.refreshNodes()
            }
            // 200 (OK) or 206 (Partial Content) are both good
            val success = response.isSuccessful
            if (!success) {
                VibeLog.w(TAG, "verifyStreamUrl failed for $url: ${response.code} ${response.message}")
            }
            return@use success
        }
    } catch (e: Exception) {
        VibeLog.w(TAG, "Stream URL verification failed for $url: ${e.message}")
        false
    }
}
