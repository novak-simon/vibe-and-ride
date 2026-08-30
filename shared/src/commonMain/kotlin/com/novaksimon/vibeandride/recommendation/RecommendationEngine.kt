package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.util.VibeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private const val FIRST_CANDIDATE_POOL_LIMIT = 100

private val TAG_WEIGHTS = mapOf(
    "energetic" to 0.5f,
    "motivational" to 0.5f,
    "upbeat" to 0.3f,
    "radio" to 0.3f,
    "groovy" to 0.2f,
    "powerful" to 0.2f,
    "beachmusic" to 0.2f,
    "handclaps" to 0.2f,
    "fast" to 0.2f,
    "epic" to 0.1f,
    "uplifting" to 0.1f,
    "upliftingrock" to 0.1f,
    "agressive" to 0.1f,
    "happy" to 0.1f,
    "cheerful" to 0.1f,
    "party" to 0.1f,
    "drumkit" to 0.1f,
    "summer" to 0.05f,
    "popular" to 0.05f,
    "lounge" to -0.1f,
    "film" to -0.1f,
    "filmscore" to -0.1f,
    "soft" to -0.1f,
    "ballad" to -0.1f,
    "melancholic" to -0.1f,
    "peaceful" to -0.2f,
    "cinematic" to -0.2f,
    "peace" to -0.2f,
    "chilloutmusic" to -0.2f,
    "chill" to -0.2f,
    "relaxing" to -0.2f,
    "mystery" to -0.2f,
    "sad" to -0.3f,
    "lonely" to -0.3f,
    "dark" to -0.3f,
    "podcast" to -0.3f,
    "calm" to -0.3f,
    "atmospheric" to -0.3f,
    "soundscape" to -0.5f,
    "introspective" to -0.5f,
    "ambient" to -1f,
    "ambientdub" to -1f,
    "drone" to -1f,
)

private val GENRE_WEIGHTS = mapOf(
    "ambient" to -1f, "ambientdub" to -1f, "drone" to -1f,
)

data class RecommendationResult(
    val topSongs: List<SongV2>,
    val debugInfo: List<SongRecommendation>,
)

