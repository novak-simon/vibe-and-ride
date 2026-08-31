package com.novaksimon.vibeandride.server.analysis

import com.novaksimon.vibeandride.model.AudioFeatures
import kotlinx.serialization.json.Json

class AudioFeatureExtractor(
    private val pythonExecutable: String = "C:\\Users\\novak\\AppData\\Local\\Programs\\Python\\Python311\\python.exe",
    private val scriptPath: String
) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun extract(streamUrl: String, songDuration: Int): AudioFeatures? {
        val process = ProcessBuilder(
            pythonExecutable,
            scriptPath,
            streamUrl,
            songDuration.toString()
        )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        val output = process.inputStream
            .bufferedReader()
            .use { it.readText() }
            .trim()

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException(
                "Audio feature extraction failed with exit code $exitCode"
            )
        }
        println("Output: $output")

        return if (output != "null") json.decodeFromString<AudioFeatures>(output)  else null


    }
}