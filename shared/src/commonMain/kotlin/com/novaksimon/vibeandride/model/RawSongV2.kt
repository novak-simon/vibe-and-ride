package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawSongV2(
    val id: Long,
    val title: String? = null,
    @SerialName("artist_id") val artistId: Long? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val mood: String? = null,
    val tags: List<String>? = null,
    val duration: Int? = null,
    val bpm: Int? = null,
    @SerialName("start_time") val startTime: Float? = null,
    @SerialName("end_time") val endTime: Float? = null,
    val image: String? = null,
    @SerialName("share_url") val shareUrl: String? = null,
    val provider: Int = 0,
    @SerialName("audius_provider_id") val audiusProviderId: String? = null,
    @SerialName("jamendo_provider_id") val jamendoProviderId: Long? = null
    )