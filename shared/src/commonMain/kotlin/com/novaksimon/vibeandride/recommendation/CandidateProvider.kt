package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.database.getSongsAroundTargetCadence
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.util.VibeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CandidateProvider(
    private val songRepository: SongRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    ) {

    suspend fun getCandidates(interval: Interval, limit: Int = 50): List<SongV2> {
        return approximateFilterByInterval(interval, limit)
    }

    private suspend fun approximateFilterByInterval(
        interval: Interval,
        limit: Int,
    ): List<SongV2> {
        val cadence = interval.cadence.toInt()
        val targetCadence = getAlgorithmCadence(cadence)
        var pool = withContext(ioDispatcher) {
            songRepository.getSongsAroundTargetCadence(
                targetCadence,
                algorithmCadenceToleration = 2,
                limit = limit
            ).getOrDefault(emptyList())
        }
        if (pool.size < limit) {
            pool = pool.union(
                withContext(ioDispatcher) {
                    songRepository.getSongsAroundTargetCadence(
                        targetCadence,
                        algorithmCadenceToleration = 5,
                        limit = limit - pool.size
                    ).getOrDefault(emptyList())
                }
            ).toList()
            if (pool.size < limit) {
                pool = pool.union(
                    withContext(ioDispatcher) {
                        songRepository.getSongsAroundTargetCadence(
                            targetCadence,
                            algorithmCadenceToleration = 10,
                            limit = limit - pool.size
                        ).getOrDefault(emptyList())
                    }
                ).toList()
                if (pool.isEmpty()) {
                    VibeLog.w("RecommendationEngine", "No songs found around cadence, fetching generic songs...")
                    withContext(ioDispatcher) {
                        pool = songRepository.fetchSongs(50).getOrDefault(emptyList())
                    }
                }
            }
        }
        VibeLog.d("RecommendationEngine", "approximateFilterByInterval: found ${pool.size} candidates")
        return pool
    }
}