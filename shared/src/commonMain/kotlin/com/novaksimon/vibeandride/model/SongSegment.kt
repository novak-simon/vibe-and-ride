package com.novaksimon.vibeandride.model

import kotlinx.serialization.Serializable

@Serializable
data class SongSegment(
    val id: Long,
    val songId: Int,
    val segmentIndex: Int,
    val startTime: Float,
    val endTime: Float,
    val label: String?,
    val relativeEnergy: Float?,
    val loudnessLufs: Float?,
)