class RecommendationEngine(
    private val songRepository: SongRepository,
    private val profileRepository: ProfileRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) {

    suspend fun approximateFilterByInterval(
        interval: Interval,
    ): List<SongV2> {
        val cadence = interval.cadence.toInt()
        val targetCadence = getAlgorithmCadence(cadence)
        var pool = withContext(ioDispatcher) {
            songRepository.getSongsAroundTargetCadence(
                targetCadence,
                algorithmCadenceToleration = 2,
                limit = FIRST_CANDIDATE_POOL_LIMIT
            ).getOrDefault(emptyList())
        }
        if (pool.size < FIRST_CANDIDATE_POOL_LIMIT) {
            pool = pool.union(
                withContext(ioDispatcher) {
                    songRepository.getSongsAroundTargetCadence(
                        targetCadence,
                        algorithmCadenceToleration = 5,
                        limit = FIRST_CANDIDATE_POOL_LIMIT - pool.size
                    ).getOrDefault(emptyList())
                }
            ).toList()
            if (pool.size < FIRST_CANDIDATE_POOL_LIMIT) {
                pool = pool.union(
                    withContext(ioDispatcher) {
                        songRepository.getSongsAroundTargetCadence(
                            targetCadence,
                            algorithmCadenceToleration = 10,
                            limit = FIRST_CANDIDATE_POOL_LIMIT
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

    suspend fun getProfilePreferenceScore(
        song: SongV2?,
        profile: Profile
    ): PreferenceRecommendation {
        if (song == null) {
            return PreferenceRecommendation()
        }

        val numberOfMatchingGenres: Float = profile.genrePreferences.intersect(song.genre.toSet()).size.toFloat()
        val stat: ProfileSongStats? = profileRepository.getStatByProfileAndSongId(songId = song.id, profileId = profile.id)

        if (stat != null) {
            val likeCoefficient = if (stat.isLiked) 2f else 1f
            val lastPlayedAt = stat.lastPlayedAt
            if (lastPlayedAt == null) {
                // No recorded play time despite stat existing — treat as "long ago"
                val skipCoefficient = if (stat.skipCount + stat.playCount > 0) {
                    2f * (stat.playCount.toFloat() / (stat.playCount + stat.skipCount).toFloat())
                } else {
                    2f
                }
                val score = numberOfMatchingGenres * 2f * likeCoefficient * skipCoefficient
                return PreferenceRecommendation(
                    song = song,
                    score = score,
                    profile = profile,
                    numberOfMatchingGenres = round3(numberOfMatchingGenres),
                    repetitionCoefficient = round3(2f),
                    likeCoefficient = likeCoefficient,
                    skipCoefficient = round3(skipCoefficient),
                )
            }

            val sinceLastPlayed = Clock.System.now() - lastPlayedAt
            if (sinceLastPlayed < 1.hours) {
                return PreferenceRecommendation(
                    song = song,
                    score = -1f,
                    profile = profile,
                    numberOfMatchingGenres = round3(numberOfMatchingGenres),
                    repetitionCoefficient = round3(-1f),
                    likeCoefficient = null,
                    skipCoefficient = null,
                )
            }

            val repetitionCoefficient = minOf(2.0, sinceLastPlayed / 2.days).toFloat()
            val skipCoefficient = if (stat.skipCount + stat.playCount > 0) {
                2f * (stat.playCount.toFloat() / (stat.playCount + stat.skipCount).toFloat())
            } else {
                2f
            }
            val score =
                clamp01((numberOfMatchingGenres + 0.5f) * repetitionCoefficient * likeCoefficient * skipCoefficient / 16)
            return PreferenceRecommendation(
                song = song,
                score = score,
                profile = profile,
                numberOfMatchingGenres = round3(numberOfMatchingGenres),
                repetitionCoefficient = round3(repetitionCoefficient),
                likeCoefficient = likeCoefficient,
                skipCoefficient = round3(skipCoefficient),
            )
        } else {
            val score = clamp01((numberOfMatchingGenres + 0.5f) / 2)
            return PreferenceRecommendation(
                song = song,
                score = score,
                profile = profile,
                numberOfMatchingGenres = round3(numberOfMatchingGenres),
                repetitionCoefficient = round3(-1f),
                likeCoefficient = -1f,
                skipCoefficient = round3(-1f),
            )
        }
    }

    private fun computeCs(x: Float): Float {
        return if (x > 4.6f) {
            0.3f / (x - 4f)
        } else {
            (0.3f / (x - 5.2f)) + 1f
        }
    }




    fun getIntervalMatchScore(song: SongV2, interval: Interval): IntervalRecommendation {
        val targetCadence = interval.cadence

        val sbpm = song.bpm

        var cadenceDiff: Float
        var cadenceScore: Float
        var cadenceCompatibleBonus = 0f

        val candidateDiffs = mutableListOf<Float>()

        if (sbpm != 0) {
            val match = calculateBpmMatch(sbpm.toFloat(), targetCadence, bpmTolerance = 2.0f)
            if (match.difference != null) {
                candidateDiffs.add(match.difference)
                if (match.compatible == true) {
                    cadenceCompatibleBonus = 0.15f
                }
            }
        }

        if (sbpm != 0) {
            candidateDiffs.add(abs(sbpm.toFloat() - targetCadence.toFloat()))
        }

        if (candidateDiffs.isNotEmpty()) {
            cadenceDiff = candidateDiffs.min()
            cadenceScore = clamp01(computeCs(cadenceDiff))
        } else {
            cadenceScore = 0f
        }


        val tagScore =
            song.tags.sumOf { TAG_WEIGHTS.getOrDefault(it, 0f).toDouble() }
                .toFloat()
        val genreScore = 0 //TODO
        val songScore = clamp02(tagScore + genreScore)

        var totalScore =
            0.90f * cadenceScore + 0.10f * songScore + cadenceCompatibleBonus //TODO
        totalScore = clamp02(totalScore, 1.0f)

        return IntervalRecommendation(
            song = song,
            score = totalScore,
            cadenceScore = round3(cadenceScore),
            songScore = round3(songScore),
            cadenceCompatibleBonus = round3(cadenceCompatibleBonus),
            interval = interval,
        )
    }

    suspend fun getSongRecommendationScore(
        song: SongV2?,
        interval: Interval?,
        profile: Profile
    ): SongRecommendation {
        if (interval == null || song == null) {
            return SongRecommendation(song = song, score = 0f)
        }
        val profilePreference = getProfilePreferenceScore(song, profile)
        val intervalMatch = getIntervalMatchScore(song, interval)
        val score = profilePreference.score + 1.5f * intervalMatch.score

        return SongRecommendation(
            song = song,
            score = score,
            preferenceRecommendation = profilePreference,
            intervalRecommendation = intervalMatch,
        )
    }


    suspend fun recommend(interval: Interval): RecommendationResult {
        var firstCandidatePool: List<SongV2>? = null
        withContext(ioDispatcher) {
            firstCandidatePool = approximateFilterByInterval(interval)
        }

        if (firstCandidatePool.isNullOrEmpty()) {
            VibeLog.w("RecommendationEngine", "RecommendationEngine: firstCandidatePool is empty for interval $interval")
            return RecommendationResult(emptyList(), emptyList())
        }
        val algorithmSemaphore = Semaphore(4)
        val profile: Profile = profileRepository.getProfileForRecommendation()
        val candidates = coroutineScope {
            firstCandidatePool.map { candidate ->
                async(defaultDispatcher) {
                    algorithmSemaphore.withPermit {
                        getSongRecommendationScore(candidate, interval, profile)
                    }
                }
            }.awaitAll()
        }

        val sorted = candidates.sortedWith(
            compareByDescending<SongRecommendation> { it.score }
                .thenByDescending { it.preferenceRecommendation?.score ?: 0f }
                .thenByDescending { it.intervalRecommendation?.cadenceScore ?: 0f }
                .thenByDescending { it.intervalRecommendation?.songScore ?: 0f }
                .thenBy { it.song?.id ?: Int.MAX_VALUE }
        )

        val topSongsCandidates = sorted.mapNotNull { it.song }.take(10)

        VibeLog.i("RecommendationEngine", "RecommendationEngine: recommended ${topSongsCandidates.size} songs with resolved artists")
        return RecommendationResult(topSongsCandidates, sorted)
    }
}

