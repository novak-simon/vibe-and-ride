package com.novaksimon.vibeandride.player

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audioPlayer: AndroidAudioPlayer
    private lateinit var mediaSession: MediaSession

    companion object {
        const val COMMAND_SET_SOURCE = "SET_SOURCE"
        const val COMMAND_PRELOAD = "PRELOAD"
        const val COMMAND_START_PRELOADED = "START_PRELOADED"
        const val COMMAND_RAMP_PRELOADED = "RAMP_PRELOADED"
        const val COMMAND_PROMOTE_PRELOADED = "PROMOTE_PRELOADED"
        const val COMMAND_CLEAR_PRELOADED = "CLEAR_PRELOADED"
        const val COMMAND_CROSSFADE = "CROSSFADE"
    }

    private val setSourceCommand =
        SessionCommand(COMMAND_SET_SOURCE, Bundle.EMPTY)

    private val preloadCommand =
        SessionCommand(COMMAND_PRELOAD, Bundle.EMPTY)

    private val startPreloadedCommand =
        SessionCommand(COMMAND_START_PRELOADED, Bundle.EMPTY)

    private val rampPreloadedCommand =
        SessionCommand(COMMAND_RAMP_PRELOADED, Bundle.EMPTY)

    private val promotePreloadedCommand =
        SessionCommand(COMMAND_PROMOTE_PRELOADED, Bundle.EMPTY)

    private val clearPreloadedCommand =
        SessionCommand(COMMAND_CLEAR_PRELOADED, Bundle.EMPTY)

    private val crossfadeCommand =
        SessionCommand(COMMAND_CROSSFADE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()

        audioPlayer = AndroidAudioPlayer(applicationContext)
        serviceScope.launch {
            audioPlayer.initialize()
        }
        // Your AndroidAudioPlayer owns the actual ExoPlayers.
        // The MediaSession needs one player to expose as its current player.
        val activePlayer = audioPlayer.getActivePlayer() ?: error("AudioPlayer failed to initialize")

        mediaSession = MediaSession.Builder(this, activePlayer)
            .setCallback(object : MediaSession.Callback {

                @OptIn(UnstableApi::class)
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {

                    val commands =
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            .buildUpon()
                            .add(setSourceCommand)
                            .add(preloadCommand)
                            .add(startPreloadedCommand)
                            .add(rampPreloadedCommand)
                            .add(promotePreloadedCommand)
                            .add(clearPreloadedCommand)
                            .add(crossfadeCommand)
                            .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
                    println(
                        "PlaybackService: received command " +
                                customCommand.customAction
                    )
                    when (customCommand.customAction) {

                        COMMAND_SET_SOURCE -> {
                            val url = args.getString("url")
                            val startTime =
                                args.getFloat("startTime", 0f)

                            if (url != null) {
                                audioPlayer.setSource(
                                    url,
                                    startTime
                                )
                            }
                        }

                        COMMAND_PRELOAD -> {
                            println("PlaybackService: PRELOAD")

                            val url = args.getString("url")
                            val startTime = args.getFloat("startTime", 0f)

                            println("URL=$url startTime=$startTime")

                            if (url != null) {
                                audioPlayer.preloadSource(
                                    url,
                                    startTime
                                )
                            }
                        }

                        COMMAND_START_PRELOADED -> {
                            val volume =
                                args.getFloat("volume", 1f)

                            audioPlayer.startPreloaded(volume)
                        }

                        COMMAND_RAMP_PRELOADED -> {
                            val volume =
                                args.getFloat("volume", 1f)

                            val duration =
                                args.getLong("durationMs", 3000L)

                            audioPlayer.rampPreloadedVolume(
                                volume,
                                duration
                            )
                        }

                        COMMAND_PROMOTE_PRELOADED -> {
                            audioPlayer.promotePreloadedToCurrent()
                        }

                        COMMAND_CLEAR_PRELOADED -> {
                            audioPlayer.clearPreloaded()
                        }

                        COMMAND_CROSSFADE -> {
                            println("PlaybackService: CROSSFADE")

                            val duration = args.getLong(
                                "durationMs",
                                3000L
                            )

                            println("Crossfade duration=$duration")

                            audioPlayer.crossfadeToPreloaded(duration)
                        }
                    }

                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
                }
            })
            .build()
        audioPlayer.onActivePlayerChanged = { newPlayer ->
            println("PlaybackService: switching MediaSession to new player")

            mediaSession.setPlayer(newPlayer)
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession.release()
        audioPlayer.release()
        super.onDestroy()
    }
}