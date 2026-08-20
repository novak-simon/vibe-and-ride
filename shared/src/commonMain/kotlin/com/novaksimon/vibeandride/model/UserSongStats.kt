package com.novaksimon.vibeandride.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSongStats(
    @SerialName("song_id") val songId: Int,
    @SerialName("user_id") val userId: Int,
    @SerialName("is_liked") val isLiked: Boolean,
    @SerialName("play_count") val playCount: Int,
    @SerialName("skip_count") val skipCount: Int,
    @SerialName("first_played_at") val firstPlayedAt: kotlin.time.Instant?,
    @SerialName("last_played_at") val lastPlayedAt: kotlin.time.Instant?,
    @SerialName("last_skipped_at") val lastSkippedAt: kotlin.time.Instant?,
    @SerialName("total_play_time") val totalPlayTime: Int,
)