package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.SongV2
import kotlinx.coroutines.flow.Flow

interface PlaybackController {
    val isPlayingFlow: Flow<Boolean>
    val errorFlow: Flow<String>
    fun play()
    fun pause()
    fun isPlaying(): Boolean
    suspend fun setSource(
        song: SongV2,
        audioUrl: String,
        startTimeSeconds: Float = 0f
    )
    suspend fun preloadSource(
        song: SongV2,
        audioUrl: String,
    )
    suspend fun crossfadeToPreloaded(
        song: SongV2,
        audioUrl: String,
        durationMs: Long
    )
    suspend fun clearPreloaded()
    fun positionSeconds(): Float?
    fun durationSeconds(): Float?
}