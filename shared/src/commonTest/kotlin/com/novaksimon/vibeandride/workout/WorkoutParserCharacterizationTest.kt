package com.novaksimon.vibeandride.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class WorkoutParserCharacterizationTest {

    private val parser = WorkoutParser()

    @Test
    fun testBasicParsing() {
        val zwo = """
            <workout_file>
                <author>Simon Novak</author>
                <name>Test Workout</name>
                <description>A test description</description>
                <sportType>bike</sportType>
                <tags>
                    <tag>Intervals</tag>
                    <tag>Test</tag>
                </tags>
                <workout>
                    <SteadyState Duration="600" Power="0.75" Cadence="90"/>
                    <Ramp Duration="300" Power="0.5" Cadence="80"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)

        assertEquals("Test Workout", workout.name)
        assertEquals("Simon Novak", workout.author)
        assertEquals("A test description", workout.description)
        assertTrue(workout.sportType) // sportType is bike
        assertEquals(listOf("Intervals", "Test"), workout.tags)
        assertEquals(2, workout.intervals?.size)

        val interval1 = workout.intervals!![0]
        assertEquals(600.0, interval1.duration)
        assertEquals(0.75f, interval1.power)
        assertEquals(90.0, interval1.cadence)
        assertEquals("SteadyState", interval1.type)

        val interval2 = workout.intervals!![1]
        assertEquals(300.0, interval2.duration)
        assertEquals(0.5f, interval2.power)
        assertEquals(80.0, interval2.cadence)
        assertEquals("Ramp", interval2.type)
    }

    @Test
    fun testWorkoutNameHandling() {
        val zwo = """
            <workout_file>
                <name>Zwift Workout</name>
                <workout></workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo, "CustomFileName")
        assertEquals("CustomFileName", workout.name)

        val zwoNormal = """
            <workout_file>
                <name>My Cool Workout</name>
                <workout></workout>
            </workout_file>
        """.trimIndent()
        val workoutNormal = parser.parseZwoString(zwoNormal, "IgnoreMe")
        assertEquals("My Cool Workout", workoutNormal.name)
    }

    @Test
    fun testTagsHandling() {
        val zwo = """
            <workout_file>
                <tags>
                    <tag>Tag 1</tag>
                    <tag></tag>
                    <tag>  </tag>
                    <tag>Tag 2</tag>
                </tags>
                <workout></workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        // Current implementation: if (t.isNotBlank()) tags.add(t)
        assertEquals(listOf("Tag 1", "Tag 2"), workout.tags)
    }

    @Test
    fun testMissingMetadata() {
        val zwo = """
            <workout_file>
                <workout></workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        assertNull(workout.name)
        assertNull(workout.author)
        assertNull(workout.description)
        assertFalse(workout.sportType) // Default sportType is bike (true) in model, but parser sets false if sportType is null or not "bike"
        assertEquals(emptyList(), workout.tags)
    }

    @Test
    fun testMultipleIntervalTypes() {
        val zwo = """
            <workout_file>
                <workout>
                    <SteadyState Duration="60"/>
                    <IntervalsT Repeat="1" OnDuration="60" OffDuration="60"/>
                    <Warmup Duration="180"/>
                    <Cooldown Duration="240"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        assertEquals(5, workout.intervals?.size)
        assertEquals("SteadyState", workout.intervals!![0].type)
        assertEquals("On", workout.intervals!![1].type)
        assertEquals("Off", workout.intervals!![2].type)
        assertEquals("Warmup", workout.intervals!![3].type)
        assertEquals("Cooldown", workout.intervals!![4].type)
    }

    @Test
    fun testFlatPowerCadenceAttributes() {
        val zwo = """
            <workout_file>
                <workout>
                    <SteadyState Duration="10" Power="0.8" Cadence="85"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        val interval = workout.intervals!![0]
        assertEquals(0.8f, interval.power)
        assertEquals(85.0, interval.cadence)
    }

    @Test
    fun testLowHighAttributes() {
        // Documenting fix: these are now SUPPORTED
        val zwo = """
            <workout_file>
                <workout>
                    <SteadyState Duration="60" PowerLow="0.6" PowerHigh="0.8" CadenceLow="80" CadenceHigh="85"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        val interval = workout.intervals!![0]
        
        // Now fixed: (0.6 + 0.8) / 2 = 0.7
        assertEquals(0.7f, interval.power, 0.0001f)
        // (80 + 85) / 2 = 82.5 -> round(82.5) = 82.0
        assertEquals(82.0, interval.cadence)
    }

    @Test
    fun testMissingNumericAttributes() {
        val zwo = """
            <workout_file>
                <workout>
                    <SteadyState/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        val interval = workout.intervals!![0]
        assertEquals(0.0, interval.duration)
        assertEquals(0.0f, interval.power)
        assertEquals(0.0, interval.cadence)
    }

    @Test
    fun testInvalidNumericValues() {
        val zwo = """
            <workout_file>
                <workout>
                    <SteadyState Duration="abc" Power="invalid" Cadence="none"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        val interval = workout.intervals!![0]
        // toDoubleOrNull() returns null, which ?: 0.0 handles
        assertEquals(0.0, interval.duration)
        assertEquals(0.0f, interval.power)
        assertEquals(0.0, interval.cadence)
    }

    @Test
    fun testEmptyWorkout() {
        val zwo = """
            <workout_file>
                <workout></workout>
            </workout_file>
        """.trimIndent()

        val workout = parser.parseZwoString(zwo)
        assertEquals(0, workout.intervals?.size)
    }

    @Test
    fun testParseRepeats() {
        val zwo = """
            <workout_file>
                <workout>
                    <IntervalsT Repeat="2" OnDuration="10" OffDuration="20" OnPower="1.0" OffPower="0.5" OnCadence="100" OffCadence="80"/>
                </workout>
            </workout_file>
        """.trimIndent()
        
        val workout = parser.parseZwoString(zwo)
        val intervals = workout.intervals!!
        
        assertEquals(4, intervals.size)
        
        // Pair 1
        assertEquals(10.0, intervals[0].duration)
        assertEquals(1.0f, intervals[0].power)
        assertEquals(100.0, intervals[0].cadence)
        assertEquals("On", intervals[0].type)
        
        assertEquals(20.0, intervals[1].duration)
        assertEquals(0.5f, intervals[1].power)
        assertEquals(80.0, intervals[1].cadence)
        assertEquals("Off", intervals[1].type)
        
        // Pair 2
        assertEquals(10.0, intervals[2].duration)
        assertEquals(20.0, intervals[3].duration)
    }

    @Test
    fun testParseRepeatsWithRanges() {
        val zwo = """
            <workout_file>
                <workout>
                    <IntervalsT Repeat="1" OnDuration="60" OffDuration="60" OnPowerLow="0.8" OnPowerHigh="1.0" OffPower="0.5" OnCadenceLow="90" OnCadenceHigh="110"/>
                </workout>
            </workout_file>
        """.trimIndent()
        
        val workout = parser.parseZwoString(zwo)
        val intervals = workout.intervals!!
        
        assertEquals(0.9f, intervals[0].power)
        assertEquals(100.0, intervals[0].cadence)
    }

    @Test
    fun testParseFullAspireWorkout() {
        val zwo = """
            <workout_file>
                <workout>
                    <Warmup Duration="300" PowerLow="0.25" PowerHigh="0.65"/>
                    <SteadyState Duration="300" Power="0.65" Cadence="95"/>
                    <SteadyState Duration="60" Power="0.80" Cadence="100"/>
                    <SteadyState Duration="60" Power="0.75" Cadence="100"/>
                    <SteadyState Duration="60" Power="0.55" Cadence="85"/>
                    <SteadyState Duration="60" Power="0.90" Cadence="105"/>
                    <SteadyState Duration="60" Power="0.85" Cadence="105"/>
                    <SteadyState Duration="60" Power="0.55"/>
                    <SteadyState Duration="60" Power="1.00" Cadence="110"/>
                    <SteadyState Duration="60" Power="0.95" Cadence="110"/>
                    <SteadyState Duration="60" Power="0.55" Cadence="85"/>
                    <SteadyState Duration="30" Power="1.08" Cadence="110"/>
                    <SteadyState Duration="60" Power="0.55" Cadence="85"/>
                    <SteadyState Duration="30" Power="1.15" Cadence="115"/>
                    <SteadyState Duration="60" Power="0.55" Cadence="85"/>
                    <SteadyState Duration="180" Power="0.55" Cadence="85"/>
                    <IntervalsT Repeat="5" OnDuration="120" OnPower="1.05" OffDuration="120" OffPower="0.88" OnCadence="100" OffCadence="85"/>
                    <SteadyState Duration="300" Power="0.55" Cadence="85"/>
                    <IntervalsT Repeat="5" OnDuration="90" OnPower="1.05" OffDuration="150" OffPower="0.88" OnCadence="100" OffCadence="85"/>
                    <SteadyState Duration="300" Power="0.55" Cadence="85"/>
                    <IntervalsT Repeat="5" OnDuration="60" OnPower="1.05" OffDuration="180" OffPower="0.88" OnCadence="70" OffCadence="90"/>
                    <SteadyState Duration="300" Power="0.55" Cadence="85"/>
                    <IntervalsT Repeat="7" OnDuration="60" OnPower="1.15" OffDuration="60" OffPower="0.55" OnCadence="100" OffCadence="85"/>
                    <Cooldown Duration="360" PowerLow="0.55" PowerHigh="0.25"/>
                </workout>
            </workout_file>
        """.trimIndent()
        
        val workout = parser.parseZwoString(zwo)
        val intervals = workout.intervals!!
        
        val totalDuration = intervals.sumOf { it.duration }
        assertEquals(7200.0, totalDuration)
    }
}
