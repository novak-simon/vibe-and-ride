package com.novaksimon.vibeandride.server.analysis

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import com.novaksimon.vibeandride.model.RawSong
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.query.filter.FilterOperation

class AnalysisOrchestrator(
    private val supabase: SupabaseClient,
    private val pythonProjectPath: String = "C:/Users/novak/Documents/vibe-and-ride-utils",
    private val pythonExecutable: String = "python"
) {

    suspend fun runAnalysis(limit: Int = 10) {
        println("AnalysisOrchestrator: Fetching unanalyzed songs...")
        
        // Complex OR filter in supabase-kt
        val unanalyzedSongs = supabase.from("songs")
            .select {
                filter {
                    or {
                        // Assuming eq with null might work if passed as a specific type or if we use the operator
                        filter("bpm", FilterOperator.IS, "null")
                        and {
                            or {
                                filter("start_time", FilterOperator.IS, "null")
                                eq("start_time", 0)
                            }
                            or {
                                filter("end_time", FilterOperator.IS, "null")
                                eq("end_time", 0)
                            }
                        }

                        filter("end_time", FilterOperator.IS, "null")
                        eq("end_time", 0)
                    }
                }
                limit(limit.toLong())
            }
            .decodeList<RawSong>()

        println("AnalysisOrchestrator: Found ${unanalyzedSongs.size} songs to analyze.")

        unanalyzedSongs.forEach { song ->
            try {
                analyzeSong(song)
            } catch (e: Exception) {
                println("AnalysisOrchestrator: Error analyzing song ${song.id}: ${e.message}")
            }
        }
    }

    private suspend fun analyzeSong(song: RawSong) = withContext(Dispatchers.IO) {
        val songId = song.id
        println("AnalysisOrchestrator: Triggering Python analysis for song $songId (using $pythonExecutable)")

        // Use the configured python executable
        val pb = ProcessBuilder(pythonExecutable, "main.py", "analyze", "--id", songId.toString())
        pb.directory(File(pythonProjectPath))
        pb.redirectErrorStream(true)
        
        val process = pb.start()
        
        // Read output in a separate coroutine to avoid blocking
        val outputJob = launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    println("[Python Analysis $songId]: $line")
                }
            }
        }

        val exited = process.waitFor(5, TimeUnit.MINUTES)
        outputJob.join()

        if (!exited) {
            process.destroyForcibly()
            println("AnalysisOrchestrator: Python analysis timed out for song $songId")
            return@withContext
        }

        if (process.exitValue() == 0) {
            println("AnalysisOrchestrator: Successfully analyzed song $songId")
        } else {
            println("AnalysisOrchestrator: Python analysis failed for song $songId (exit code ${process.exitValue()})")
        }
    }
}
