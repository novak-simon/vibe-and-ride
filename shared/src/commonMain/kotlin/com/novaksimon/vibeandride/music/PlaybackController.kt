package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import kotlinx.coroutines.flow.Flow

interface PlaybackController {
    val isPlayingFlow: Flow<Boolean>
    val errorFlow: Flow<String>
    fun play()
    fun pause()
    fun isPlaying(): Boolean
    suspend fun setSource(
        song: Song,
        audioUrl: String,
        startTimeSeconds: Float = 0f
    )
    suspend fun preloadSource(
        song: Song,
        audioUrl: String,
    )
    suspend fun crossfadeToPreloaded(
        song: Song,
        audioUrl: String,
        durationMs: Long
    )
    suspend fun clearPreloaded()
    fun positionSeconds(): Float?
    fun durationSeconds(): Float?
}