package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawProfileSongStats
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.model.songToDisplaySong
import com.novaksimon.vibeandride.util.VibeLog
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

private const val TAG = "ProfileSongStatsRepo"

suspend fun getStatByProfileAndSongId(songId: Int, profileId: String? = null): ProfileSongStats? {
    return try {
        val resolvedProfileId = profileId ?: getCurrentProfile()?.id ?: return null
        client.postgrest["profile_song_stats"]
            .select {
                filter {
                    eq("song_id", songId)
                    eq("profile_id", resolvedProfileId)
                }
                limit(1)
            }
            .decodeSingleOrNull<RawProfileSongStats>()
            ?.let(::rawToStat)
    } catch (e: Exception) {
        VibeLog.e(TAG, "getStatByProfileAndSongId failed for song $songId", e)
        null
    }
}

/**
 * Increments the given stat column (e.g. "play_count", "skip_count") for the
 * profile/song pair. Creates the row if it doesn't exist yet.
 */
suspend fun incrementStat(
    stat: String,
    songId: Int,
    profileId: String? = null,
    incrementBy: Double = 1.0,
): ProfileSongStats? {
    return try {
        val resolvedProfileId = profileId ?: getCurrentProfile()?.id ?: return null
        val existing = getStatByProfileAndSongId(songId, resolvedProfileId)
        val finalIncrementBy = if (stat == "play_count" || stat == "total_play_time") {
            incrementBy.toInt()
        } else {
            incrementBy
        }

        if (existing == null) {
            val insertBody = buildJsonObject {
                put("profile_id", resolvedProfileId)
                put("song_id", songId)
                put(stat, finalIncrementBy)
            }
            client.postgrest["profile_song_stats"]
                .insert(insertBody) { select() }
                .decodeSingleOrNull<RawProfileSongStats>()
                ?.let(::rawToStat)
        } else {
            val currentValue = when (stat) {
                "play_count" -> existing.playCount
                "skip_count" -> existing.skipCount
                "total_play_time" -> existing.totalPlayTime
                else -> 0
            }
            val newValue = currentValue + incrementBy

            val updateBody = buildJsonObject { put(stat, newValue) }
            client.postgrest["profile_song_stats"]
                .update(updateBody) {
                    filter {
                        eq("profile_id", resolvedProfileId)
                        eq("song_id", songId)
                    }
                    select()
                }
                .decodeSingleOrNull<RawProfileSongStats>()
                ?.let(::rawToStat)
        }
    } catch (e: Exception) {
        VibeLog.e(TAG, "incrementStat failed for song $songId, stat $stat", e)
        null
    }
}

/**
 * Sets a datetime-valued stat column (e.g. "last_played_at", "last_skipped_at")
 * to the given instant, or now if not provided.
 */
suspend fun setStatDatetime(
    stat: String,
    songId: Int,
    profileId: String? = null,
    value: Instant = kotlin.time.Clock.System.now(),
): ProfileSongStats? {
    return try {
        val resolvedProfileId = profileId ?: getCurrentProfile()?.id ?: return null
        val updateBody = buildJsonObject { put(stat, value.toString()) }
        client.postgrest["profile_song_stats"]
            .update(updateBody) {
                filter {
                    eq("profile_id", resolvedProfileId)
                    eq("song_id", songId)
                }
                select()
            }
            .decodeSingleOrNull<RawProfileSongStats>()
            ?.let(::rawToStat)
    } catch (e: Exception) {
        VibeLog.e(TAG, "setStatDatetime failed for song $songId, stat $stat", e)
        null
    }
}
suspend fun changeLikeState(songId: Int, targetState: Boolean, profileId: String? = null) {
    val resolvedProfileId = profileId ?: getCurrentProfile()?.id ?: return
    val existing = getStatByProfileAndSongId(songId, resolvedProfileId)

    try {
        if (existing == null) {
            val insertBody = buildJsonObject {
                put("profile_id", resolvedProfileId)
                put("song_id", songId)
                put("is_liked", targetState)
            }
            client.postgrest["profile_song_stats"].insert(insertBody)
        } else {
            val updateBody = buildJsonObject { 
                put("is_liked", targetState)
            }
            client.postgrest["profile_song_stats"].update(updateBody) {
                filter {
                    eq("song_id", songId)
                    eq("profile_id", resolvedProfileId)
                }
            }
        }
    } catch (e: Exception) {
        VibeLog.e(TAG, "Error changing like state for song $songId", e)
    }
}

suspend fun getLikedSongsWithDetails(profileId: String? = null): List<com.novaksimon.vibeandride.model.DisplaySongV2> {
    return try {
        val resolvedProfileId = profileId ?: getCurrentProfile()?.id ?: return emptyList()
        val likedStats = client.postgrest["profile_song_stats"]
            .select {
                filter {
                    eq("profile_id", resolvedProfileId)
                    eq("is_liked", true)
                }
            }
            .decodeList<RawProfileSongStats>()

        val songIds = likedStats.mapNotNull { it.songId }.distinct()
        getSongsByIds(songIds).map { song ->
            songToDisplaySong(song)
        }
    } catch (e: Exception) {
        VibeLog.e(TAG, "getLikedSongsWithDetails failed", e)
        emptyList()
    }
}

