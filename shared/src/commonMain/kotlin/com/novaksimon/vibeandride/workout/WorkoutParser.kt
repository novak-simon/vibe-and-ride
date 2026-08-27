package com.novaksimon.vibeandride.workout

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.database.uploadWorkout
import nl.adaptivity.xmlutil.*
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.model.WorkoutToUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun XmlReader.nextText(): String {
    val sb = StringBuilder()
    while (hasNext()) {
        val event = next()
        if (event == EventType.TEXT || event == EventType.CDSECT) {
            sb.append(text)
        } else if (event == EventType.END_ELEMENT) {
            break
        }
    }
    return sb.toString()
}

class WorkoutParser {

    fun parseZwoString(
        content: String,
        fileName: String = "Unnamed workout"
    ): WorkoutToUpload {
        var name: String? = null
        var author: String? = null
        var sportType: String? = null
        var description: String? = null
        val tags = mutableListOf<String>()
        val intervals = mutableListOf<Interval>()

        val reader = xmlStreaming.newReader(content)
        var inWorkout = false

        reader.use { reader ->
            while (reader.hasNext()) {
                when (reader.next()) {
                    EventType.START_ELEMENT -> {
                        val localName = reader.localName
                        if (inWorkout) {
                            fun attr(name: String): Double {
                                return reader.getAttributeValue("", name)?.toDoubleOrNull() ?: 0.0
                            }

                            val duration = attr("Duration")
                            var power = attr("Power").toFloat()
                            if (power == 0f) {
                                val pLow = attr("PowerLow").toFloat()
                                val pHigh = attr("PowerHigh").toFloat()
                                power = if (pLow != 0f && pHigh != 0f) {
                                    (pLow + pHigh) / 2f
                                } else if (pLow != 0f) {
                                    pLow
                                } else {
                                    pHigh
                                }
                            }

                            var cadence = attr("Cadence")
                            if (cadence == 0.0) {
                                val cLow = attr("CadenceLow")
                                val cHigh = attr("CadenceHigh")
                                cadence = if (cLow != 0.0 && cHigh != 0.0) {
                                    kotlin.math.round((cLow + cHigh) / 2)
                                } else if (cLow != 0.0) {
                                    cLow
                                } else {
                                    cHigh
                                }
                            }

                            intervals.add(
                                Interval(
                                    duration = duration,
                                    power = power,
                                    cadence = cadence,
                                    type = localName
                                )
                            )
                        } else {
                            when (localName) {
                                "author" -> author = reader.nextText()
                                "name" -> {
                                    val text = reader.nextText()
                                    name = if (text != "Zwift Workout") text else fileName
                                }

                                "sportType" -> sportType = reader.nextText()
                                "description" -> description = reader.nextText()
                                "tag" -> {
                                    val t = reader.nextText()
                                    if (t.isNotBlank()) tags.add(t)
                                }

                                "workout" -> inWorkout = true
                            }
                        }
                    }

                    EventType.END_ELEMENT -> {
                        if (reader.localName == "workout") {
                            inWorkout = false
                        }
                    }

                    else -> {}
                }
            }
        }

        val sportTypeIsBike = sportType?.trim()?.lowercase() == "bike"

        return WorkoutToUpload(
            name = name,
            author = author,
            sportType = sportTypeIsBike,
            tags = tags,
            description = description,
            intervals = intervals
        )
    }

    suspend fun parseAndUpload(
        content: String,
        fileName: String
    ): Workout {
        val workout = withContext(Dispatchers.Default) {
            parseZwoString(content, fileName)
        }
        return withContext(Dispatchers.IO) { uploadWorkout(workout) }
    }
}