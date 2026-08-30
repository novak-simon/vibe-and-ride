package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistV2(
    val id: Int,
    val name: String,
    val provider: Int? = null,
    @SerialName("audius_artist_id") val audiusArtistId: String? = null,
    @SerialName("jamendo_artist_id") val jamendoArtistId: Int? = null
)
