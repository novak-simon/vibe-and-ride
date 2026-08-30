package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SongV2(
    val id: Int,
    val title: String,
    @SerialName("artist_id") val artistId: Int,
    @SerialName("album_id") val albumId: Int,
    @SerialName("release_date") val releaseDate: String,
    val description: String,
    val genre: String,
    val mood: String,
    val tags: List<String> = emptyList(),
    val duration: Int,
    val bpm: Int,
    @SerialName("start_time") val startTime: Float = 0f,
    @SerialName("end_time") val endTime: Float = 0f,
    val image: String,
    @SerialName("share_url") val shareUrl: String,
    val provider: Int = 0,
    @SerialName("audius_provider_id") val audiusProviderId: String? = null,
    @SerialName("jamendo_provider_id") val jamendoProviderId: Int? = null
    )