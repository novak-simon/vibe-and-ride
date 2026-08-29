package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.database.SupabaseClientProvider.client
import com.novaksimon.vibeandride.model.RawProfile
import com.novaksimon.vibeandride.util.VibeLog
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://ehosthiruiasknnnbvcc.supabase.co", // see note below re: config
        supabaseKey = "sb_publishable_5c6hdoZmb0apj59s7WpAxw_eqdHAHtI",
    ) {
        install(Auth)
        install(Postgrest)
    }
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    fun setInitialized() {
        _isInitialized.value = true
    }
}

suspend fun initializeProfile() {
    VibeLog.i("Supabase", "Supabase: initializing profile...")

    try {
        client.auth.awaitInitialization()

        if (client.auth.currentUserOrNull() == null) {
            VibeLog.i("Supabase", "Supabase: no existing session, signing in anonymously...")
            client.auth.signInAnonymously()
        }

        val user = client.auth.currentUserOrNull()
            ?: error("No authenticated user")

        VibeLog.i("Supabase", "Supabase: User ID: ${user.id}")

        client
            .from("profiles")
            .upsert(RawProfile(user.id))

        VibeLog.i("Supabase", "Supabase: Profile initialized")
        SupabaseClientProvider.setInitialized()

    } catch (e: Exception) {
        VibeLog.e("Supabase", "Supabase initialization error", e)
    }
}
