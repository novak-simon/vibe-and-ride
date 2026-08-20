package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutToUpload(
    val name: String?,
    val author: String?,
    @SerialName("sport_type_is_bike") val sportType: Boolean = true,
    val tags: List<String>? = emptyList(),
    val description: String?,
    val intervals: List<Interval>? = emptyList(),
)
