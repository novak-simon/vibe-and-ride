package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Workout(
    val id: Int,
    val name: String,
    val author: String,
    @SerialName("sport_type_is_bike") val sportType: Boolean = true,
    val tags: List<String> = emptyList(),
    val description: String = "No description",
    @SerialName("chosen_count") val chosenCount: Int = 0,
    val intervals: List<Interval> = emptyList(),
) {
    val totalDuration: Int
        get() = intervals.sumOf { it.duration }.toInt()


}
