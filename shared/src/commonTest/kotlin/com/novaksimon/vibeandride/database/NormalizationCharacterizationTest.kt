package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.model.RawInterval
import com.novaksimon.vibeandride.model.RawWorkout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormalizationCharacterizationTest {

    @Test
    fun testRawToIntervalNormalValues() {
        val raw = RawInterval(
            duration = 300.0,
            power = 0.8f,
            cadence = 90.0,
            type = "SteadyState"
        )
        val interval = rawToInterval(raw)
        assertEquals(300.0, interval.duration)
        assertEquals(0.8f, interval.power)
        assertEquals(90.0, interval.cadence)
        assertEquals("SteadyState", interval.type)
    }

    @Test
    fun testRawToIntervalDefaultDuration() {
        val raw = RawInterval(duration = null, power = 0.5f)
        val interval = rawToInterval(raw)
        assertEquals(60.0, interval.duration)

        val rawZero = RawInterval(duration = 0.0, power = 0.5f)
        val intervalZero = rawToInterval(rawZero)
        assertEquals(60.0, intervalZero.duration)
    }

    @Test
    fun testRawToIntervalPowerFallback() {
        // Power missing, but low/high present
        val rawLowHigh = RawInterval(powerLow = 0.6f, powerHigh = 0.8f)
        // Actual result has float precision noise
        assertEquals(0.70000005f, rawToInterval(rawLowHigh).power)

        // Only low
        val rawLow = RawInterval(powerLow = 0.6f)
        assertEquals(0.6f, rawToInterval(rawLow).power)

        // Only high
        val rawHigh = RawInterval(powerHigh = 0.8f)
        assertEquals(0.8f, rawToInterval(rawHigh).power)

        // Neither
        val rawNone = RawInterval()
        assertEquals(0.6f, rawToInterval(rawNone).power) // Default power is 0.6f
    }

    @Test
    fun testRawToIntervalCadenceFallback() {
        // Cadence missing, but low/high present
        val rawLowHigh = RawInterval(power = 0.7f, cadenceLow = 80.0, cadenceHigh = 90.0)
        assertEquals(85.0, rawToInterval(rawLowHigh).cadence)

        // Rounding: (80 + 85) / 2 = 82.5 -> round(82.5) = 82.0 (towards even)
        val rawRounding = RawInterval(power = 0.7f, cadenceLow = 80.0, cadenceHigh = 85.0)
        assertEquals(82.0, rawToInterval(rawRounding).cadence)

        // Only low
        val rawLow = RawInterval(power = 0.7f, cadenceLow = 80.0)
        assertEquals(80.0, rawToInterval(rawLow).cadence)

        // Only high
        val rawHigh = RawInterval(power = 0.7f, cadenceHigh = 90.0)
        assertEquals(90.0, rawToInterval(rawHigh).cadence)

        // Neither -> Estimate from power
        // power = 0.5 -> cadence = 70 + 30 * 0.5 = 85
        val rawNone = RawInterval(power = 0.5f)
        assertEquals(85.0, rawToInterval(rawNone).cadence)
    }

    @Test
    fun testRawToIntervalTypeDefault() {
        val raw = RawInterval(power = 0.5f)
        val interval = rawToInterval(raw)
        assertEquals("SteadyState", interval.type)
    }

    @Test
    fun testRawToWorkoutNormalization() {
        val raw = RawWorkout(
            id = 123,
            name = null,
            author = null,
            sportType = null,
            tags = null,
            description = null,
            intervals = listOf(RawInterval(duration = 100.0))
        )
        val workout = rawToWorkout(raw)
        assertEquals("Unnamed Workout", workout.name)
        assertEquals("Unknown Author", workout.author)
        assertTrue(workout.sportType)
        assertEquals(emptyList(), workout.tags)
        assertEquals("No description", workout.description)
        assertEquals(1, workout.intervals.size)
        assertEquals(100.0, workout.intervals[0].duration)
    }

    @Test
    fun testRawToWorkoutNullIntervals() {
        val raw = RawWorkout(id = 1, intervals = null)
        val workout = rawToWorkout(raw)
        assertEquals(emptyList(), workout.intervals)
    }
}
