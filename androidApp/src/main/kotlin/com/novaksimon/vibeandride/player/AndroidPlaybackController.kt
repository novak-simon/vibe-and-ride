package com.novaksimon.vibeandride.player

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.music.PlaybackController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json


class AndroidPlaybackController(
    private val controller: MediaController
) : PlaybackController {

    private val _isPlayingFlow = MutableStateFlow(controller.isPlaying)
    override val isPlayingFlow: Flow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errorFlow: Flow<String> = _errorFlow.asSharedFlow()

    init {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlayingFlow.value = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                _errorFlow.tryEmit(error.message ?: "Unknown ExoPlayer error")
            }
        })
    }

    override fun play() {
        controller.play()
    }

    override fun pause() {
        controller.pause()
    }

    override fun isPlaying(): Boolean {
        return controller.isPlaying
    }

    override fun setSource(
        song: Song,
        audioUrl: String,
        startTimeSeconds: Float
    ) {
        sendCommand(
            PlaybackService.COMMAND_SET_SOURCE,
            Bundle().apply {
                putString("song", Json.encodeToString(song))
                putString("audioUrl", audioUrl)
                putFloat("startTime", startTimeSeconds)
            }
        )
    }

    override fun preloadSource(
        song: Song,
        audioUrl: String,
    ) {
        println("AndroidPlaybackController: PRELOAD $song")

        sendCommand(
            PlaybackService.COMMAND_PRELOAD,
            Bundle().apply {
                putString("song", Json.encodeToString(song))
                putString("audioUrl", audioUrl)
            }
        )
    }
    override fun crossfadeToPreloaded(
        song: Song,
        audioUrl: String,
        durationMs: Long
    ) {
        println("AndroidPlaybackController: sending CROSSFADE")
        sendCommand(
            PlaybackService.COMMAND_CROSSFADE,
            Bundle().apply {
                putString("song", Json.encodeToString(song))
                putString("audioUrl", audioUrl)
                putLong("durationMs", durationMs)
            }
        )
    }
    override fun clearPreloaded() {
        sendCommand(
            PlaybackService.COMMAND_CLEAR_PRELOADED
        )
    }

    override fun positionSeconds(): Float? {
        val position = controller.currentPosition

        return if (position >= 0) {
            position / 1000f
        } else {
            null
        }
    }

    override fun durationSeconds(): Float? {
        val duration = controller.duration

        return if (duration > 0) {
            duration / 1000f
        } else {
            null
        }
    }

    private fun sendCommand(
        action: String,
        args: Bundle = Bundle()
    ) {
        controller.sendCustomCommand(
            SessionCommand(
                action,
                Bundle.EMPTY
            ),
            args
        )
    }
}