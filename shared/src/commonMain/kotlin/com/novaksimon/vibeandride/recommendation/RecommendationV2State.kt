package com.novaksimon.vibeandride.recommendation

/**
 * State representing the recommendations for the current and future interval.
 */
data class RecommendationV2State(
    val current: RecommendationResult? = null,
    val future: RecommendationResult? = null,
    val currentIntervalIndex: Int = -1,
    val isLoadingCurrent: Boolean = false,
    val isLoadingFuture: Boolean = false
)
