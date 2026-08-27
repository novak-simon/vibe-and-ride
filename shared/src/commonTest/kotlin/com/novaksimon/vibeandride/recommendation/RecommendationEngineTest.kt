package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days


class RecommendationEngineTest {

    private val fakeSongRepository = FakeSongRepository()
    private val fakeRepository = FakeProfileRepository()
    private val engine = RecommendationEngine(fakeSongRepository, fakeRepository)

    private fun createSong(
        id: Int = 1,
        bpm: Int = 100,
        tags: List<String> = emptyList(),
        genre: List<String> = emptyList()
    ) = Song(
        id = id,
        title = "Test",
        artist = "Artist",
        releaseDate = "2024-01-01",
        genre = genre,
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
    fun testExactMatch() {
        val song = createSong(bpm = 100)
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        // cadenceDiff = 0
        // cadenceScore = round3(computeCs(0)) = round3(0.9423077) = 0.942
        assertEquals(0.942f, result.cadenceScore)
        // compatibleBonus = 0.15
        assertEquals(0.15f, result.cadenceCompatibleBonus)
        // songScore = 0
        assertEquals(0.0f, result.songScore)
        // totalScore = 0.9 * 0.9423077 + 0.1 * 0 + 0.15 = 0.9980769
        assertEquals(0.9980769f, result.score, 0.0001f)
    }

    @Test
    fun testBpmMatch05x() {
        val song = createSong(bpm = 200) // 200 * 0.5 = 100
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        assertEquals(0.942f, result.cadenceScore)
        assertEquals(0.15f, result.cadenceCompatibleBonus)
        assertEquals(0.9980769f, result.score, 0.0001f)
    }

    @Test
    fun testBpmMatch2x() {
        val song = createSong(bpm = 50) // 50 * 2 = 100
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        assertEquals(0.942f, result.cadenceScore)
        assertEquals(0.15f, result.cadenceCompatibleBonus)
        assertEquals(0.9980769f, result.score, 0.0001f)
    }

    @Test
    fun testNoCompatibleMatch() {
        val song = createSong(bpm = 110) // 110 vs 100, diff 10. tolerance 2.
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        // cadenceDiff = 10
        // computeCs(10) = 0.3 / (10 - 4) = 0.3 / 6 = 0.05
        assertEquals(0.05f, result.cadenceScore)
        assertEquals(0.0f, result.cadenceCompatibleBonus)
        // totalScore = 0.9 * 0.05 + 0.1 * 0 + 0 = 0.045
        assertEquals(0.045f, result.score, 0.0001f)
    }

    @Test
    fun testBpmZero() {
        val song = createSong(bpm = 0)
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        assertEquals(0.0f, result.cadenceScore)
        assertEquals(0.0f, result.cadenceCompatibleBonus)
        assertEquals(0.0f, result.score)
    }

    @Test
    fun testPositiveTags() {
        // energetic (0.5), motivational (0.5) -> sum 1.0
        val song = createSong(bpm = 100, tags = listOf("energetic", "motivational"))
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        assertEquals(1.0f, result.songScore)
        // totalScore = 0.9 * 0.942 + 0.1 * 1.0 + 0.15 = 0.8478 + 0.1 + 0.15 = 1.0978 -> clamped to 1.0
        assertEquals(1.0f, result.score)
    }

    @Test
    fun testNegativeTags() {
        // ambient (-1.0)
        val song = createSong(bpm = 100, tags = listOf("ambient"))
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        assertEquals(-1.0f, result.songScore)
        // totalScore = 0.9 * 0.9423077 + 0.1 * (-1.0) + 0.15 = 0.8480769 - 0.1 + 0.15 = 0.8980769
        assertEquals(0.8980769f, result.score, 0.0001f)
    }

    @Test
    fun testMixedTags() {
        // energetic (0.5), lounge (-0.1) -> 0.4
        val song = createSong(bpm = 100, tags = listOf("energetic", "lounge"))
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        assertEquals(0.4f, result.songScore)
    }

    @Test
    fun testNegativeGenre() {
        // genre ambient (-1.0)
        val song = createSong(bpm = 100, genre = listOf("ambient"))
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        assertEquals(-1.0f, result.songScore)
    }

    @Test
    fun testNoRecognizedTagsOrGenres() {
        val song = createSong(bpm = 100, tags = listOf("unknown"), genre = listOf("random"))
        val interval = createInterval(cadence = 100.0)
        val result = engine.getIntervalMatchScore(song, interval)

        assertEquals(0.0f, result.songScore)
    }

    @Test
    fun testProfilePreference_NullSong() = runTest {
        val profile = Profile("p1", listOf("pop"))
        val result = engine.getProfilePreferenceScore(null, profile)
        assertEquals(0f, result.score)
    }

    @Test
    fun testProfilePreference_NoMatch_NoStats() = runTest {
        val song = createSong(genre = listOf("rock"))
        val profile = Profile("p1", listOf("pop"))
        val result = engine.getProfilePreferenceScore(song, profile)
        // numberOfMatchingGenres = 0
        // score = clamp01((0 + 0.5f) / 2) = 0.25
        assertEquals(0.25f, result.score)
        assertEquals(0f, result.numberOfMatchingGenres)
        assertEquals(-1f, result.repetitionCoefficient)
    }

    @Test
    fun testProfilePreference_GenreMatch_NoStats() = runTest {
        val song = createSong(genre = listOf("pop", "rock"))
        val profile = Profile("p1", listOf("pop"))
        val result = engine.getProfilePreferenceScore(song, profile)
        // numberOfMatchingGenres = 1
        // score = clamp01((1 + 0.5f) / 2) = 0.75
        assertEquals(0.75f, result.score)
        assertEquals(1f, result.numberOfMatchingGenres)
    }

    @Test
    fun testProfilePreference_LikedSong() = runTest {
        val song = createSong(id = 10, genre = listOf("pop"))
        val profile = Profile("p1", listOf("pop"))
        fakeRepository.stats[10 to "p1"] = ProfileSongStats(
            songId = 10,
            profileId = "p1",
            isLiked = true,
            playCount = 1,
            skipCount = 0,
            firstPlayedAt = null,
            lastPlayedAt = null,
            lastSkippedAt = null,
            totalPlayTime = 0
        )
        val result = engine.getProfilePreferenceScore(song, profile)
        // lastPlayedAt == null -> score = 1 * 2 * 2 * 2 = 8.0
        assertEquals(2.0f, result.likeCoefficient)
        assertEquals(8.0f, result.score)
    }

    @Test
    fun testProfilePreference_UnlikedSong() = runTest {
        val song = createSong(id = 11, genre = listOf("pop"))
        val profile = Profile("p1", listOf("pop"))
        fakeRepository.stats[11 to "p1"] = ProfileSongStats(
            songId = 11,
            profileId = "p1",
            isLiked = false,
            playCount = 1,
            skipCount = 0,
            firstPlayedAt = null,
            lastPlayedAt = null,
            lastSkippedAt = null,
            totalPlayTime = 0
        )
        val result = engine.getProfilePreferenceScore(song, profile)
        // lastPlayedAt == null -> score = 1 * 2 * 1 * 2 = 4.0
        assertEquals(1.0f, result.likeCoefficient)
        assertEquals(4.0f, result.score)
    }

    @Test
    fun testProfilePreference_StatZeroSkips() = runTest {
        val song = createSong(id = 12, genre = listOf("pop"))
        val profile = Profile("p1", listOf("pop"))
        fakeRepository.stats[12 to "p1"] = ProfileSongStats(
            songId = 12,
            profileId = "p1",
            isLiked = false,
            playCount = 10,
            skipCount = 0,
            firstPlayedAt = null,
            lastPlayedAt = null,
            lastSkippedAt = null,
            totalPlayTime = 0
        )
        val result = engine.getProfilePreferenceScore(song, profile)
        assertEquals(2.0f, result.skipCoefficient)
    }

    @Test
    fun testProfilePreference_StatWithSkips() = runTest {
        val song = createSong(id = 13, genre = listOf("pop"))
        val profile = Profile("p1", listOf("pop"))
        fakeRepository.stats[13 to "p1"] = ProfileSongStats(
            songId = 13,
            profileId = "p1",
            isLiked = false,
            playCount = 3,
            skipCount = 1,
            firstPlayedAt = null,
            lastPlayedAt = null,
            lastSkippedAt = null,
            totalPlayTime = 0
        )
        val result = engine.getProfilePreferenceScore(song, profile)
        // skipCoefficient = minOf(2.0, 3 / 3 * 1) = 1.0
        assertEquals(1.0f, result.skipCoefficient)
        // score = 1 * 2 * 1 * 1 = 2.0
        assertEquals(2.0f, result.score)
    }

    @Test
    fun testProfilePreference_NullLastPlayed() = runTest {
        val song = createSong(id = 14, genre = listOf("pop"))
        val profile = Profile("p1", listOf("pop"))
        fakeRepository.stats[14 to "p1"] = ProfileSongStats(
            songId = 14,
            profileId = "p1",
            isLiked = false,
            playCount = 1,
            skipCount = 0,
            firstPlayedAt = Clock.System.now(),
            lastPlayedAt = null,
            lastSkippedAt = null,
            totalPlayTime = 0
        )
        val result = engine.getProfilePreferenceScore(song, profile)
        assertEquals(2.0f, result.repetitionCoefficient)
        assertEquals(4.0f, result.score)
    }

    @Test
    fun testProfilePreference_RecentlyPlayed() = runTest {
        val song = createSong(id = 15, genre = listOf("pop"))
        val profile = Profile("p1", listOf("pop"))
        val now = Clock.System.now()
        fakeRepository.stats[15 to "p1"] = ProfileSongStats(
            songId = 15,
            profileId = "p1",
            isLiked = false,
            playCount = 1,
            skipCount = 0,
            firstPlayedAt = now - 30.minutes,
            lastPlayedAt = now - 30.minutes,
            lastSkippedAt = null,
            totalPlayTime = 0
        )
        val result = engine.getProfilePreferenceScore(song, profile)
        assertEquals(-1f, result.score)
        assertEquals(-1f, result.repetitionCoefficient)
    }

    @Test
    fun testProfilePreference_PreviouslyPlayed_LowRepetition() = runTest {
        val song = createSong(id = 16, genre = listOf("pop"))
        val profile = Profile("p1", listOf("pop"))
        val now = Clock.System.now()
        fakeRepository.stats[16 to "p1"] = ProfileSongStats(
            songId = 16,
            profileId = "p1",
            isLiked = false,
            playCount = 1,
            skipCount = 0,
            firstPlayedAt = now - 12.hours,
            lastPlayedAt = now - 12.hours,
            lastSkippedAt = null,
            totalPlayTime = 0
        )
        val result = engine.getProfilePreferenceScore(song, profile)
        // sinceLastPlayed = 12h. 12h / 48h = 0.25
        assertEquals(0.25f, result.repetitionCoefficient)
        // score = clamp01((1 + 0.5f) * 0.25 * 1 * 2 / 16) = clamp01(1.5 * 0.5 / 16) = 0.75 / 16 = 0.046875
        assertEquals(0.046875f, result.score, 0.0001f)
    }

    @Test
    fun testProfilePreference_PreviouslyPlayed_MaxRepetition() = runTest {
        val song = createSong(id = 17, genre = listOf("pop"))
        val profile = Profile("p1", listOf("pop"))
        val now = Clock.System.now()
        fakeRepository.stats[17 to "p1"] = ProfileSongStats(
            songId = 17,
            profileId = "p1",
            isLiked = false,
            playCount = 1,
            skipCount = 0,
            firstPlayedAt = now - 5.days,
            lastPlayedAt = now - 5.days,
            lastSkippedAt = null,
            totalPlayTime = 0
        )
        val result = engine.getProfilePreferenceScore(song, profile)
        // 5 days / 2 days = 2.5 -> minOf(2.0, 2.5) = 2.0
        assertEquals(2.0f, result.repetitionCoefficient)
        // score = (1 + 0.5) * 2.0 * 1 * 2 / 16 = 6.0 / 16 = 0.375
        assertEquals(0.375f, result.score)
    }
}
