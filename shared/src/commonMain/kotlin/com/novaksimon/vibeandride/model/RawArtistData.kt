package com.novaksimon.vibeandride.model

import kotlinx.serialization.Serializable

@Serializable
data class RawArtistData(
    val id: Int? = null,
    val name: String? = null,
)