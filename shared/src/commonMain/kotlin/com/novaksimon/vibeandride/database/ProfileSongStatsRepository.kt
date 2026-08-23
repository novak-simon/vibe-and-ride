package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawProfileSongStats
import com.novaksimon.vibeandride.model.ProfileSongStats
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

suspend fun getStatById(id: Int): ProfileSongStats? {
    val stat = client.postgrest["profile_song_stats"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawProfileSongStats>()
    return if (stat != null) rawToStat(stat) else null
}

suspend fun getStatByProfileAndSongId(profileId: String, songId: Int): ProfileSongStats? {
    if (profileId.isEmpty()) return null
    return client.postgrest["profile_song_stats"]
        .select {
            filter {
                eq("song_id", songId)
                eq("profile_id", profileId)
            }
            limit(1)
        }
        .decodeSingleOrNull<RawProfileSongStats>()
        ?.let(::rawToStat)
}

suspend fun getStatsByProfileId(profileId: String, limit: Int? = 50): List<ProfileSongStats> {
    if (profileId.isEmpty()) return emptyList()
    return client.postgrest["profile_song_stats"]
        .select {
            filter { eq("profile_id", profileId) }
            if (limit != null) limit(limit.toLong())
        }
        .decodeList<RawProfileSongStats>()
        .map(::rawToStat)
}

/**
 * Increments the given stat column (e.g. "play_count", "skip_count") for the
 * profile/song pair. Creates the row if it doesn't exist yet.
 */
suspend fun incrementStat(
    stat: String,
    songId: Int,
    profileId: String,
    incrementBy: Int = 1,
): ProfileSongStats? {
    if (profileId.isEmpty()) return null
    val existing = getStatByProfileAndSongId(profileId, songId)

    if (existing == null) {
        val insertBody = buildJsonObject {
            put("profile_id", profileId)
            put("song_id", songId)
            put(stat, incrementBy)
        }
        return client.postgrest["profile_song_stats"]
            .insert(insertBody) { select() }
            .decodeSingleOrNull<RawProfileSongStats>()
            ?.let(::rawToStat)
    }

    val currentValue = when (stat) {
        "play_count" -> existing.playCount
        "skip_count" -> existing.skipCount
        else -> 0
    }
    val newValue = currentValue + incrementBy

    val updateBody = buildJsonObject { put(stat, newValue) }
    return client.postgrest["profile_song_stats"]
        .update(updateBody) {
            filter {
                eq("profile_id", profileId)
                eq("song_id", songId)
            }
            select()
        }
        .decodeSingleOrNull<RawProfileSongStats>()
        ?.let(::rawToStat)
}

/**
 * Sets a datetime-valued stat column (e.g. "last_played_at", "last_skipped_at")
 * to the given instant, or now if not provided.
 */
suspend fun setStatDatetime(
    stat: String,
    songId: Int,
    profileId: String,
    value: Instant = kotlin.time.Clock.System.now(),
): ProfileSongStats? {
    if (profileId.isEmpty()) return null
    val updateBody = buildJsonObject { put(stat, value.toString()) }
    return client.postgrest["profile_song_stats"]
        .update(updateBody) {
            filter {
                eq("song_id", songId)
                eq("profile_id", profileId)
            }
            select()
        }
        .decodeSingleOrNull<RawProfileSongStats>()
        ?.let(::rawToStat)
}
suspend fun changeLikeState(songId: Int, profileId: String, targetState: Boolean) {
    if (profileId.isEmpty()) return
    val updateBody = buildJsonObject { put("is_liked", targetState) }
    client.postgrest["profile_song_stats"]
        .update(updateBody) {
            filter {
                eq("song_id", songId)
                eq("profile_id", profileId)
            }
            select()
        }
}
