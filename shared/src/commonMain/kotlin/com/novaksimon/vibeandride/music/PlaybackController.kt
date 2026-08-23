package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song
import kotlinx.coroutines.flow.Flow

interface PlaybackController {
    val isPlayingFlow: Flow<Boolean>
    fun play()
    fun pause()
    fun isPlaying(): Boolean
    fun setSource(
        song: Song,
        startTimeSeconds: Float = 0f
    )
    fun preloadSource(
        song: Song,
    )
    fun crossfadeToPreloaded(
        song: Song,
        durationMs: Long
    )
    fun clearPreloaded()
    fun positionSeconds(): Float?
    fun durationSeconds(): Float?
}