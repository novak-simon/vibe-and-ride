package com.novaksimon.vibeandride.recommendation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RecommendationHelperTest {

    @Test
    fun testClamp01() {
        assertEquals(0.0f, clamp01(-1.0f))
        assertEquals(0.0f, clamp01(0.0f))
        assertEquals(0.5f, clamp01(0.5f))
        assertEquals(1.0f, clamp01(1.0f))
        assertEquals(1.0f, clamp01(1.5f))
        assertEquals(2.0f, clamp01(3.0f, 2.0f))
    }

    @Test
    fun testClamp02() {
        assertEquals(-1.0f, clamp02(-1.5f))
        assertEquals(-1.0f, clamp02(-1.0f))
        assertEquals(0.0f, clamp02(0.0f))
        assertEquals(1.0f, clamp02(1.0f))
        assertEquals(1.0f, clamp02(1.5f))
        assertEquals(-2.0f, clamp02(-3.0f, 2.0f))
        assertEquals(2.0f, clamp02(3.0f, 2.0f))
    }

    @Test
    fun testGetAlgorithmCadence() {
        // Already in range
        assertEquals(80, getAlgorithmCadence(80))
        assertEquals(60, getAlgorithmCadence(60))
        assertEquals(120, getAlgorithmCadence(120))

        // Below range (doubling)
        assertEquals(100, getAlgorithmCadence(50)) // 50 * 2 = 100
        assertEquals(80, getAlgorithmCadence(20))  // 20 -> 40 -> 80
        assertEquals(60, getAlgorithmCadence(15))  // 15 -> 30 -> 60 (in range)

        // Above range (halving)
        assertEquals(80, getAlgorithmCadence(160)) // 160 -> 80
        assertEquals(90, getAlgorithmCadence(180)) // 180 -> 90
        assertEquals(62, getAlgorithmCadence(250)) // 250 -> 125 -> 62

        // Boundary/Low values
        assertEquals(120, getAlgorithmCadence(0)) // 0 * 2 = 0 repeatedly until 20 iterations, then returns 120
        assertEquals(80, getAlgorithmCadence(40)) // 40 -> 80

        // High input
        assertEquals(62, getAlgorithmCadence(2000)) // 2000 -> 1000 -> 500 -> 250 -> 125 -> 62
    }

    @Test
    fun testCalculateBpmMatch() {
        // Exact 1x match
        val match1x = calculateBpmMatch(100f, 100.0, 2.0f)
        assertTrue(match1x.compatible!!)
        assertEquals(100f, match1x.effectiveBpm)
        assertEquals(0f, match1x.difference)
        assertEquals(1.0f, match1x.multiplier)

        // Exact 0.5x match
        val match05x = calculateBpmMatch(200f, 100.0, 2.0f)
        assertTrue(match05x.compatible!!)
        assertEquals(100f, match05x.effectiveBpm)
        assertEquals(0f, match05x.difference)
        assertEquals(0.5f, match05x.multiplier)

        // Exact 2x match
        val match2x = calculateBpmMatch(50f, 100.0, 2.0f)
        assertTrue(match2x.compatible!!)
        assertEquals(100f, match2x.effectiveBpm)
        assertEquals(0f, match2x.difference)
        assertEquals(2.0f, match2x.multiplier)

        // Best multiplier is 0.5 (180 BPM song, 100 target -> 90 vs 180 vs 360)
        val best05 = calculateBpmMatch(180f, 100.0, 20.0f)
        assertEquals(0.5f, best05.multiplier)
        assertEquals(90f, best05.effectiveBpm)

        // Best multiplier is 2 (60 BPM song, 110 target -> 30 vs 60 vs 120)
        val best2 = calculateBpmMatch(60f, 110.0, 20.0f)
        assertEquals(2.0f, best2.multiplier)
        assertEquals(120f, best2.effectiveBpm)

        // Compatible within tolerance (101 vs 100, tolerance 2)
        val withinTol = calculateBpmMatch(101f, 100.0, 2.0f)
        assertTrue(withinTol.compatible!!)
        assertEquals(1.0f, withinTol.difference)

        // Outside tolerance (103 vs 100, tolerance 2)
        val outsideTol = calculateBpmMatch(103f, 100.0, 2.0f)
        assertFalse(outsideTol.compatible!!)
        assertEquals(3.0f, outsideTol.difference)

        // Null song BPM
        val nullBpm = calculateBpmMatch(null, 100.0, 2.0f)
        assertFalse(nullBpm.compatible!!)
        assertNull(nullBpm.effectiveBpm)

        // Null target cadence
        val nullCadence = calculateBpmMatch(100f, null, 2.0f)
        assertFalse(nullCadence.compatible!!)

        // BPM where multiple multipliers are close (e.g. 150 BPM song, 100 target)
        // 0.5x = 75 (diff 25)
        // 1.0x = 150 (diff 50)
        // 2.0x = 300 (diff 200)
        val multiple = calculateBpmMatch(150f, 100.0, 30.0f)
        assertEquals(0.5f, multiple.multiplier)
        assertEquals(75f, multiple.effectiveBpm)
    }
}