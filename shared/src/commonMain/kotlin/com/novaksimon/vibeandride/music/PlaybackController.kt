package com.novaksimon.vibeandride.music

interface PlaybackController {
    fun play()
    fun pause()
    fun isPlaying(): Boolean
    fun setSource(
        url: String,
        startTimeSeconds: Float = 0f
    )
    fun preloadSource(
        url: String,
        startTimeSeconds: Float = 0f
    )
    fun crossfadeToPreloaded(
        durationMs: Long
    )
    fun clearPreloaded()
    fun positionSeconds(): Float?
    fun durationSeconds(): Float?
}