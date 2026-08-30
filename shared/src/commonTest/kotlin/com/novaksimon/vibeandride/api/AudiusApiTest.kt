package com.novaksimon.vibeandride.api

import com.novaksimon.vibeandride.database.rawToInterval
import com.novaksimon.vibeandride.database.rawToWorkout
import com.novaksimon.vibeandride.model.RawInterval
import com.novaksimon.vibeandride.model.RawWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudiusApiTest {

    @Test
    fun scratch1() = runTest {
        val result = getAudiusStreamUrlV2("1590186863")
        println(result)
    }
    @Test
    fun scratch2() = runTest {
        debugGetTrackMetadata("JEY4V7d")
    }
}
