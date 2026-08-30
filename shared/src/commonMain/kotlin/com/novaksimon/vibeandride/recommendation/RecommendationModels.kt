package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.SongV2

data class PreferenceRecommendation(
    val song: SongV2? = null,
    val score: Float = 0f,
    val profile: Profile? = null,
    val numberOfMatchingGenres: Float? = 0f,
    val repetitionCoefficient: Float? = 0f,
    val likeCoefficient: Float? = 0f,
    val skipCoefficient: Float? = 0f,
)

data class IntervalRecommendation(
    val song: SongV2? = null,
    val score: Float = 0f,
    val cadenceScore: Float? = 0f,
    val songScore: Float? = 0f,
    val cadenceCompatibleBonus: Float? = 0f,
    val interval: Interval? = null,
)

data class SongRecommendation(
    val song: SongV2? = null,
    val score: Float? = 0f,
    val preferenceRecommendation: PreferenceRecommendation? = null,
    val intervalRecommendation: IntervalRecommendation? = null,
)

data class BPMMatch(
    val compatible: Boolean?,
    val effectiveBpm: Float?,
    val difference: Float?,
    val multiplier: Float?, // 0.5, 1, 2
)
