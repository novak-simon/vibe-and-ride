package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.RecommendationEngine
import com.novaksimon.vibeandride.recommendation.RecommendationResult
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.workout.WorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MusicProvider(uiState: MutableStateFlow<MusicUiState>) {
    private val recommender = RecommendationEngine()
    private var workout: Workout? = null
    private var recommendationBuffer: MutableList<RecommendationResult?> = mutableListOf()
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recommendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recommendSemaphore = Semaphore(4)

    fun workoutSelected(state: WorkoutState) {
        workout = state.workout
        var intervalCount = 0
        workout?.intervals?.let { intervalCount = workout!!.intervals!!.size }
        recommendationBuffer = MutableList(intervalCount) { null }
        println("Interval index on workoutSelected: ${state.intervalIndex}")
        if (state.intervalIndex != null) {
            recommendScope.launch {
                val recommendation = recommendSemaphore.withPermit {
                    println("Recommending songs for interval: ${state.intervalIndex}")
                    recommender.recommend(state.currentInterval)
                }

                recommendationBuffer[state.intervalIndex] = recommendation
                println("Recommendation complete")
                println(recommendation)

            }
            recommendScope.launch {
                val recommendation = recommendSemaphore.withPermit {
                    println("Recommending songs for interval: ${state.intervalIndex + 1}")
                    recommender.recommend(state.workout?.intervals?.get(state.intervalIndex + 1))
                }

                recommendationBuffer[state.intervalIndex + 1] = recommendation
                println("Recommendation complete")
                println(recommendation)
            }
        }
    }

    fun play() {

    }

    fun skip() {
        TODO("Not yet implemented")
    }

    fun precomputeNextRecommendation(workoutState: WorkoutState) {
        if (workoutState.intervalIndex != null) {
            if (recommendationBuffer[workoutState.intervalIndex + 1] != null) {
                return
            }
            recommendScope.launch {
                val recommendation = recommendSemaphore.withPermit {
                    println("Recommending songs for interval: ${workoutState.intervalIndex + 1}")
                    recommender.recommend(workoutState.workout?.intervals?.get(workoutState.intervalIndex + 1))
                }

                recommendationBuffer[workoutState.intervalIndex + 1] = recommendation
                println("Recommendation complete")
                println(recommendation)

            }
        }
    }
}
