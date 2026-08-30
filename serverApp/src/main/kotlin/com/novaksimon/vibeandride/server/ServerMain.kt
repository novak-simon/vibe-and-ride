package com.novaksimon.vibeandride.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import com.novaksimon.vibeandride.server.import.JamendoImporter
import com.novaksimon.vibeandride.server.analysis.AudioFeatureExtraction
import com.novaksimon.vibeandride.server.import.AudiusImporterV2
import kotlinx.coroutines.launch
import java.io.File

fun loadDotEnv(file: File) {
    if (!file.exists()) return
    file.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
            val (key, value) = trimmed.split("=", limit = 2)
            System.setProperty(key.trim(), value.trim())
        }
    }
}

fun main() {
    loadDotEnv(File(".env"))

    val supabaseUrl = "https://ehosthiruiasknnnbvcc.supabase.co"
    val supabaseKey = System.getProperty("SUPABASE_SERVICE_KEY") ?: System.getenv("SUPABASE_SERVICE_KEY") ?: ""
    val jamendoClientId = System.getProperty("JAMENDO_CLIENT_ID") ?: System.getenv("JAMENDO_CLIENT_ID") ?: ""

    val supabase = createSupabaseClient(supabaseUrl, supabaseKey) {
        install(Postgrest)
    }

    val jamendoImporter = JamendoImporter(supabase, jamendoClientId)
    val audiusImporterV2 = AudiusImporterV2(supabase)
    val audioFeatureExtraction = AudioFeatureExtraction(supabase)

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/") {
                call.respondText("Vibe & Ride Backend is Running")
            }
            get("/status") {
                call.respond(StatusResponse(status = "OK", version = "1.0.0"))
            }
            post("/import/jamendo") {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
                val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
                launch { jamendoImporter.import(limit, offset) }
                call.respond(StatusResponse(status = "Import started", limit = limit, offset = offset))
            }
            post("/import/audius") {
                val latest = call.parameters["latest"]?.toBoolean() ?: false
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 500
                val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
                launch { audiusImporterV2.fetchAudiusSongs(latest = latest, limit = limit, offset = offset) }
                call.respond(StatusResponse(status = "Audius V2 import started"))
            }
            post("/analyze") {
                launch { audioFeatureExtraction.analyzeUnanalyzedSongs() }
                call.respond(StatusResponse(status = "Audio feature extraction started for unanalyzed songs"))
            }
        }
    }.start(wait = true)
}
