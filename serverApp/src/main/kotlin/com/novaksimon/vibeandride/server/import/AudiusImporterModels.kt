package com.novaksimon.vibeandride.server.import


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudiusSong(
    @SerialName("audius_id") val audiusId: String,
    @SerialName("track_id") val trackId: Long,
    @SerialName("track_cid") val trackCid: String? = null,
    val permalink: String? = null,
    val title: String,
    val genre: String? = null,
    val description: String? = null,
    val mood: String? = null,
    val duration: Int,
    val bpm: Int? = null,
    @SerialName("musical_key") val musicalKey: String? = null,
    val artwork: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_handle") val userHandle: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_profile_picture") val userProfilePicture: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("play_count") val playCount: Long? = null,
    @SerialName("repost_count") val repostCount: Long? = null,
    @SerialName("favorite_count") val favoriteCount: Long? = null,
    @SerialName("comment_count") val commentCount: Long?,
    @SerialName("expired_stream_url") val expiredStreamUrl: String? = null
)
@Serializable
data class AudiusUserFromApi(
    val id: String,
    val handle: String,
    val name: String? = null,
    @SerialName("profile_picture") val profilePicture: AudiusPictureFromApi? = null
)
@Serializable
data class AudiusPictureFromApi(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null
)
@Serializable
data class StreamFromApi(
    val url: String? = null,
)
@Serializable
data class AudiusSongFromApi(
    @SerialName("id") val audiusId: String,
    @SerialName("track_id") val trackId: Long,
    @SerialName("track_cid") val trackCid: String? = null,
    val permalink: String? = null,
    val title: String,
    val genre: String? = null,
    val description: String? = null,
    val mood: String? = null,
    val duration: Int,
    val bpm: Int? = null,
    @SerialName("musical_key") val musicalKey: String? = null,
    val artwork: AudiusPictureFromApi? = null,
    val user: AudiusUserFromApi,
    @SerialName("release_date") val releaseDate: String? = null,
    val tags: String? = null,
    @SerialName("play_count") val playCount: Long? = null,
    @SerialName("repost_count") val repostCount: Long? = null,
    @SerialName("favorite_count") val favoriteCount: Long? = null,
    @SerialName("comment_count") val commentCount: Long?,
    val license: String? = null,
    @SerialName("is_delete") val isDelete: Boolean = false,
    @SerialName("is_available") val isAvailable: Boolean = false,
    @SerialName("is_streamable") val isStreamable: Boolean = false,
    val stream: StreamFromApi? = null
)
fun AudiusSongFromApi.toAudiusSong() = AudiusSong(
    audiusId = audiusId,
    trackId = trackId,
    trackCid = trackCid,
    permalink = permalink,
    title = title,
    genre = genre,
    description = description,
    mood = mood,
    duration = duration,
    bpm = bpm,
    musicalKey = musicalKey,
    artwork = artwork?.medium ?: artwork?.large ?: artwork?.small,
    userId = user.id,
    userHandle = user.handle,
    userName = user.name,
    userProfilePicture = user.profilePicture?.medium ?: user.profilePicture?.large ?: user.profilePicture?.small,
    releaseDate = releaseDate,
    tags = tags?.split(",")?.map { it.trim() } ?: emptyList(),
    playCount = playCount,
    repostCount = repostCount,
    favoriteCount = favoriteCount,
    commentCount = commentCount,
    expiredStreamUrl = stream?.url
)