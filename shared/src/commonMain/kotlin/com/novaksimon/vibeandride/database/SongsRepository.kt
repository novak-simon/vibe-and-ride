package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawSongSegment
import com.novaksimon.vibeandride.model.RawSongV2
import com.novaksimon.vibeandride.model.SongSegment
import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.util.VibeLog
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "SongsRepository"

suspend fun getSongV2ById(id: Int): SongV2? {
    val rawSong = client.postgrest["songs_v2"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawSongV2>()
    return if (rawSong != null) rawToSongV2(rawSong) else null
}

suspend fun fetchSongs(limit: Int = 10): List<SongV2> {
    val response = client.postgrest["songs"]
        .select { 
            limit(limit.toLong()) 
        }
    VibeLog.d(TAG, "fetchSongs: raw response body: ${response.data}")
    val rawSongs = response.decodeList<RawSongV2>()
    VibeLog.d(TAG, "fetchSongs: successfully decoded ${rawSongs.size} songs")
    return rawSongs.map { rawToSongV2(it) }
}

suspend fun searchSongsByEmbedding(
    targetEmbedding: List<Float>,
    targetBpm: Int,
    minLimit: Int,
    maxLimit: Int
): List<SongV2> {
    if (targetEmbedding.isEmpty()) {
        VibeLog.w(TAG, "searchSongsByEmbedding: targetEmbedding is empty, returning empty list")
        return emptyList()
    }

    val response = client.postgrest.rpc(
        function = "search_songs_by_embedding",
        parameters = buildJsonObject {
            put("target_embedding", buildJsonArray {
                targetEmbedding.forEach { add(it) }
            })
            put("target_bpm", targetBpm)
            put("min_limit", minLimit)
            put("max_limit", maxLimit)
        }
    )
    val rawSongs = response.decodeList<RawSongV2>()
    return rawSongs.map { rawToSongV2(it) }
}

suspend fun getSongsAroundTargetCadence(
    targetAlgorithmCadence: Int,
    algorithmCadenceToleration: Int = 5,
    limit: Int = 20,
): List<SongV2> {
    val gc = targetAlgorithmCadence - algorithmCadenceToleration
    val lc = targetAlgorithmCadence + algorithmCadenceToleration
    val ranges = listOf(gc to lc, gc/2 to lc/2, gc*2 to lc*2)
    VibeLog.d(TAG, "getSongsAroundTargetCadence: target=$targetAlgorithmCadence ranges=$ranges")
    
    val rawSongs = client.postgrest["songs_v2"]
        .select {
            filter {
                or {
                    ranges.forEach { (lo, hi) ->
                        and {
                            gte("bpm", lo)
                            lte("bpm", hi)
                        }
                    }
                }
            }
            limit(limit.toLong())
        }
        .decodeList<RawSongV2>()
    VibeLog.d(TAG, "getSongsAroundTargetCadence: found ${rawSongs.size} matching songs")
    return rawSongs.map { rawToSongV2(it) }
}

suspend fun getSongsByIds(ids: List<Int>): List<SongV2> {
    if (ids.isEmpty()) return emptyList()
    val rawSongs = client.postgrest["songs_v2"]
        .select {
            filter {
                isIn("id", ids)
            }
        }
        .decodeList<RawSongV2>()
    return rawSongs.map { rawToSongV2(it) }
}
