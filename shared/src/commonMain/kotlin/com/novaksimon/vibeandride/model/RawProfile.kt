package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProfile(
    val id: String? = null,
    @SerialName("genre_preferences") val genrePreferences: List<String>? = null,
    @SerialName("music_taste_vector") val musicTasteVector: DoubleArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawProfile

        if (id != other.id) return false
        if (genrePreferences != other.genrePreferences) return false
        if (!musicTasteVector.contentEquals(other.musicTasteVector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (genrePreferences?.hashCode() ?: 0)
        result = 31 * result + (musicTasteVector?.contentHashCode() ?: 0)
        return result
    }
}
