package com.novaksimon.vibeandride.workout

import com.novaksimon.vibeandride.database.rawToInterval
import com.novaksimon.vibeandride.model.RawInterval
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutCrossLayerCharacterizationTest {

    private val parser = WorkoutParser()

    @Test
    fun testParserVsNormalizationConsistency() {
        val zwo = """
            <workout_file>
                <workout>
                    <SteadyState Duration="60" PowerLow="0.6" PowerHigh="0.8" CadenceLow="80" CadenceHigh="85"/>
                </workout>
            </workout_file>
        """.trimIndent()

        // 1. Parser now supports low/high
        val workout = parser.parseZwoString(zwo)
        val intervalFromParser = workout.intervals!![0]
        
        assertEquals(0.70000005f, intervalFromParser.power)
        assertEquals(82.0, intervalFromParser.cadence)

        // 2. Normalization logic also handles low/high
        val raw = RawInterval(
            duration = 60.0,
            powerLow = 0.6f,
            powerHigh = 0.8f,
            cadenceLow = 80.0,
            cadenceHigh = 85.0,
            type = "SteadyState"
        )
        val intervalFromNormalization = rawToInterval(raw)
        
        assertEquals(0.70000005f, intervalFromNormalization.power)
        assertEquals(82.0, intervalFromNormalization.cadence)
    }

    @Test
    fun testParserVsNormalizationDefaultInconsistency() {
        // Demonstrate that defaults are STILL inconsistent
        val zwo = """
            <workout_file>
                <workout>
                    <SteadyState Duration="60"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        val intervalFromParser = workout.intervals!![0]
        
        // Parser defaults to 0.0
        assertEquals(0.0f, intervalFromParser.power)
        assertEquals(0.0, intervalFromParser.cadence)

        // Normalization defaults to 0.6f and estimates cadence
        val raw = RawInterval(duration = 60.0, type = "SteadyState")
        val intervalFromNormalization = rawToInterval(raw)
        
        assertEquals(0.6f, intervalFromNormalization.power)
        // cadence = 70 + 30 * 0.6 = 88
        assertEquals(88.0, intervalFromNormalization.cadence)
    }
}
