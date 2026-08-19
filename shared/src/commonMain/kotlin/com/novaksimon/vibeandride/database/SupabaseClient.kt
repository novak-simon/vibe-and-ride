package com.novaksimon.vibeandride.database

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://ehosthiruiasknnnbvcc.supabase.co", // see note below re: config
        supabaseKey = "sb_publishable_5c6hdoZmb0apj59s7WpAxw_eqdHAHtI",
    ) {
        install(Postgrest)
    }
}