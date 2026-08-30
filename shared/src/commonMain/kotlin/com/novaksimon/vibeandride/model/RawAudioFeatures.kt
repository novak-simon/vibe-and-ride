package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawAudioFeatures(
    @SerialName("song_id")
    val songId: Int?,

    @SerialName("loudness_lufs")
    val loudnessLufs: Double?,

    @SerialName("rms_db")
    val rmsDb: Double?,

    @SerialName("rms_db_std")
    val rmsDbStd: Double?,

    @SerialName("spectral_centroid_hz")
    val spectralCentroidHz: Double?,

    @SerialName("spectral_flux")
    val spectralFlux: Double?,

    val bpm: Double?,

    @SerialName("onset_density")
    val onsetDensity: Double?,

    @SerialName("sub_bass_ratio")
    val subBassRatio: Double?,

    @SerialName("bass_ratio")
    val bassRatio: Double?,

    @SerialName("low_mid_ratio")
    val lowMidRatio: Double?,

    @SerialName("mid_ratio")
    val midRatio: Double?,

    @SerialName("high_mid_ratio")
    val highMidRatio: Double?,

    @SerialName("high_ratio")
    val highRatio: Double?
)