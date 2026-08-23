package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawProfile
import com.novaksimon.vibeandride.model.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

suspend fun getProfileById(id: String?): Profile? {
    if (id.isNullOrEmpty()) return null
    val rawProfile = client.postgrest["profiles"]
        .select {
            filter {
                eq("id", id)
            }
            limit(1)
        }
        .decodeSingleOrNull<RawProfile>()
    return rawProfile?.let { rawToProfile(it) }
}

internal suspend fun getCurrentProfile(): Profile? {
    val id = client.auth.currentUserOrNull()?.id
    println("PROFILE ID: $id")
    return getProfileById(id)
}

suspend fun getProfileForRecommendation(profileId: String? = null): Profile {
    return profileId?.let { getProfileById(it) } ?: getCurrentProfile() ?: Profile("9c9369c2-0598-4948-802d-525490e86bb5", listOf("pop"))
}
