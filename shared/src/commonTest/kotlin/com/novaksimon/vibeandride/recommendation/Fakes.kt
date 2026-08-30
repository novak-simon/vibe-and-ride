package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.music.PlaybackController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakePlaybackController : PlaybackController {
    override val isPlayingFlow: Flow<Boolean> = MutableStateFlow(false)
    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errorFlow: Flow<String> = _errorFlow.asSharedFlow()

    fun emitError(message: String) {
        _errorFlow.tryEmit(message)
    }

    override fun play() {}
    override fun pause() {}
    override fun isPlaying(): Boolean = false
    
    val calls = mutableListOf<String>()

    override suspend fun setSource(song: SongV2, audioUrl: String, startTimeSeconds: Float) {
        calls.add("setSource(${song.id})")
    }
    override suspend fun preloadSource(song: SongV2, audioUrl: String) {
        calls.add("preloadSource(${song.id})")
    }
    override suspend fun crossfadeToPreloaded(song: SongV2, audioUrl: String, durationMs: Long) {
        calls.add("crossfadeToPreloaded(${song.id})")
    }
    override suspend fun clearPreloaded() {
        calls.add("clearPreloaded")
    }
    
    var currentPos: Float? = null
    var currentDur: Float? = null
    
    override fun positionSeconds(): Float? = currentPos
    override fun durationSeconds(): Float? = currentDur
}

class FakeProfileRepository(
    var profile: Profile = Profile("fake-id", emptyList()),
    val stats: MutableMap<Pair<Int, String>, ProfileSongStats> = mutableMapOf()
) : ProfileRepository {
    private val mutex = Mutex()
    val calls = mutableListOf<String>()

    override suspend fun getProfileForRecommendation(profileId: String?): Profile = mutex.withLock {
        calls.add("getProfileForRecommendation")
        profile
    }

    override suspend fun getStatByProfileAndSongId(songId: Int, profileId: String?): ProfileSongStats? = mutex.withLock {
        calls.add("getStatByProfileAndSongId")
        val pid = profileId ?: profile.id
        stats[songId to pid]
    }

    override suspend fun changeLikeState(
        songId: Int,
        targetState: Boolean,
        segmentIndex: Int?,
        profileId: String?
    ) = mutex.withLock {
        calls.add("changeLikeState")
        Unit
    }

    override suspend fun incrementStat(statColumn: String, songId: Int, incrementBy: Int, profileId: String?) = mutex.withLock {
        calls.add("incrementStat")
        Unit
    }

    override suspend fun setStatDatetime(statColumn: String, songId: Int, profileId: String?) = mutex.withLock {
        calls.add("setStatDatetime")
        Unit
    }
}

class FakeSongRepository : SongRepository {
    var songsAroundTargetCadenceResults: MutableList<List<SongV2>> = mutableListOf()
    var fetchSongsResults: MutableList<List<SongV2>> = mutableListOf()

    private val mutex = Mutex()
    val calls = mutableListOf<String>()
    val arguments = mutableListOf<Any?>()

    override suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int,
        limit: Int
    ): Result<List<SongV2>> = mutex.withLock {
        calls.add("getSongsAroundTargetCadence")
        arguments.add(listOf(targetAlgorithmCadence, algorithmCadenceToleration, limit))
        val result = if (songsAroundTargetCadenceResults.isNotEmpty()) {
            songsAroundTargetCadenceResults.removeAt(0)
        } else {
            emptyList()
        }
        Result.success(result)
    }

    override suspend fun fetchSongs(limit: Int): Result<List<SongV2>> = mutex.withLock {
        calls.add("fetchSongs")
        arguments.add(limit)
        val result = if (fetchSongsResults.isNotEmpty()) {
            fetchSongsResults.removeAt(0)
        } else {
            emptyList()
        }
        Result.success(result)
    }

    var audioUrlResults: MutableMap<Int, Result<String>> = mutableMapOf()

    override suspend fun getAudioUrlV2(song: SongV2): Result<String> = mutex.withLock {
        calls.add("getAudioUrl")
        audioUrlResults[song.id] ?: Result.success("https://fake.url/${song.id}")
    }
}