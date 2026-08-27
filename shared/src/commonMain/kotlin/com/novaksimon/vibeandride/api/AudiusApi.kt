package com.novaksimon.vibeandride.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private val httpClient = OkHttpClient()

private val json = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class AudiusTrackResponse(
    val data: AudiusTrack
)

@Serializable
data class AudiusTrack(
    val stream: AudiusStream? = null
)

@Serializable
data class AudiusStream(
    val url: String? = null
)

suspend fun getAudiusStreamUrl(
    audiusId: String,
): String? = withContext(Dispatchers.IO) {
    val apiKey = "0xfe22f3410fff4707efd27f66e6277ce84105868c"
    val request = Request.Builder()
        .url("https://api.audius.co/v1/tracks/$audiusId")
        .addHeader("x-api-key", apiKey)
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("Audius request failed: ${response.code} ${response.message}")
        }

        val body = response.body.string()

        val trackResponse = json.decodeFromString<AudiusTrackResponse>(body)

        return@use trackResponse.data.stream?.url
    }
}