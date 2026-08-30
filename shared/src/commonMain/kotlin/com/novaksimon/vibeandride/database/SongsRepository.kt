package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawSongV2
import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.util.VibeLog
import io.github.jan.supabase.postgrest.postgrest
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
