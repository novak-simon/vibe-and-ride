package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawProfile
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://ehosthiruiasknnnbvcc.supabase.co", // see note below re: config
        supabaseKey = "sb_publishable_5c6hdoZmb0apj59s7WpAxw_eqdHAHtI",
    ) {
        install(Auth)
        install(Postgrest)
    }
}

suspend fun initializeProfile() {
    println("Supabase: initializing profile...")

    try {
        client.auth.awaitInitialization()

        if (client.auth.currentUserOrNull() == null) {
            println("Supabase: no existing session, signing in anonymously...")
            client.auth.signInAnonymously()
        }

        val user = client.auth.currentUserOrNull()
            ?: error("No authenticated user")

        println("Supabase: User ID: ${user.id}")

        client
            .from("profiles")
            .upsert(RawProfile(user.id))

        println("Supabase: Profile initialized")

    } catch (e: Exception) {
        println("Supabase initialization error: ${e.message}")
        e.printStackTrace()
    }
}
