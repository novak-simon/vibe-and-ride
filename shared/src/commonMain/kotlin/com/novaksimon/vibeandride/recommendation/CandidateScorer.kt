package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.AudioFeatures
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.model.SongV2
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class CandidateScorer(
    private val profileRepository: ProfileRepository,
) {



    suspend fun getSongRecommendationScore(
        song: SongV2,
        interval: Interval,
        audioFeatures: AudioFeatures,
    ): SongRecommendation {
        val profile = profileRepository.getProfileForRecommendation()
        val profilePreference = getProfilePreferenceScore(song, profile)
        val intervalMatch = getIntervalMatchScore(song, interval, audioFeatures)
        val score = profilePreference.score + 1.5f * intervalMatch.score

        return SongRecommendation(
            song = song,
            score = score,
            preferenceRecommendation = profilePreference,
            intervalRecommendation = intervalMatch,
        )
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



    fun getIntervalMatchScore(song: SongV2, interval: Interval, audioFeatures: AudioFeatures): IntervalRecommendation {
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


        val energyScore = 0f
        val frequencyScore = 0f
        val songScore = clamp02(energyScore + frequencyScore)

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
}