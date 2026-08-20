package com.novaksimon.vibeandride.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawUserSongStats(
    val id: Int,
    @SerialName("song_id") val songId: Int? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("is_liked") val isLiked: Boolean? = null,
    @SerialName("play_count") val playCount: Int? = null,
    @SerialName("skip_count") val skipCount: Int? = null,
    @SerialName("first_played_at") val firstPlayedAt: Instant? = null,
    @SerialName("last_played_at") val lastPlayedAt: Instant? = null,
    @SerialName("last_skipped_at") val lastSkippedAt: Instant? = null,
    @SerialName("total_play_time") val totalPlayTime: Int? = null,
)