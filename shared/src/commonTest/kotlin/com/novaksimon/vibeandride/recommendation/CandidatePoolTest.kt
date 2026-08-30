package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.SongV2
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CandidatePoolTest {

    private val fakeSongRepository = FakeSongRepository()
    private val fakeProfileRepository = FakeProfileRepository()
    private val engine = RecommendationEngine(fakeSongRepository, fakeProfileRepository)

    private fun createSong(id: Int) = SongV2(
        id = id,
        title = "Song $id",
        releaseDate = "2024",
        genre = "pop",
        tags = emptyList(),
        duration = 180,
        bpm = 100,
        startTime = 0f,
        endTime = 0f,
        image = "",
        shareUrl = "",
        provider = 1,
        description = "Description",
        audiusProviderId = "",
        jamendoProviderId = 0,
        mood = "mood",
        artistId = 0
    )

    private fun createInterval(cadence: Double) = Interval(
        duration = 60.0,
        power = 0.5f,
        cadence = cadence,
        type = "steady"
    )

    @Test
    fun testFirstQueryReturns100() = runTest {
        val songs = List(100) { createSong(it) }
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs)

        val interval = createInterval(100.0)
        val result = engine.approximateFilterByInterval(interval)

        assertEquals(100, result.size)
        assertEquals(1, fakeSongRepository.calls.size)
        assertEquals("getSongsAroundTargetCadence", fakeSongRepository.calls[0])
        val args = fakeSongRepository.arguments[0] as List<*>
        assertEquals(2, args[1]) // tolerance
        assertEquals(100, args[2]) // limit
    }

    @Test
    fun testSecondQueryFillsPool() = runTest {
        val songs1 = List(60) { createSong(it) }
        val songs2 = List(40) { createSong(it + 60) }
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs1)
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs2)

        val interval = createInterval(100.0)
        val result = engine.approximateFilterByInterval(interval)

        assertEquals(100, result.size)
        assertEquals(2, fakeSongRepository.calls.size)
        
        val args1 = fakeSongRepository.arguments[0] as List<*>
        assertEquals(2, args1[1])
        assertEquals(100, args1[2])

        val args2 = fakeSongRepository.arguments[1] as List<*>
        assertEquals(5, args2[1])
        assertEquals(40, args2[2]) // 100 - 60
    }

    @Test
    fun testThirdQueryIsUsed() = runTest {
        val songs1 = List(30) { createSong(it) }
        val songs2 = List(30) { createSong(it + 30) }
        val songs3 = List(40) { createSong(it + 60) }
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs1)
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs2)
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs3)

        val interval = createInterval(100.0)
        val result = engine.approximateFilterByInterval(interval)

        assertEquals(100, result.size)
        assertEquals(3, fakeSongRepository.calls.size)

        val args3 = fakeSongRepository.arguments[2] as List<*>
        assertEquals(100, args3[2]) // limit for 3rd query is 100
        assertEquals(10, args3[1]) // tolerance
    }

    @Test
    fun testGenericFallback() = runTest {
        fakeSongRepository.songsAroundTargetCadenceResults.add(emptyList()) // 1st
        fakeSongRepository.songsAroundTargetCadenceResults.add(emptyList()) // 2nd
        fakeSongRepository.songsAroundTargetCadenceResults.add(emptyList()) // 3rd
        
        val genericSongs = List(50) { createSong(it) }
        fakeSongRepository.fetchSongsResults.add(genericSongs)

        val interval = createInterval(100.0)
        val result = engine.approximateFilterByInterval(interval)

        assertEquals(50, result.size)
        assertEquals(4, fakeSongRepository.calls.size)
        assertEquals("fetchSongs", fakeSongRepository.calls[3])
        assertEquals(50, fakeSongRepository.arguments[3])
    }
    
    @Test
    fun testTolerancesAndLimits() = runTest {
        fakeSongRepository.songsAroundTargetCadenceResults.add(List(10) { createSong(it) })
        fakeSongRepository.songsAroundTargetCadenceResults.add(List(10) { createSong(it + 10) })
        fakeSongRepository.songsAroundTargetCadenceResults.add(List(10) { createSong(it + 20) })

        val interval = createInterval(100.0)
        engine.approximateFilterByInterval(interval)

        assertEquals(3, fakeSongRepository.calls.size)
        
        val args1 = fakeSongRepository.arguments[0] as List<*>
        assertEquals(2, args1[1])
        assertEquals(100, args1[2])

        val args2 = fakeSongRepository.arguments[1] as List<*>
        assertEquals(5, args2[1])
        assertEquals(90, args2[2]) // 100 - 10

        val args3 = fakeSongRepository.arguments[2] as List<*>
        assertEquals(10, args3[1])
        assertEquals(100, args3[2])
    }
}
