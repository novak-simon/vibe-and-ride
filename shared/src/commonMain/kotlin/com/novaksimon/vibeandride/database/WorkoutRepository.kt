package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.model.WorkoutToUpload
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.put
suspend fun listWorkouts(): List<Workout> {
    return client.postgrest["workouts"]
        .select {
            order("name", Order.ASCENDING)
        }
        .decodeList<Workout>()
}

suspend fun getWorkoutById(id: Int): Workout? {
    return client.postgrest["workouts"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<Workout>()
}

suspend fun valueExists(value: String, column: String): Boolean {
    return client.postgrest["workouts"]
        .select {
            filter { eq(column, value) }
            limit(1)
        }
        .decodeList<Map<String, kotlinx.serialization.json.JsonElement>>()
        .isNotEmpty()
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
        .decodeList<Workout>()

    if (records.isEmpty()) {
        throw RuntimeException("Failed to insert workout")
    }
    return records.first()
}

private fun isBikeSport(value: Any?): Boolean {
    return when (value) {
        is Boolean -> value
        null -> false
        is Int -> value == 1
        is String -> value.trim().lowercase() in setOf("bike", "bicycle", "cycling", "true", "yes", "1")
        else -> false
    }
}

private fun buildJsonObjectFromWorkoutFields(
    name: String?, author: String?, sportType: Boolean?,
    description: String?, intervals: Any?,
): kotlinx.serialization.json.JsonObject {
    // placeholder — needs a proper serializer for `intervals` shape once confirmed
    return kotlinx.serialization.json.buildJsonObject {
        name?.let { put("name", it) }
        author?.let { put("author", it) }
        sportType?.let { put("sport_type_is_bike", it) }
        description?.let { put("description", it) }
    }
}
