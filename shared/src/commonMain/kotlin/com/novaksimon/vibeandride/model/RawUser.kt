package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawUser(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("genre_preferences") val genrePreferences: List<String>? = null,
)