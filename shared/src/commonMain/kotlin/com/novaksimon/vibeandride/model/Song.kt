package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    @SerialName("release_date") val releaseDate: String,
    val genre: List<String>,
    val instruments: List<String>,
    val vartags: List<String>,
    val duration: Int,
    val speed: Int,
    val bpm: Int,
    @SerialName("bpm_confidence") val bpmConfidence: Int,
    @SerialName("audio_url") val audioUrl: String,
    @SerialName("start_time") val startTime: Float,
    @SerialName("end_time") val endTime: Float,
    val image: String,
    @SerialName("shareurl") val shareUrl: String,
    val lang: String,
    val gender: String,
    @SerialName("is_instrumental") val isInstrumental: Boolean,
    @SerialName("is_electric") val isElectric: Boolean,
)