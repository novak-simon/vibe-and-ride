package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProfileSongStats(
    val id: Int,
    @SerialName("song_id") val songId: Int? = null,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("is_liked") val isLiked: Boolean? = null,
    @SerialName("play_count") val playCount: Int? = null,
    @SerialName("skip_count") val skipCount: Int? = null,
    @SerialName("first_played_at") val firstPlayedAt: kotlin.time.Instant? = null,
    @SerialName("last_played_at") val lastPlayedAt: kotlin.time.Instant? = null,
    @SerialName("last_skipped_at") val lastSkippedAt: kotlin.time.Instant? = null,
    @SerialName("total_play_time") val totalPlayTime: Int? = null,
)
