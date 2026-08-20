package com.novaksimon.vibeandride.player

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.novaksimon.vibeandride.music.PlaybackController

class AndroidPlaybackController(
    private val controller: MediaController
) : PlaybackController {

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
        url: String,
        startTimeSeconds: Float
    ) {
        sendCommand(
            PlaybackService.COMMAND_SET_SOURCE,
            Bundle().apply {
                putString("url", url)
                putFloat("startTime", startTimeSeconds)
            }
        )
    }

    override fun preloadSource(
        url: String,
        startTimeSeconds: Float
    ) {
        println("AndroidPlaybackController: PRELOAD $url")

        sendCommand(
            PlaybackService.COMMAND_PRELOAD,
            Bundle().apply {
                putString("url", url)
                putFloat("startTime", startTimeSeconds)
            }
        )
    }
    override fun crossfadeToPreloaded(
        durationMs: Long
    ) {
        println("AndroidPlaybackController: sending CROSSFADE")
        sendCommand(
            PlaybackService.COMMAND_CROSSFADE,
            Bundle().apply {
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