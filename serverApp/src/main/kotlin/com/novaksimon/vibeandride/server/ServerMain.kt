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
import com.novaksimon.vibeandride.server.import.AudiusImporter
import com.novaksimon.vibeandride.server.analysis.AnalysisOrchestrator
import com.novaksimon.vibeandride.server.analysis.AudioFeatureExtraction
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
    val pythonExecutable = System.getProperty("PYTHON_EXECUTABLE") ?: System.getenv("PYTHON_EXECUTABLE") ?: "python"

    val supabase = createSupabaseClient(supabaseUrl, supabaseKey) {
        install(Postgrest)
    }

    val jamendoImporter = JamendoImporter(supabase, jamendoClientId)
    val audiusImporter = AudiusImporter(supabase)
    val analysisOrchestrator = AnalysisOrchestrator(supabase, pythonExecutable = pythonExecutable)
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
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
                val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
                launch { audiusImporter.importTrending(limit, offset) }
                call.respond(StatusResponse(status = "Import started", limit = limit, offset = offset))
            }
            post("/analyze") {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 5
                launch { analysisOrchestrator.runAnalysis(limit) }
                call.respond(StatusResponse(status = "Analysis started", limit = limit))
            }
            post("/analyze/features") {
                launch { audioFeatureExtraction.analyzeUnanalyzedSongs() }
                call.respond(StatusResponse(status = "Audio feature extraction started for unanalyzed songs"))
            }
        }
    }.start(wait = true)
}
