package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawSong(
    val id: Int,
    val title: String? = null,
    @SerialName("artist_id") val artistId: Int? = null,
    @SerialName("album_id") val albumId: Int? = null,
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
)