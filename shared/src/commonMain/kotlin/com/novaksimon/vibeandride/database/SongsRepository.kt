package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawSong
import com.novaksimon.vibeandride.model.RawSongSegment
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.SongSegment
import com.novaksimon.vibeandride.util.VibeLog
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

private const val TAG = "SongsRepository"

suspend fun getSongById(id: Int): Song? {
    val rawSong = client.postgrest["songs"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawSong>()
    return if (rawSong != null) rawToSong(rawSong) else null
}

suspend fun fetchSongs(limit: Int = 10): List<Song> {
    val response = client.postgrest["songs"]
        .select { 
            limit(limit.toLong()) 
        }
    VibeLog.d(TAG, "fetchSongs: raw response body: ${response.data}")
    val rawSongs = response.decodeList<RawSong>()
    VibeLog.d(TAG, "fetchSongs: successfully decoded ${rawSongs.size} songs")
    return rawSongs.map { rawToSong(it) }
}

suspend fun getSongSegments(songId: Int): List<SongSegment> {
    return try {
        val rawSegments = client.postgrest["song_segments"]
            .select {
                filter {
                    eq("song_id", songId)
                }
                order("segment_index", Order.ASCENDING)
            }
            .decodeList<RawSongSegment>()
        rawSegments.map { rawToSongSegment(it) }
    } catch (e: Exception) {
        // Log as debug/info since it's expected that many songs won't have segments yet
        VibeLog.d(TAG, "getSongSegments: No segments found or table missing for song $songId. Skipping segment logic.")
        emptyList()
    }
}

suspend fun getSongsAroundTargetCadence(
    targetAlgorithmCadence: Int,
    algorithmCadenceToleration: Int = 5,
    limit: Int = 20,
): List<Song> {
    val gc = targetAlgorithmCadence - algorithmCadenceToleration
    val lc = targetAlgorithmCadence + algorithmCadenceToleration
    val ranges = listOf(gc to lc, gc/2 to lc/2, gc*2 to lc*2)
    VibeLog.d(TAG, "getSongsAroundTargetCadence: target=$targetAlgorithmCadence ranges=$ranges")
    
    val rawSongs = client.postgrest["songs"]
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
        .decodeList<RawSong>()
    VibeLog.d(TAG, "getSongsAroundTargetCadence: found ${rawSongs.size} matching songs")
    return rawSongs.map { rawToSong(it) }
}

suspend fun getSongsByIds(ids: List<Int>): List<Song> {
    if (ids.isEmpty()) return emptyList()
    val rawSongs = client.postgrest["songs"]
        .select {
            filter {
                isIn("id", ids)
            }
        }
        .decodeList<RawSong>()
    return rawSongs.map { rawToSong(it) }
}
