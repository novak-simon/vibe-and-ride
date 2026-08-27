package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawSong
import com.novaksimon.vibeandride.model.Song
import io.github.jan.supabase.postgrest.postgrest

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
    return try {
        val response = client.postgrest["songs"]
            .select { 
                limit(limit.toLong()) 
            }
        println("fetchSongs: raw response body: ${response.data}")
        val rawSongs = response.decodeList<RawSong>()
        println("fetchSongs: successfully decoded ${rawSongs.size} songs")
        rawSongs.map { rawToSong(it) }
    } catch (e: Exception) {
        println("fetchSongs error: ${e.message}")
        e.printStackTrace()
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
    println("getSongsAroundTargetCadence: target=$targetAlgorithmCadence ranges=$ranges")
    
    return try {
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
        println("getSongsAroundTargetCadence: found ${rawSongs.size} matching songs")
        rawSongs.map { rawToSong(it) }
    } catch (e: Exception) {
        println("getSongsAroundTargetCadence error: ${e.message}")
        e.printStackTrace()
        // Fallback: just fetch some songs if the complex filter fails
        fetchSongs(limit)
    }
}
