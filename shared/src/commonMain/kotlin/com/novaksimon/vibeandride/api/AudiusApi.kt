package com.novaksimon.vibeandride.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.milliseconds

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
    val is_streamable: Boolean = true
)

private const val AUDIUS_HOST = "api.audius.co"

suspend fun getAudiusStreamUrl(
    audiusId: String,
): String? = withContext(Dispatchers.IO) {
    val apiKey = "0xfe22f3410fff4707efd27f66e6277ce84105868c"
    
    // We try api.audius.co with retries as it is the primary entry point
    for (attempt in 1..3) {
        try {
            val url = "https://$AUDIUS_HOST/v1/tracks/$audiusId?app_name=VIBE_AND_RIDE"
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
                        null
                    }
                    
                    if (trackResponse?.data != null) {
                        return@use "https://$AUDIUS_HOST/v1/tracks/$audiusId/stream?app_name=VIBE_AND_RIDE"
                    }
                }
                null
            }
            
            if (streamUrl != null) return@withContext streamUrl
            
        } catch (e: Exception) {
            println("Audius request failed on $AUDIUS_HOST (attempt $attempt): ${e.message}")
        }
        
        if (attempt < 3) {
            delay((500L * attempt).milliseconds)
        }
    }
    
    return@withContext null
}
