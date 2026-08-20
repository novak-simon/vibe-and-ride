package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawSong
import com.novaksimon.vibeandride.model.Song
import io.github.jan.supabase.postgrest.postgrest

suspend fun getSongByJamendoId(jamendoId: String): Song? {
    val rawSong = client.postgrest["songs"]
        .select {
            filter { eq("jamendo_id", jamendoId) }
            limit(1)
        }
        .decodeSingleOrNull<RawSong>()
    return if (rawSong != null) rawToSong(rawSong) else null
}

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
    val songs = client.postgrest["songs"]
        .select { limit(limit.toLong()) }
        .decodeList<RawSong>()
        .map { rawToSong(it) }
    return songs
}

suspend fun getPlayableSongs(): List<Song> {
    val songs = client.postgrest["songs"]
        .select()
        .decodeList<RawSong>()
        .filter { it.audioUrl != null }
        .map { rawToSong(it) }
    return songs
}
suspend fun getSongsAroundTargetCadence(
    targetAlgorithmCadence: Int,
    algorithmCadenceToleration: Int = 5,
    limit: Int = 20,
): List<Song> {
    val gc = targetAlgorithmCadence - algorithmCadenceToleration
    val lc = targetAlgorithmCadence + algorithmCadenceToleration
    val ranges = listOf(gc to lc, gc/2 to lc/2, gc*2 to lc*2)
    val songs = client.postgrest["songs"]
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
        .map { rawToSong(it) }
    return songs
}