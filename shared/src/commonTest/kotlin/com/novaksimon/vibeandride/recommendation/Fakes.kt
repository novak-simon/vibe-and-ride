package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.music.PlaybackController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakePlaybackController : PlaybackController {
    override val isPlayingFlow: Flow<Boolean> = MutableStateFlow(false)
    override fun play() {}
    override fun pause() {}
    override fun isPlaying(): Boolean = false
    override fun setSource(song: Song, audioUrl: String, startTimeSeconds: Float) {}
    override fun preloadSource(song: Song, audioUrl: String) {}
    override fun crossfadeToPreloaded(song: Song, audioUrl: String, durationMs: Long) {}
    override fun clearPreloaded() {}
    override fun positionSeconds(): Float? = null
    override fun durationSeconds(): Float? = null
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

    override suspend fun changeLikeState(songId: Int, targetState: Boolean, profileId: String?) = mutex.withLock {
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
    var songsAroundTargetCadenceResults: MutableList<List<Song>> = mutableListOf()
    var fetchSongsResults: MutableList<List<Song>> = mutableListOf()

    private val mutex = Mutex()
    val calls = mutableListOf<String>()
    val arguments = mutableListOf<Any?>()

    override suspend fun getSongsAroundTargetCadence(
        targetAlgorithmCadence: Int,
        algorithmCadenceToleration: Int,
        limit: Int
    ): List<Song> = mutex.withLock {
        calls.add("getSongsAroundTargetCadence")
        arguments.add(listOf(targetAlgorithmCadence, algorithmCadenceToleration, limit))
        if (songsAroundTargetCadenceResults.isNotEmpty()) {
            songsAroundTargetCadenceResults.removeAt(0)
        } else {
            emptyList()
        }
    }

    override suspend fun fetchSongs(limit: Int): List<Song> = mutex.withLock {
        calls.add("fetchSongs")
        arguments.add(limit)
        if (fetchSongsResults.isNotEmpty()) {
            fetchSongsResults.removeAt(0)
        } else {
            emptyList()
        }
    }

    override suspend fun getAudioUrl(song: Song): String? = mutex.withLock {
        calls.add("getAudioUrl")
        "https://fake.url/${song.id}"
    }
}
