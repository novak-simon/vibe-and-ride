package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.SongV2
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RecommendFlowTest {

    private val fakeSongRepository = FakeSongRepository()
    private val fakeProfileRepository = FakeProfileRepository()
    private val engine = RecommendationEngine(fakeSongRepository, fakeProfileRepository)

    private fun createSong(
        id: Int, 
        bpm: Int = 100,
        tags: List<String> = emptyList(),
        genre: List<String> = emptyList()
    ) = SongV2(
        id = id,
        title = "Song $id",
        artistId = 1,
        releaseDate = "2024",
        description = "",
        genre = if (genre.isNotEmpty()) genre[0] else "pop",
        mood = "",
        tags = tags,
        duration = 180,
        bpm = bpm,
        startTime = 0f,
        endTime = 0f,
        image = "",
        shareUrl = ""
    )

    private fun createInterval(cadence: Double = 100.0) = Interval(
        duration = 60.0,
        power = 0.5f,
        cadence = cadence,
        type = "steady"
    )

    @Test
    fun testEmptyCandidatePool() = runTest {
        fakeSongRepository.songsAroundTargetCadenceResults.add(emptyList())
        fakeSongRepository.songsAroundTargetCadenceResults.add(emptyList())
        fakeSongRepository.songsAroundTargetCadenceResults.add(emptyList())
        fakeSongRepository.fetchSongsResults.add(emptyList())

        val result = engine.recommend(createInterval())

        assertTrue(result.topSongs.isEmpty())
        assertTrue(result.debugInfo.isEmpty())
        
        assertFalse(fakeProfileRepository.calls.contains("getProfileForRecommendation"), "Profile should not be fetched if pool is empty")
    }

    @Test
    fun testFewerThan10Candidates() = runTest {
        val songs = List(5) { createSong(it) }
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs)

        val result = engine.recommend(createInterval())

        assertEquals(5, result.topSongs.size)
        assertEquals(5, result.debugInfo.size)
        val ids = result.topSongs.map { it.id }.toSet()
        assertEquals(setOf(0, 1, 2, 3, 4), ids)
    }

    @Test
    fun testMoreThan10Candidates() = runTest {
        val songs = List(15) { createSong(it) }
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs)

        val result = engine.recommend(createInterval())

        assertEquals(10, result.topSongs.size)
        assertEquals(15, result.debugInfo.size)
        assertEquals(result.debugInfo.take(10).map { it.song?.id }, result.topSongs.map { it.id })
    }

    @Test
    fun testScoreOrdering() = runTest {
        val song1 = createSong(id = 1, bpm = 100, genre = listOf("pop"))
        val song2 = createSong(id = 2, bpm = 120, genre = listOf("rock"))
        fakeSongRepository.songsAroundTargetCadenceResults.add(listOf(song1, song2))
        
        fakeProfileRepository.profile = Profile("p1", listOf("pop"))

        val result = engine.recommend(createInterval(100.0))

        assertEquals(1, result.topSongs[0].id)
        assertEquals(2, result.topSongs[1].id)
        assertTrue(result.debugInfo[0].score!! > result.debugInfo[1].score!!)
    }

    @Test
    fun testTieBreakPreferenceScore() = runTest {
        val song1 = createSong(id = 1, bpm = 100, genre = listOf("pop"))
        val song2 = createSong(id = 2, bpm = 100, genre = listOf("rock"))
        fakeSongRepository.songsAroundTargetCadenceResults.add(listOf(song1, song2))
        
        fakeProfileRepository.profile = Profile("p1", listOf("pop"))

        val result = engine.recommend(createInterval(100.0))

        assertEquals(1, result.topSongs[0].id)
        assertEquals(2, result.topSongs[1].id)
    }

    @Test
    fun testTieBreakCadenceScore() = runTest {
        // Song 1: BPM 106 (cs 0.15), tags [groovy, beachmusic, handclaps] (ss 0.6)
        // IntervalScore 1 = 0.9 * 0.15 + 0.1 * 0.6 = 0.135 + 0.06 = 0.195
        val song1 = createSong(id = 1, bpm = 106, tags = listOf("groovy", "beachmusic", "handclaps"))
        
        // Song 2: BPM 107 (cs 0.1), tags [motivational, upbeat, beachmusic, popular] (ss 1.05)
        // IntervalScore 2 = 0.9 * 0.1 + 0.1 * 1.05 = 0.09 + 0.105 = 0.195
        val song2 = createSong(id = 2, bpm = 107, tags = listOf("motivational", "upbeat", "beachmusic", "popular"))
        
        fakeSongRepository.songsAroundTargetCadenceResults.add(listOf(song1, song2))
        
        val result = engine.recommend(createInterval(100.0))
        
        // Both have intervalMatch.score = 0.195. Total score same.
        // Song 1 has cadenceScore 0.15, Song 2 has 0.1.
        assertEquals(1, result.topSongs[0].id)
        assertEquals(2, result.topSongs[1].id)
    }

    @Test
    fun testFinalSongIdTieBreaker() = runTest {
        val song1 = createSong(id = 10, bpm = 100)
        val song2 = createSong(id = 5, bpm = 100)
        fakeSongRepository.songsAroundTargetCadenceResults.add(listOf(song1, song2))
        
        val result = engine.recommend(createInterval(100.0))
        
        assertEquals(5, result.topSongs[0].id)
        assertEquals(10, result.topSongs[1].id)
    }

    @Test
    fun testProfileRepositoryInteraction() = runTest {
        fakeSongRepository.songsAroundTargetCadenceResults.add(listOf(createSong(1)))
        
        engine.recommend(createInterval())
        
        assertTrue(fakeProfileRepository.calls.contains("getProfileForRecommendation"))
    }

    @Test
    fun testDeterminism() = runTest {
        val songs = List(20) { createSong(it, bpm = 100 + it) }
        
        fakeSongRepository.songsAroundTargetCadenceResults.add(songs)
        val firstResult = engine.recommend(createInterval(110.0))
        
        repeat(5) {
            fakeSongRepository.songsAroundTargetCadenceResults.add(songs)
            val result = engine.recommend(createInterval(110.0))
            assertEquals(firstResult.topSongs.map { it.id }, result.topSongs.map { it.id })
        }
    }
}
