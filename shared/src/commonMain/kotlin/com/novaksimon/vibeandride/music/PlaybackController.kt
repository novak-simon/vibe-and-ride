package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song

interface PlaybackController {
    fun play()
    fun pause()
    fun isPlaying(): Boolean
    fun setSource(
        song: Song,
        startTimeSeconds: Float = 0f
    )
    fun preloadSource(
        song: Song,
        startTimeSeconds: Float = 0f
    )
    fun crossfadeToPreloaded(
        song: Song,
        durationMs: Long
    )
    fun clearPreloaded()
    fun positionSeconds(): Float?
    fun durationSeconds(): Float?
}