package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.RecommendationV2State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class MusicStateTest {

    @Test
    fun testDefaultInitialState() {
        val state = MusicState()

        assertNull(state.workout)
        assertEquals(0, state.songIndex)
        assertNull(state.currentSong)
        assertEquals(-1, state.currentIntervalIndex)
        assertNull(state.preloadedSongId)
        assertEquals(PlaybackState.IDLE, state.playbackState)
        assertNotNull(state.recommendationV2)
    }

    @Test
    fun testStateUpdateViaCopy() {
        val song = createSong(1, "Test Song")
        val workout = Workout(id = 1, name = "Test Workout", author = "Author")
        val v2State = RecommendationV2State(currentIntervalIndex = 5)

        val initialState = MusicState()
        val updatedState = initialState.copy(
            workout = workout,
            recommendationV2 = v2State,
            currentIntervalIndex = 0,
            songIndex = 1,
            currentSong = song,
            playbackState = PlaybackState.TRANSITIONING
        )

        assertEquals(workout, updatedState.workout)
        assertEquals(5, updatedState.recommendationV2.currentIntervalIndex)
        assertEquals(0, updatedState.currentIntervalIndex)
        assertEquals(1, updatedState.songIndex)
        assertEquals(song, updatedState.currentSong)
        assertEquals(PlaybackState.TRANSITIONING, updatedState.playbackState)

        // Verify original state is unchanged
        assertNull(initialState.workout)
        assertEquals(PlaybackState.IDLE, initialState.playbackState)
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
