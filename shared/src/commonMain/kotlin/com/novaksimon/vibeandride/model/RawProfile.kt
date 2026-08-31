package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProfile(
    val id: String? = null,
    @SerialName("genre_preferences") val genrePreferences: List<String>? = null,
    @SerialName("music_taste_vector") val musicTasteVector: List<Double>? = null,
)
