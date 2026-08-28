package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    @SerialName("artist_id") val artistId: Int? = null,
    @SerialName("release_date") val releaseDate: String,
    val genre: List<String>,
    val tags: List<String>,
    val duration: Int,
    val bpm: Int,
    @SerialName("start_time") val startTime: Float,
    @SerialName("end_time") val endTime: Float,
    val image: String,
    @SerialName("share_url") val shareUrl: String,
)