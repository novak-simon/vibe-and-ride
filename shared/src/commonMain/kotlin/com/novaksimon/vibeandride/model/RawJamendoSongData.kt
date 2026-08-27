package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawJamendoSongData(
    @SerialName("song_id") val songId: Int? = null,
    @SerialName("jamendo_id") val jamendoId: Long? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val waveform: List<Int>? = null,


    )