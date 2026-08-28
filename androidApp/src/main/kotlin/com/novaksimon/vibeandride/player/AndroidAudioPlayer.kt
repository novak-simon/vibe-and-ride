package com.novaksimon.vibeandride.player

import android.content.Context
import android.os.Looper
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class AndroidAudioPlayer(
    private val context: Context
) : AudioPlayer {

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null

    private var playerListenerA: Player.Listener? = null
    private var playerListenerB: Player.Listener? = null

    /**
     * 0 = playerA is active
     * 1 = playerB is active
     */
    private var activeSlot: Int = 0

    private var isReleased = false

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
        if (isReleased) return
        if (playerA != null && playerB != null) {
            return
        }


        val httpDataSourceFactory =
            DefaultHttpDataSource.Factory()
                .setUserAgent("VibeAndRide/1.0")
                .setAllowCrossProtocolRedirects(true)

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
                .setLooper(Looper.getMainLooper())
                .build()
                .also {
                    attachListener(it, 0)
                }
        }

        if (playerB == null) {
            playerB = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLooper(Looper.getMainLooper())
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
        val listener = object : Player.Listener {

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

                val uri = player.currentMediaItem?.localConfiguration?.uri
                val title = player.currentMediaItem?.mediaMetadata?.title

                println(
                    "Player Slot $slot error: $message | Song: $title | URI: $uri"
                )

                if (slot == activeSlot) {
                    onError?.invoke(message)
                }
            }
        }

        if (slot == 0) playerListenerA = listener else playerListenerB = listener
        player.addListener(listener)
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
        audioUrl: String,
        startTimeSeconds: Float
    ) {
        if (isReleased) return
        val player = activePlayer() ?: return
        val slot = activeSlot

        pendingStartTimes[slot] = startTimeSeconds
        needsSeekOnReady[slot] = true

        player.volume = 1f
        player.setMediaItem(createMediaItem(song, audioUrl))
        player.prepare()
    }

    override fun preloadSource(
        song: Song,
        audioUrl: String,
    ) {
        if (isReleased) return
        val player = preloadedPlayer() ?: return
        val slot = preloadedSlot()

        // Check if the same song is already preloaded or playing in this slot
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == audioUrl) {
            println("AndroidAudioPlayer: Song ${song.title} already set in slot $slot, skipping preload")
            return
        }

        println("AndroidAudioPlayer: Preloading ${song.title} into slot $slot")

        pendingStartTimes[slot] = song.startTime
        needsSeekOnReady[slot] = true

        player.playWhenReady = false
        player.volume = 0f

        player.setMediaItem(createMediaItem(song, audioUrl))
        player.prepare()
    }

    private fun createMediaItem(song: Song, audioUrl: String): MediaItem {
        return MediaItem.Builder()
            .setUri(audioUrl)
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
        if (isReleased) return
        val player = preloadedPlayer() ?: return

        player.volume =
            volume.coerceIn(0f, 1f)

        player.play()
    }

    override fun rampPreloadedVolume(
        targetVolume: Float,
        durationMs: Long
    ) {
        if (isReleased) return
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
        if (isReleased) return
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
        if (isReleased) return
        fadeJob?.cancel()

        val player =
            preloadedPlayer() ?: return

        player.stop()
        player.clearMediaItems()
    }

    override fun play() {
        if (isReleased) return
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
        if (isReleased) return
        activePlayer()?.pause()
        preloadedPlayer()?.pause()
    }

    override fun isPlaying(): Boolean {
        return activePlayer()?.isPlaying ?: false
    }

    override fun stop() {
        if (isReleased) return
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
    suspend fun crossfadeToPreloaded(song: Song, durationMs: Long) {
        if (isReleased) return
        val oldPlayer = activePlayer() ?: return
        val newPlayer = preloadedPlayer() ?: return

        println(
            "AndroidAudioPlayer: crossfade requested for ${song.title} (duration: ${durationMs}ms), " +
                    "new player state=${newPlayer.playbackState}"
        )

        // Wait until the preloaded player is ready.
        try {
            withTimeout(10000L) {
                awaitPlayerReady(newPlayer)
            }
        } catch (e: Exception) {
            println("AndroidAudioPlayer: preload wait failed: ${e.message}")
            // If it failed but has a media item, we might still want to try switching
            if (newPlayer.currentMediaItem == null) return
        }
        
        println("AndroidAudioPlayer: preloaded player READY, starting crossfade")

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
            return
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

    private suspend fun awaitPlayerReady(player: ExoPlayer) {
        if (player.playbackState == Player.STATE_READY) return
        
        val deferred = CompletableDeferred<Unit>()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    deferred.complete(Unit)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                deferred.completeExceptionally(error)
            }
        }
        
        player.addListener(listener)
        try {
            deferred.await()
        } finally {
            player.removeListener(listener)
        }
    }

    override fun release() {
        if (isReleased) return
        isReleased = true

        fadeJob?.cancel()
        fadeJob = null

        scope.cancel()

        playerListenerA?.let { playerA?.removeListener(it) }
        playerListenerB?.let { playerB?.removeListener(it) }
        playerListenerA = null
        playerListenerB = null

        playerA?.release()
        playerB?.release()

        playerA = null
        playerB = null

        onActivePlayerChanged = null
        onEndOfMedia = null
        onError = null
    }
}