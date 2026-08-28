package com.novaksimon.vibeandride.model

import kotlinx.serialization.Serializable

@Serializable
data class Interval(
    val duration: Double,
    var power: Float,
    var cadence: Double,
    val type: String,
    var powerLow: Float? = null,
    var powerHigh: Float? = null,
    var cadenceLow: Double? = null,
    var cadenceHigh: Double? = null,
)
