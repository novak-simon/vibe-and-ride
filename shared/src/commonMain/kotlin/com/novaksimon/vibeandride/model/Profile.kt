package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("genre_preferences") val genrePreferences: List<String>,
    @SerialName("music_taste_vector") val musicTasteVector: List<Double>,
)
