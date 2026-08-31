package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("genre_preferences") val genrePreferences: List<String>,
    @SerialName("music_taste_vector") val musicTasteVector: DoubleArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Profile

        if (id != other.id) return false
        if (genrePreferences != other.genrePreferences) return false
        if (!musicTasteVector.contentEquals(other.musicTasteVector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + genrePreferences.hashCode()
        result = 31 * result + musicTasteVector.contentHashCode()
        return result
    }
}
