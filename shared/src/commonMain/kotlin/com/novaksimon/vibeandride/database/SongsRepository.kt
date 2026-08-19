package com.novaksimon.vibeandride.database

import androidx.annotation.Nullable
import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.Song
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator

suspend fun getSongByJamendoId(jamendoId: String): Song? {
    return client.postgrest["songs"]
        .select {
            filter { eq("jamendo_id", jamendoId) }
            limit(1)
        }
        .decodeSingleOrNull<Song>()
}

suspend fun getSongById(id: Int): Song? {
    return client.postgrest["songs"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<Song>()
}

suspend fun jamendoIdExists(jamendoId: String): Boolean {
    return client.postgrest["songs"]
        .select(Columns.list("id")) {
            filter { eq("jamendo_id", jamendoId) }
            limit(1)
        }
        .decodeList<Map<String, Int>>()
        .isNotEmpty()
}

suspend fun fetchSongs(limit: Int = 10): List<Song> {
    return client.postgrest["songs"]
        .select { limit(limit.toLong()) }
        .decodeList<Song>()
}

suspend fun getPlayableSongs(): List<Song> {
    return client.postgrest["songs"]
        .select()
        .decodeList<Song>()
        .filter { it.audioUrl != null }
}
suspend fun getSongsAroundTargetCadence(
    targetAlgorithmCadence: Int,
    algorithmCadenceToleration: Int = 5,
    limit: Int = 20,
): List<Song> {
    val gc = targetAlgorithmCadence - algorithmCadenceToleration
    val lc = targetAlgorithmCadence + algorithmCadenceToleration
    val ranges = listOf(gc to lc, gc/2 to lc/2, gc*2 to lc*2)
    return client.postgrest["songs"]
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
        .decodeList<Song>()
}