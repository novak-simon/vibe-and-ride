package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawUser
import com.novaksimon.vibeandride.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

suspend fun getUserById(id: Int): User? {
    val rawUser = client.postgrest["users"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<RawUser>()
    return rawUser?.let { rawToUser(it) }
}

suspend fun getCurrentUser(): User {
    """Temporarily returns always User with ID = 0."""
    val rawUser = client.postgrest["users"]
        .select {
            filter { eq("id", 0) }
            limit(1)
        }
        .decodeSingleOrNull<RawUser>()
    return rawUser?.let { rawToUser(it) } ?: User(id = 0, name = "User", genrePreferences = listOf("pop"))
}