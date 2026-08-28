package com.novaksimon.vibeandride.server

import kotlinx.serialization.Serializable

@Serializable
data class IdRow(val id: Int)

@Serializable
data class StatusResponse(
    val status: String,
    val version: String? = null,
    val limit: Int? = null,
    val offset: Int? = null
)
