package com.novaksimon.vibeandride.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSongStats(
    val id: Int = 0,
    @SerialName("song_id") val songId: Int = 0,
    @SerialName("user_id") val userId: Int = 0,
    @SerialName("is_liked") val isLiked: Boolean = false,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("skip_count") val skipCount: Int = 0,
    @SerialName("first_played_at") val firstPlayedAt: Instant? = null,
    @SerialName("last_played_at") val lastPlayedAt: Instant? = null,
    @SerialName("last_skipped_at") val lastSkippedAt: Instant? = null,
    @SerialName("total_play_time") val totalPlayTime: Int = 0,
)