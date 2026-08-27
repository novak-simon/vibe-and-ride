package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawWorkout(
    val id: Int,
    val name: String? = null,
    val author: String? = null,
    @SerialName("sport_type_is_bike") val sportType: Boolean? = null,
    val tags: List<String>? = null,
    val description: String? = null,
    @SerialName("chosen_count") val chosenCount: Int? = null,
    val intervals: List<RawInterval>? = null,
)
