package com.novaksimon.vibeandride.workout

import com.novaksimon.vibeandride.model.Workout

sealed class WorkoutMode {
    data class Structured(val workout: Workout) : WorkoutMode()
    object FreeRide : WorkoutMode()
}
