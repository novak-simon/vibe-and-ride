package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawInterval(
    val duration: Double? = null,
    var power: Float? = null,
    @SerialName("power_low") val powerLow: Float? = null,
    @SerialName("power_high") val powerHigh: Float? = null,
    var cadence: Double? = null,
    @SerialName("cadence_low") val cadenceLow: Double? = null,
    @SerialName("cadence_high") val cadenceHigh: Double? = null,
    val type: String? = null,
)