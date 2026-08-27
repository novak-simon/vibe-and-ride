package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawWorkout
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.model.WorkoutToUpload
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
suspend fun listWorkouts(): List<Workout> {
    val workouts = client.postgrest["workouts"]
        .select {
            order("name", Order.ASCENDING)
        }
        .decodeList<RawWorkout>()
        .map { rawToWorkout(it) }
    return workouts
}

suspend fun getWorkoutById(id: Int): Workout? {
    val rawWorkout = client.postgrest["workouts"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawWorkout>()
    return if (rawWorkout != null) rawToWorkout(rawWorkout) else null
}

/**
 * TODO: uploadParsedWorkout currently relies on positional indexing into
 * parsed XML children (matches original Python behavior) — fragile if
 * element order varies. Flagged for fixing later, see project todo list.
 */
suspend fun uploadWorkout(
    workout: WorkoutToUpload,
): Workout {
    val records = client
        .from("workouts")
        .insert(workout) {
            select()
        }
        .decodeList<RawWorkout>()

    if (records.isEmpty()) {
        throw RuntimeException("Failed to insert workout")
    }
    return rawToWorkout(records.first())
}