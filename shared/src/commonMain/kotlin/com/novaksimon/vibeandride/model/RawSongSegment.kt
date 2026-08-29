package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawSongSegment(
    val id: Long,
    @SerialName("song_id") val songId: Int,
    @SerialName("segment_index") val segmentIndex: Int,
    @SerialName("start_time") val startTime: Float,
    @SerialName("end_time") val endTime: Float,
    val label: String? = null,
    @SerialName("relative_energy") val relativeEnergy: Float? = null,
    @SerialName("loudness_lufs") val loudnessLufs: Float? = null,
)
