package com.novaksimon.vibeandride.music

import com.novaksimon.vibeandride.model.Song

interface AudioPlayer {
    fun initialize()
    /** Prepares and loads the track, seeking to startTimeSeconds once ready. */
    fun setSource(song: Song, startTimeSeconds: Float)
    /** Prepares a secondary track that can be started while the current one is still playing. */
    fun preloadSource(song: Song, startTimeSeconds: Float)
    fun hasPreloadedSource(): Boolean
    fun startPreloaded(volume: Float)
    fun rampPreloadedVolume(targetVolume: Float, durationMs: Long)
    /** Promotes the preloaded track to current and clears the old current track. */
    fun promotePreloadedToCurrent()
    fun clearPreloaded()
    fun play()
    fun pause()
    fun isPlaying(): Boolean
    fun stop()
    fun release()
    fun hasValidSource(): Boolean
    fun hasError(): Boolean
    fun hasPreloadError(): Boolean
    fun isPlaybackEnded(): Boolean
    fun positionSeconds(): Float?
    fun durationSeconds(): Float?

    /** Platform implementation calls this when the current track finishes. */
    var onEndOfMedia: (() -> Unit)?
    var onError: ((message: String) -> Unit)?
}