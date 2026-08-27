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
                            fun attr(name: String, defaultValue: Double = 0.0): Double {
                                return reader.getAttributeValue("", name)?.toDoubleOrNull() ?: defaultValue
                            }

                            if (localName == "IntervalsT") {
                                val repeat = attr("Repeat").toInt()
                                val onDuration = attr("OnDuration")
                                val offDuration = attr("OffDuration")
                                
                                val onPower = parsePower(attr("OnPower"), attr("OnPowerLow"), attr("OnPowerHigh"))
                                val offPower = parsePower(attr("OffPower"), attr("OffPowerLow"), attr("OffPowerHigh"))
                                
                                val onCadence = parseCadence(attr("OnCadence"), attr("OnCadenceLow"), attr("OnCadenceHigh"))
                                val offCadence = parseCadence(attr("OffCadence"), attr("OffCadenceLow"), attr("OffCadenceHigh"))

                                repeat(repeat) {
                                    intervals.add(Interval(duration = onDuration, power = onPower, cadence = onCadence, type = "On"))
                                    intervals.add(Interval(duration = offDuration, power = offPower, cadence = offCadence, type = "Off"))
                                }
                            } else {
                                val duration = attr("Duration")
                                val power = parsePower(attr("Power"), attr("PowerLow"), attr("PowerHigh"))
                                val cadence = parseCadence(attr("Cadence"), attr("CadenceLow"), attr("CadenceHigh"))

                                intervals.add(
                                    Interval(
                                        duration = duration,
                                        power = power,
                                        cadence = cadence,
                                        type = localName
                                    )
                                )
                            }
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

    private fun parsePower(power: Double, low: Double, high: Double): Float {
        return if (power != 0.0) {
            power.toFloat()
        } else if (low != 0.0 && high != 0.0) {
            ((low + high) / 2.0).toFloat()
        } else if (low != 0.0) {
            low.toFloat()
        } else {
            high.toFloat()
        }
    }

    private fun parseCadence(cadence: Double, low: Double, high: Double): Double {
        return if (cadence != 0.0) {
            cadence
        } else if (low != 0.0 && high != 0.0) {
            kotlin.math.round((low + high) / 2.0)
        } else if (low != 0.0) {
            low
        } else {
            high
        }
    }
}
