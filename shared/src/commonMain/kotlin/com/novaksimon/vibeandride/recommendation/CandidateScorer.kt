package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.database.getStatByProfileAndSongId
import com.novaksimon.vibeandride.model.AudioFeatures
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.SongV2
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit

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
        val score =
            0.4f * profilePreference.score +
            0.6f * intervalMatch.score

        return SongRecommendation(
            song = song,
            score = score,
            preferenceRecommendation = profilePreference,
            intervalRecommendation = intervalMatch,
        )
    }
    private fun computeRepetition(x: Float):Float {
        return -2/(x+47/24)+1
    }
    private fun computeSkip(x: Float): Float {
        return clamp01(-1 * x * x + 1)
    }
    suspend fun getProfilePreferenceScore(
        song: SongV2?,
        profile: Profile
    ): PreferenceRecommendation {
        val stats = if (song?.id != null) {
            getStatByProfileAndSongId(songId = song.id)
        } else {
            null
        }
        var score = 1f
        var repetitionCoefficient = 1f
        var likeBonus = 0f
        var skipCoefficient = 1f
        if (stats != null) {
            val lastPlayedAt = stats.lastPlayedAt ?: (Clock.System.now() - 365.days)
            val sinceLastPlayed = Clock.System.now() - lastPlayedAt
            repetitionCoefficient = if (sinceLastPlayed < 1.hours) {
                -1f
            } else {
                computeRepetition(sinceLastPlayed.toDouble(DurationUnit.DAYS).toFloat())
            }
            likeBonus = if (stats.isLiked) {
                1f
            } else {
                0f
            }
            skipCoefficient = computeSkip(stats.skipCount/stats.playCount)
            score = clamp01((
                    0.5f * repetitionCoefficient +
                    0.5f * skipCoefficient +
                    1.0f * likeBonus

                )
            )
        }

        return PreferenceRecommendation(
            song = song,
            score = score,
            profile = profile,
            numberOfMatchingGenres = -1f,
            repetitionCoefficient = repetitionCoefficient,
            likeCoefficient = likeBonus,
            skipCoefficient = skipCoefficient,
        )
    }


    private fun computeCs(x: Float): Float {
        return if (x > 4.6f) {
            0.3f / (x - 4f)
        } else {
            (0.3f / (x - 5.2f)) + 1f
        }
    }
    private fun computeLoudness(x: Float): Float {
        return -6/x
    }
    private fun computeRmsStd(x: Float): Float {
        return 1/(x+5)+4/5
    }
    private fun computeSpectralFlux(x: Float): Float {
        return -1/(10*x-1.1f)-1.1f
    }
    private fun computeOnsetDensity(x: Float): Float {
        return -1/(2*x+1)+1
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
        //  === Audio Features Scoring === Energy ===
        val loudnessLufsScore = clamp01(computeLoudness(audioFeatures.loudnessLufs.toFloat()))
        val rmsDbScore = clamp01(computeLoudness(audioFeatures.rmsDb.toFloat()))
        val rmsDbStdScore = clamp01(computeRmsStd(audioFeatures.rmsDbStd.toFloat()))
        val spectralFluxScore = clamp01(computeSpectralFlux(audioFeatures.spectralFlux.toFloat()))
        val onsetDensity = clamp01(computeOnsetDensity(audioFeatures.onsetDensity.toFloat()))
        var energyScore =
                    0.20f * loudnessLufsScore +
                    0.20f * rmsDbScore +
                    0.15f * rmsDbStdScore +
                    0.35f * spectralFluxScore +
                    0.10f * onsetDensity

        // === Tag Penalties === Energy ===
        if ("ambient" in song.tags) energyScore /= 10
        else if ("podcast" in song.genre.lowercase()) energyScore = 0f

        val totalScore = clamp01((
                    0.10f * cadenceScore +
                    0.90f * energyScore +
                    cadenceCompatibleBonus
                    )
                )

        return IntervalRecommendation(
            song = song,
            score = totalScore,
            cadenceScore = round3(cadenceScore),
            energyScore = round3(energyScore),
            cadenceCompatibleBonus = round3(cadenceCompatibleBonus),
            interval = interval,
        )
    }
}