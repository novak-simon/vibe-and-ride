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
                                
                                val onPowerLow = reader.getAttributeValue("", "OnPowerLow")?.toFloatOrNull()
                                val onPowerHigh = reader.getAttributeValue("", "OnPowerHigh")?.toFloatOrNull()
                                val onPower = reader.getAttributeValue("", "OnPower")?.toFloatOrNull() 
                                    ?: (if (onPowerLow != null && onPowerHigh != null) (onPowerLow + onPowerHigh) / 2f else 0f)

                                val offPowerLow = reader.getAttributeValue("", "OffPowerLow")?.toFloatOrNull()
                                val offPowerHigh = reader.getAttributeValue("", "OffPowerHigh")?.toFloatOrNull()
                                val offPower = reader.getAttributeValue("", "OffPower")?.toFloatOrNull()
                                    ?: (if (offPowerLow != null && offPowerHigh != null) (offPowerLow + offPowerHigh) / 2f else 0f)

                                val onCadenceLow = reader.getAttributeValue("", "OnCadenceLow")?.toDoubleOrNull()
                                val onCadenceHigh = reader.getAttributeValue("", "OnCadenceHigh")?.toDoubleOrNull()
                                val onCadence = reader.getAttributeValue("", "OnCadence")?.toDoubleOrNull()
                                    ?: (if (onCadenceLow != null && onCadenceHigh != null) kotlin.math.round((onCadenceLow + onCadenceHigh) / 2.0) else 0.0)

                                val offCadenceLow = reader.getAttributeValue("", "OffCadenceLow")?.toDoubleOrNull()
                                val offCadenceHigh = reader.getAttributeValue("", "OffCadenceHigh")?.toDoubleOrNull()
                                val offCadence = reader.getAttributeValue("", "OffCadence")?.toDoubleOrNull()
                                    ?: (if (offCadenceLow != null && offCadenceHigh != null) kotlin.math.round((offCadenceLow + offCadenceHigh) / 2.0) else 0.0)

                                repeat(repeat) {
                                    intervals.add(
                                        Interval(
                                            duration = onDuration, 
                                            power = onPower, 
                                            cadence = onCadence, 
                                            type = "On",
                                            powerLow = onPowerLow,
                                            powerHigh = onPowerHigh,
                                            cadenceLow = onCadenceLow,
                                            cadenceHigh = onCadenceHigh
                                        )
                                    )
                                    intervals.add(
                                        Interval(
                                            duration = offDuration, 
                                            power = offPower, 
                                            cadence = offCadence, 
                                            type = "Off",
                                            powerLow = offPowerLow,
                                            powerHigh = offPowerHigh,
                                            cadenceLow = offCadenceLow,
                                            cadenceHigh = offCadenceHigh
                                        )
                                    )
                                }
                            } else {
                                val duration = attr("Duration")
                                val pLow = reader.getAttributeValue("", "PowerLow")?.toFloatOrNull()
                                val pHigh = reader.getAttributeValue("", "PowerHigh")?.toFloatOrNull()
                                val p = reader.getAttributeValue("", "Power")?.toFloatOrNull()
                                    ?: (if (pLow != null && pHigh != null) (pLow + pHigh) / 2f else 0f)
                                    
                                val cLow = reader.getAttributeValue("", "CadenceLow")?.toDoubleOrNull()
                                val cHigh = reader.getAttributeValue("", "CadenceHigh")?.toDoubleOrNull()
                                val c = reader.getAttributeValue("", "Cadence")?.toDoubleOrNull()
                                    ?: (if (cLow != null && cHigh != null) kotlin.math.round((cLow + cHigh) / 2.0) else 0.0)

                                intervals.add(
                                    Interval(
                                        duration = duration,
                                        power = p,
                                        cadence = c,
                                        type = localName,
                                        powerLow = pLow,
                                        powerHigh = pHigh,
                                        cadenceLow = cLow,
                                        cadenceHigh = cHigh
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
}
