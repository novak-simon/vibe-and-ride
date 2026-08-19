package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.round

@Serializable
data class Interval(
    val duration: Double = 60.0,
    var power: Float? = null,
    @SerialName("power_low") val powerLow: Float? = null,
    @SerialName("power_high") val powerHigh: Float? = null,
    var cadence: Double? = null,
    @SerialName("cadence_low") val cadenceLow: Double? = null,
    @SerialName("cadence_high") val cadenceHigh: Double? = null,
    val type: String? = null,
) {
    init {
        if (power == null || power == 0f) {
            power = when {
                powerLow != null && powerHigh != null && powerLow != 0f && powerHigh != 0f ->
                    (powerLow + powerHigh) / 2f
                powerLow != null && powerLow != 0f -> powerLow
                powerHigh != null && powerHigh != 0f -> powerHigh
                else -> 0.6f
            }
        }

        if (cadence == null || cadence == 0.0) {
            cadence = when {
                cadenceLow != null && cadenceHigh != null && cadenceLow != 0.0 && cadenceHigh != 0.0 ->
                    round((cadenceLow + cadenceHigh) / 2)
                cadenceLow != null && cadenceLow != 0.0 -> cadenceLow
                cadenceHigh != null && cadenceHigh != 0.0 -> cadenceHigh
                else -> estimateCadenceFromPower(power).toDouble()
            }
        }
    }
}