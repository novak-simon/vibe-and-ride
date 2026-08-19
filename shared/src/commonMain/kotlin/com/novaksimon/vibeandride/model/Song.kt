package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val title: String,
    @SerialName("jamendo_id") val jamendoId: Long? = null,
    val id: Int? = null,
    val artist: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val genre: List<String>? = null,
    val instruments: List<String>? = null,
    val vartags: List<String>? = null,
    val duration: Int? = null,
    val speed: Int = 0,
    val bpm: Int = 0,
    @SerialName("bpm_confidence") val bpmConfidence: Int = 0,
    @SerialName("beat_intervals") val beatIntervals: List<Float>? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("start_time") val startTime: Float = 0f,
    @SerialName("end_time") val endTime: Float? = 0f,
    val waveform: List<Int>? = null,
    val image: String? = null,
    @SerialName("shareurl") val shareUrl: String? = null,
    @SerialName("license_ccurl") val licenseCcUrl: String? = null,
    @SerialName("content_id_free") val contentIdFree: Boolean? = null,
    val lang: String? = null,
    val gender: String? = null,
    @SerialName("is_instrumental") val isInstrumental: Boolean? = null,
    @SerialName("is_electric") val isElectric: Boolean? = null,
    @SerialName("license_nd") val licenseNd: Boolean? = null,
    @SerialName("license_sa") val licenseSa: Boolean? = null,
    @SerialName("license_nc") val licenseNc: Boolean? = null,
    @SerialName("license_version") val licenseVersion: Float? = null,
)