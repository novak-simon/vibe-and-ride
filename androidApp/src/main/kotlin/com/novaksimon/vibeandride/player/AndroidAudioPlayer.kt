package com.novaksimon.vibeandride.player

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.music.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class AndroidAudioPlayer(
    private val context: Context
) : AudioPlayer {

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null

    /**
     * 0 = playerA is active
     * 1 = playerB is active
     */
    private var activeSlot: Int = 0

    private val pendingStartTimes = FloatArray(2)
    private val needsSeekOnReady = BooleanArray(2)

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var fadeJob: Job? = null

    override var onEndOfMedia: (() -> Unit)? = null
    override var onError: ((message: String) -> Unit)? = null

    /**
     * Called whenever the active ExoPlayer changes.
     *
     * PlaybackService should use this to keep its MediaSession
     * attached to the currently active player.
     */
    var onActivePlayerChanged: ((ExoPlayer) -> Unit)? = null

    companion object {
        private var cache: SimpleCache? = null

        @Synchronized
        private fun getCache(context: Context): SimpleCache {
            if (cache == null) {
                val cacheDir = File(context.cacheDir, "media_cache")

                val evictor = LeastRecentlyUsedCacheEvictor(
                    100 * 1024 * 1024 // 100 MB
                )

                val databaseProvider = StandaloneDatabaseProvider(context)

                cache = SimpleCache(
                    cacheDir,
                    evictor,
                    databaseProvider
                )
            }

            return cache!!
        }
    }

    override fun initialize() {
        if (playerA != null && playerB != null) {
            return
        }


        val httpDataSourceFactory =
            DefaultHttpDataSource.Factory()

        val cacheDataSourceFactory =
            CacheDataSource.Factory()
                .setCache(getCache(context.applicationContext))
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
                )

        val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory)

        if (playerA == null) {
            playerA = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .also {
                    attachListener(it, 0)
                }
        }

        if (playerB == null) {
            playerB = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .also {
                    attachListener(it, 1)
                }
        }

        activeSlot = 0

        pendingStartTimes[0] = 0f
        pendingStartTimes[1] = 0f

        needsSeekOnReady[0] = false
        needsSeekOnReady[1] = false

        // Tell the service which player is initially active.
        playerA?.let {
            onActivePlayerChanged?.invoke(it)
        }

    }

    private fun attachListener(
        player: ExoPlayer,
        slot: Int
    ) {
        player.addListener(
            object : Player.Listener {

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    println(
                        "Player $slot state changed: $playbackState"
                    )

                    if (
                        playbackState == Player.STATE_READY &&
                        needsSeekOnReady[slot]
                    ) {
                        player.seekTo(
                            (pendingStartTimes[slot] * 1000f).toLong()
                        )

                        needsSeekOnReady[slot] = false
                    }

                    if (
                        playbackState == Player.STATE_ENDED &&
                        slot == activeSlot
                    ) {
                        onEndOfMedia?.invoke()
                    }
                }

                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    val message =
                        error.message ?: "Unknown playback error"

                    println(
                        "Player Slot $slot error: $message"
                    )

                    if (slot == activeSlot) {
                        onError?.invoke(message)
                    }
                }
            }
        )
    }

    private fun activePlayer(): ExoPlayer? {
        return if (activeSlot == 0) {
            playerA
        } else {
            playerB
        }
    }

    private fun preloadedPlayer(): ExoPlayer? {
        return if (activeSlot == 0) {
            playerB
        } else {
            playerA
        }
    }

    private fun preloadedSlot(): Int {
        return if (activeSlot == 0) 1 else 0
    }

    /**
     * Public access for PlaybackService / MediaSession.
     */
    fun getActivePlayer(): ExoPlayer? {
        return activePlayer()
    }

    override fun setSource(
        song: Song,
        startTimeSeconds: Float
    ) {
        val player = activePlayer() ?: return
        val slot = activeSlot

        pendingStartTimes[slot] = startTimeSeconds
        needsSeekOnReady[slot] = true

        player.volume = 1f
        player.setMediaItem(createMediaItem(song))
        player.prepare()
    }

    override fun preloadSource(
        song: Song,
        startTimeSeconds: Float
    ) {
        val player = preloadedPlayer() ?: return
        val slot = preloadedSlot()

        pendingStartTimes[slot] = startTimeSeconds
        needsSeekOnReady[slot] = true

        player.playWhenReady = false
        player.volume = 0f

        player.setMediaItem(createMediaItem(song))
        player.prepare()
    }

    private fun createMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
            .setUri(song.audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.image.toUri())
                    .build()
            )
            .build()
    }

    override fun hasPreloadedSource(): Boolean {
        val player = preloadedPlayer() ?: return false

        return player.currentMediaItem != null &&
                player.playerError == null
    }

    override fun startPreloaded(
        volume: Float
    ) {
        val player = preloadedPlayer() ?: return

        player.volume =
            volume.coerceIn(0f, 1f)

        player.play()
    }

    override fun rampPreloadedVolume(
        targetVolume: Float,
        durationMs: Long
    ) {
        val player = preloadedPlayer() ?: return

        val clampedTarget =
            targetVolume.coerceIn(0f, 1f)

        val clampedDuration =
            durationMs.coerceAtLeast(0L)

        fadeJob?.cancel()

        if (clampedDuration == 0L) {
            player.volume = clampedTarget
            return
        }

        val initial =
            player.volume.coerceIn(0f, 1f)

        val steps =
            maxOf(
                1,
                (clampedDuration / 50L).toInt()
            )

        fadeJob = scope.launch {

            repeat(steps) { step ->

                val progress =
                    (step + 1).toFloat() /
                            steps.toFloat()

                player.volume =
                    initial +
                            (clampedTarget - initial) *
                            progress

                delay(
                    (clampedDuration / steps)
                        .milliseconds
                )
            }

            player.volume = clampedTarget
        }
    }

    override fun promotePreloadedToCurrent() {
        val nextActive =
            preloadedPlayer() ?: return

        if (nextActive.currentMediaItem == null) {
            return
        }

        fadeJob?.cancel()

        nextActive.volume = 1f

        val oldActiveSlot = activeSlot
        val nextActiveSlot = preloadedSlot()

        activeSlot = nextActiveSlot

        val oldPlayer =
            if (oldActiveSlot == 0) {
                playerA
            } else {
                playerB
            }

        oldPlayer?.stop()
        oldPlayer?.clearMediaItems()

        /*
         * This is the important part for MediaSession.
         *
         * PlaybackService should switch the MediaSession to this
         * player immediately after the active slot changes.
         */
        onActivePlayerChanged?.invoke(nextActive)
    }

    override fun clearPreloaded() {
        fadeJob?.cancel()

        val player =
            preloadedPlayer() ?: return

        player.stop()
        player.clearMediaItems()
    }

    override fun play() {
        val player =
            activePlayer() ?: return

        if (
            player.playbackState == Player.STATE_IDLE ||
            player.playerError != null
        ) {
            if (player.currentMediaItem != null) {
                player.prepare()
            }
        }

        player.play()
    }

    override fun pause() {
        activePlayer()?.pause()
        preloadedPlayer()?.pause()
    }

    override fun isPlaying(): Boolean {
        return activePlayer()?.isPlaying ?: false
    }

    override fun stop() {
        fadeJob?.cancel()

        activePlayer()?.stop()

        preloadedPlayer()?.stop()
        preloadedPlayer()?.clearMediaItems()
    }

    override fun hasValidSource(): Boolean {
        val player =
            activePlayer() ?: return false

        return player.currentMediaItem != null &&
                player.playerError == null
    }

    override fun hasError(): Boolean {
        return activePlayer()?.playerError != null
    }

    override fun hasPreloadError(): Boolean {
        return preloadedPlayer()?.playerError != null
    }

    override fun isPlaybackEnded(): Boolean {
        return activePlayer()?.playbackState ==
                Player.STATE_ENDED
    }

    override fun positionSeconds(): Float? {
        val player =
            activePlayer() ?: return null

        val position =
            player.currentPosition

        return if (position >= 0) {
            position / 1000f
        } else {
            null
        }
    }

    override fun durationSeconds(): Float? {
        val player =
            activePlayer() ?: return null

        val duration =
            player.duration

        return if (duration > 0) {
            duration / 1000f
        } else {
            null
        }
    }
    fun crossfadeToPreloaded(song: Song, durationMs: Long) {
        val oldPlayer = activePlayer() ?: return
        val newPlayer = preloadedPlayer() ?: return

        println(
            "AndroidAudioPlayer: crossfade requested for ${song.title}, " +
                    "new player state=${newPlayer.playbackState}"
        )

        fadeJob?.cancel()

        fadeJob = scope.launch {
            // Wait until a media item is set (preload triggered).
            while (newPlayer.currentMediaItem == null) {
                println("AndroidAudioPlayer: waiting for media item to be set...")
                delay(50.milliseconds)
            }

            // Wait until the preloaded player is ready.
            while (
                newPlayer.playbackState != ExoPlayer.STATE_READY &&
                newPlayer.playbackState != ExoPlayer.STATE_ENDED
            ) {
                delay(20.milliseconds)
            }

            if (newPlayer.playbackState != ExoPlayer.STATE_READY) {
                println("AndroidAudioPlayer: preload failed or ended early")
                return@launch
            }

            println("AndroidAudioPlayer: preloaded player READY")

            newPlayer.volume = 0f
            newPlayer.play()

            val duration = durationMs.coerceAtLeast(0L)

            if (duration == 0L) {
                oldPlayer.stop()
                oldPlayer.clearMediaItems()

                newPlayer.volume = 1f
                activeSlot = preloadedSlot()
                onActivePlayerChanged?.invoke(newPlayer)

                println("AndroidAudioPlayer: instant switch complete")
                return@launch
            }

            val steps = maxOf(
                1,
                (duration / 20L).toInt()
            )

            repeat(steps) { step ->
                val progress =
                    (step + 1).toFloat() / steps.toFloat()

                oldPlayer.volume = 1f - progress
                newPlayer.volume = progress

                delay((duration / steps).milliseconds)
            }

            oldPlayer.volume = 0f
            newPlayer.volume = 1f

            oldPlayer.stop()
            oldPlayer.clearMediaItems()

            activeSlot = preloadedSlot()
            onActivePlayerChanged?.invoke(newPlayer)

            println(
                "AndroidAudioPlayer: crossfade complete, " +
                        "activeSlot=$activeSlot"
            )
        }
    }

    override fun release() {
        fadeJob?.cancel()
        fadeJob = null

        scope.cancel()

        playerA?.release()
        playerB?.release()

        playerA = null
        playerB = null

        onActivePlayerChanged = null
        onEndOfMedia = null
        onError = null
    }

    fun isAvailable(): Boolean {
        return activePlayer() != null
    }
}