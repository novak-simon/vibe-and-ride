package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int = 0,
    val name: String = "User",
    @SerialName("genre_preferences") val genrePreferences: List<String> = emptyList(),
)