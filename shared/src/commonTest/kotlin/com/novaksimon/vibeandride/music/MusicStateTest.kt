package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.RecommendationResult
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicStateTest {

    @Test
    fun testDefaultInitialState() {
        val state = MusicState()

        assertNull(state.workout)
        assertTrue(state.recommendationBuffer.isEmpty())
        assertTrue(state.recommendationIndex.isEmpty())
        assertEquals(0, state.songIndex)
        assertNull(state.currentSong)
        assertNull(state.futureIntervalSong)
        assertEquals(-1, state.currentIntervalIndex)
        assertNull(state.preloadedSongId)
        assertFalse(state.hasPreloadedSong)
        assertFalse(state.isTransitioning)
        assertTrue(state.currentDebug.isEmpty())
        assertTrue(state.futureDebug.isEmpty())
    }

    @Test
    fun testStateUpdateViaCopy() {
        val song = createSong(1, "Test Song")
        val workout = Workout(id = 1, name = "Test Workout", author = "Author")
        val recResult = RecommendationResult(topSongs = listOf(song), debugInfo = emptyList())

        val initialState = MusicState()
        val updatedState = initialState.copy(
            workout = workout,
            recommendationBuffer = listOf(recResult),
            recommendationIndex = listOf(true),
            currentIntervalIndex = 0,
            songIndex = 1,
            currentSong = song,
            isTransitioning = true
        )

        assertEquals(workout, updatedState.workout)
        assertEquals(1, updatedState.recommendationBuffer.size)
        assertEquals(recResult, updatedState.recommendationBuffer[0])
        assertEquals(listOf(true), updatedState.recommendationIndex)
        assertEquals(0, updatedState.currentIntervalIndex)
        assertEquals(1, updatedState.songIndex)
        assertEquals(song, updatedState.currentSong)
        assertTrue(updatedState.isTransitioning)

        // Verify original state is unchanged
        assertNull(initialState.workout)
        assertFalse(initialState.isTransitioning)
    }

    @Test
    fun testReadonlyCollections() {
        val mutableRecs = mutableListOf<RecommendationResult?>(null)
        val state = MusicState(recommendationBuffer = mutableRecs)

        // The property is declared as List, so it should be treated as read-only by callers
        assertEquals(1, state.recommendationBuffer.size)

        // Modifying the original list (if it was passed by reference) is not prevented by the type,
        // but the data class copy should ensure we can update it immutably.
        val newState = state.copy(recommendationBuffer = state.recommendationBuffer + null)
        assertEquals(2, newState.recommendationBuffer.size)
        assertEquals(1, state.recommendationBuffer.size)
    }

    private fun createSong(id: Int, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        releaseDate = "2024",
        genre = emptyList(),
        tags = emptyList(),
        duration = 180,
        bpm = 120,
        startTime = 0f,
        endTime = 0f,
        image = "",
        shareUrl = ""
    )
}
