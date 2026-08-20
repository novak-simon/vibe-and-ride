package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawUserSongStats
import com.novaksimon.vibeandride.model.UserSongStats
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

suspend fun getStatById(id: Int): UserSongStats? {
    val stat = client.postgrest["user_song_stats"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawUserSongStats>()
    return if (stat != null) rawToStat(stat) else null
}

suspend fun getStatByUserAndSongId(userId: Int, songId: Int): UserSongStats? {
    return client.postgrest["user_song_stats"]
        .select {
            filter {
                eq("song_id", songId)
                eq("user_id", userId)
            }
            limit(1)
        }
        .decodeSingleOrNull<RawUserSongStats>()
        ?.let(::rawToStat)
}

suspend fun getStatsByUserId(userId: Int, limit: Int? = 50): List<UserSongStats> {
    return client.postgrest["user_song_stats"]
        .select {
            filter { eq("user_id", userId) }
            if (limit != null) limit(limit.toLong())
        }
        .decodeList<RawUserSongStats>()
        .map(::rawToStat)
}

/**
 * Increments the given stat column (e.g. "play_count", "skip_count") for the
 * user/song pair. Creates the row if it doesn't exist yet.
 */
suspend fun incrementStat(
    stat: String,
    songId: Int,
    userId: Int,
    incrementBy: Int = 1,
): UserSongStats? {
    val existing = getStatByUserAndSongId(userId, songId)

    if (existing == null) {
        val insertBody = buildJsonObject {
            put("user_id", userId)
            put("song_id", songId)
            put(stat, incrementBy)
        }
        return client.postgrest["user_song_stats"]
            .insert(insertBody) { select() }
            .decodeSingleOrNull<RawUserSongStats>()
            ?.let(::rawToStat)
    }

    val currentValue = when (stat) {
        "play_count" -> existing.playCount
        "skip_count" -> existing.skipCount
        else -> 0
    }
    val newValue = currentValue + incrementBy

    val updateBody = buildJsonObject { put(stat, newValue) }
    return client.postgrest["user_song_stats"]
        .update(updateBody) {
            filter {
                eq("user_id", userId)
                eq("song_id", songId)
            }
            select()
        }
        .decodeSingleOrNull<RawUserSongStats>()
        ?.let(::rawToStat)
}

/**
 * Sets a datetime-valued stat column (e.g. "last_played_at", "last_skipped_at")
 * to the given instant, or now if not provided.
 */
suspend fun setStatDatetime(
    stat: String,
    songId: Int,
    userId: Int,
    value: Instant = kotlin.time.Clock.System.now(),
): UserSongStats? {
    val updateBody = buildJsonObject { put(stat, value.toString()) }
    return client.postgrest["user_song_stats"]
        .update(updateBody) {
            filter {
                eq("song_id", songId)
                eq("user_id", userId)
            }
            select()
        }
        .decodeSingleOrNull<RawUserSongStats>()
        ?.let(::rawToStat)
}
suspend fun changeLikeState(songId: Int, userId: Int, targetState: Boolean) {
    val updateBody = buildJsonObject { put("is_liked", targetState) }
    client.postgrest["user_song_stats"]
        .update(updateBody) {
            filter {
                eq("song_id", songId)
                eq("user_id", userId)
            }
            select()
        }
}