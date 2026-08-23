package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.database.getCurrentUser
import com.novaksimon.vibeandride.database.getStatByUserAndSongId
import com.novaksimon.vibeandride.music.UserMusicService
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.User
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

private const val FIRST_CANDIDATE_POOL_LIMIT = 50

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

private val INSTRUMENT_WEIGHTS = mapOf(
    "drums" to 0.1f, "drummachine" to 0.1f, "bass" to 0.1f,
    "electricguitar" to 0.05f, "electroamplified" to 0.05f, "synthesizer" to 0.05f,
)

private val GENRE_WEIGHTS = mapOf(
    "ambient" to -1f, "ambientdub" to -1f, "drone" to -1f,
)

data class RecommendationResult(
    val topSongs: List<Song>,
    val debugInfo: List<SongRecommendation>,
)

class RecommendationEngine(userMusicService: UserMusicService) {

    val service = userMusicService

    suspend fun approximateFilterByInterval(
        interval: Interval,
    ): List<Song> {
        val cadence = interval.cadence.toInt()
        val targetCadence = getAlgorithmCadence(cadence)
        var pool = service.getSongsAroundTargetCadence(
            targetCadence,
            algorithmCadenceToleration = 2,
            limit = FIRST_CANDIDATE_POOL_LIMIT
        )
        if (pool.size < FIRST_CANDIDATE_POOL_LIMIT) {
            pool = pool.union(
                service.getSongsAroundTargetCadence(
                    targetCadence,
                    algorithmCadenceToleration = 5,
                    limit = FIRST_CANDIDATE_POOL_LIMIT - pool.size
                )
            ).toList()
            if (pool.size < FIRST_CANDIDATE_POOL_LIMIT) {
                pool = pool.union(
                    service.getSongsAroundTargetCadence(
                        targetCadence,
                        algorithmCadenceToleration = 10,
                        limit = FIRST_CANDIDATE_POOL_LIMIT
                    )
                ).toList()
                if (pool.isEmpty()) {
                    pool = service.fetchSongs(FIRST_CANDIDATE_POOL_LIMIT / 3)
                }
            }
        }
        return pool
    }

    suspend fun getUserPreferenceScore(
        song: Song?,
        user: User?,
    ): PreferenceRecommendation {
        val resolvedUser = user ?: getCurrentUser()
        if (song == null) {
            return PreferenceRecommendation()
        }

        val numberOfMatchingGenres: Float = if (song.genre.isNotEmpty()) {
            song.genre.intersect(resolvedUser.genrePreferences.toSet()).size.toFloat()
        } else {
            0.5f
        }

        val stat = getStatByUserAndSongId(resolvedUser.id, song.id)

        if (stat != null) {
            val lastPlayedAt = stat.lastPlayedAt
            if (lastPlayedAt == null) {
                // No recorded play time despite stat existing — treat as "long ago" (same as normal branch below)
                val likeCoefficient = if (stat.isLiked) 2f else 1f
                val skipCoefficient = if (stat.skipCount != 0) {
                    minOf(
                        2f,
                        stat.playCount / 3f * stat.skipCount
                    ) // NOTE: preserves original (possibly buggy) formula — see flag above
                } else {
                    2f
                }
                val score = numberOfMatchingGenres * 2f * likeCoefficient * skipCoefficient
                return PreferenceRecommendation(
                    song = song,
                    score = score,
                    user = resolvedUser,
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
                    user = resolvedUser,
                    numberOfMatchingGenres = round3(numberOfMatchingGenres),
                    repetitionCoefficient = round3(-1f),
                    likeCoefficient = null,
                    skipCoefficient = null,
                )
            }

            val repetitionCoefficient = minOf(2.0, sinceLastPlayed / 2.days).toFloat()
            val likeCoefficient = if (stat.isLiked) 2f else 1f
            val skipCoefficient = if (stat.skipCount != 0) {
                minOf(
                    2f,
                    stat.playCount / 3f * stat.skipCount
                ) // NOTE: preserves original formula — flagged as likely bug above
            } else {
                2f
            }
            val score =
                clamp01((numberOfMatchingGenres + 0.5f) * repetitionCoefficient * likeCoefficient * skipCoefficient / 16)
            return PreferenceRecommendation(
                song = song,
                score = score,
                user = resolvedUser,
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
                user = resolvedUser,
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



    fun getIntervalMatchScore(song: Song, interval: Interval): IntervalRecommendation {
        val targetCadence = interval.cadence

        val sbpm = song.bpm
        val bpmConf = song.bpmConfidence

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

        val confScore = clamp01(bpmConf / 100f)

        val tagScore =
            song.vartags.sumOf { TAG_WEIGHTS.getOrDefault(it, 0f).toDouble() }
                .toFloat()
        val instrumentScore = song.instruments.sumOf {
            INSTRUMENT_WEIGHTS.getOrDefault(it, 0f).toDouble()
        }.toFloat()
        val genreScore =
            song.genre.sumOf { GENRE_WEIGHTS.getOrDefault(it, 0f).toDouble() }
                .toFloat()
        val songScore = clamp02(tagScore + instrumentScore + genreScore)

        var totalScore =
            0.05f * cadenceScore + 0.05f * confScore + 0.90f * songScore + cadenceCompatibleBonus
        totalScore = clamp02(totalScore, 1.0f)

        return IntervalRecommendation(
            song = song,
            score = totalScore,
            cadenceScore = round3(cadenceScore),
            confScore = round3(confScore),
            songScore = round3(songScore),
            cadenceCompatibleBonus = round3(cadenceCompatibleBonus),
            interval = interval,
        )
    }

    suspend fun getSongRecommendationScore(
        song: Song?,
        user: User?,
        interval: Interval?,
    ): SongRecommendation {
        if (user == null || interval == null || song == null) {
            return SongRecommendation(song = song, score = 0f)
        }
        val userPreference = getUserPreferenceScore(song, user)
        val intervalMatch = getIntervalMatchScore(song, interval)
        val score = userPreference.score + 1.5f * intervalMatch.score

        return SongRecommendation(
            song = song,
            score = score,
            preferenceRecommendation = userPreference,
            intervalRecommendation = intervalMatch,
        )
    }



    suspend fun recommend(interval: Interval): RecommendationResult {

        val user = getCurrentUser()
        var firstCandidatePool: List<Song>? = null
        withContext(Dispatchers.IO) {
            firstCandidatePool = approximateFilterByInterval(interval)
        }
        if (firstCandidatePool.isNullOrEmpty()) {
            return RecommendationResult(emptyList(), emptyList())
        }
        val algorithmSemaphore = Semaphore(4)
        val candidates = coroutineScope {
            firstCandidatePool.map { candidate ->
                async(Dispatchers.Default) {
                    algorithmSemaphore.withPermit {
                        getSongRecommendationScore(candidate, user, interval)
                    }
                }
            }.awaitAll()
        }

        val sorted = candidates.sortedWith(
            compareByDescending<SongRecommendation> { it.score }
                .thenByDescending { it.preferenceRecommendation?.score ?: 0f }
                .thenByDescending { it.intervalRecommendation?.cadenceScore ?: 0f }
                .thenByDescending { it.intervalRecommendation?.confScore ?: 0f }
                .thenByDescending { it.intervalRecommendation?.songScore ?: 0f }
                .thenBy { it.song?.id ?: Int.MAX_VALUE }
        )

        val topSongs = sorted.mapNotNull { it.song }.take(10)
        return RecommendationResult(topSongs, sorted)
    }
}