package com.novaksimon.vibeandride.api

import com.novaksimon.vibeandride.util.VibeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "AudiusApi"

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

    private suspend fun refreshNodes() = withContext(Dispatchers.IO) {
        try {
            val url = "https://$DEFAULT_AUDIUS_HOST/v1/discovery-providers"
            val request = Request.Builder().url(url).build()
            
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

suspend fun getAudiusStreamUrl(
    audiusId: String,
): String? = withContext(Dispatchers.IO) {
    val apiKey = "0xfe22f3410fff4707efd27f66e6277ce84105868c"
    
    val candidateNodes = AudiusNodeRegistry.getNodes()
    
    // Try up to 3 different nodes
    for (attempt in 0 until minOf(3, candidateNodes.size)) {
        val host = candidateNodes[attempt].replace("https://", "").replace("http://", "")
        try {
            val url = "https://$host/v1/tracks/$audiusId?app_name=VIBE_AND_RIDE"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
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
                    
                    if (trackResponse?.data != null) {
                        // Return the stream URL pointing to the SAME host that confirmed the metadata
                        return@use "https://$host/v1/tracks/$audiusId/stream?app_name=VIBE_AND_RIDE"
                    }
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
