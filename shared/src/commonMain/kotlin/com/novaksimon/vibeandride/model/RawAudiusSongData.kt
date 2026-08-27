package com.novaksimon.vibeandride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawAudiusSongData(
    @SerialName("song_id") val songId: Int? = null,
    @SerialName("audius_id") val audiusId: String? = null,
    @SerialName("track_cid") val trackCid: String? = null,
    @SerialName("audius_user_id") val audiusUserId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("album") val album: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("duration") val duration: String? = null,
    @SerialName("play_count") val playCount: Long? = null,
    @SerialName("repost_count") val repostCount: Long? = null,
    @SerialName("audius_tags") val audiusTags: List<String>? = null,
    @SerialName("is_public") val isPublic: Boolean? = null,
    @SerialName("cover_art_url") val coverArtUrl: String? = null
)