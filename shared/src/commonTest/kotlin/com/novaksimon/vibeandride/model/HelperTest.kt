package com.novaksimon.vibeandride.model

import kotlin.test.Test
import kotlin.test.assertEquals

class HelperTest {

    @Test
    fun testEstimateCadenceFromPower() {
        // Test default case (null)
        assertEquals(80, estimateCadenceFromPower(null))

        // Test low power
        // cadence = 70 + 30 * 0.1 = 73 -> coerced to 75
        assertEquals(75, estimateCadenceFromPower(0.1f))

        // Test mid power
        // cadence = 70 + 30 * 0.5 = 85
        assertEquals(85, estimateCadenceFromPower(0.5f))

        // Test high power
        // cadence = 70 + 30 * 2.0 = 130 -> coerced to 115
        assertEquals(115, estimateCadenceFromPower(2.0f))
    }
}