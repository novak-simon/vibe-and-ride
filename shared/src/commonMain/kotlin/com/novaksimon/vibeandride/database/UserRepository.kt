package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

suspend fun getUserById(id: Int): User? {
    return client.postgrest["users"]
        .select {
            filter { eq("id", id) }
            limit(1)
        }
        .decodeSingleOrNull<User>()
}

suspend fun getCurrentUser(): User? {
    """Temporarily returns always User with ID = 0."""
    return client.postgrest["users"]
        .select {
            filter { eq("id", 0) }
            limit(1)
        }
        .decodeSingleOrNull<User>()
}