package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.RawInterval
import com.novaksimon.vibeandride.model.RawSong
import com.novaksimon.vibeandride.model.RawUser
import com.novaksimon.vibeandride.model.RawUserSongStats
import com.novaksimon.vibeandride.model.RawWorkout
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.User
import com.novaksimon.vibeandride.model.UserSongStats
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.model.estimateCadenceFromPower
import kotlin.time.Clock
import kotlin.math.round
import kotlin.time.Instant

fun rawToInterval(rawInterval: RawInterval): Interval {
    var duration = rawInterval.duration
    if (duration == null || duration == 0.0) {
        duration = 60.0 // Default duration
    }
    var power = rawInterval.power
    val powerLow = rawInterval.powerLow
    val powerHigh = rawInterval.powerHigh
    if (power == null || power == 0f) {
        power = when {
            powerLow != null && powerHigh != null && powerLow != 0f && powerHigh != 0f ->
                (powerLow + powerHigh) / 2f
            powerLow != null && powerLow != 0f -> powerLow
            powerHigh != null && powerHigh != 0f -> powerHigh
            else -> 0.6f
        }
    }

    var cadence = rawInterval.cadence
    val cadenceLow = rawInterval.cadenceLow
    val cadenceHigh = rawInterval.cadenceHigh
    if (cadence == null || cadence == 0.0) {
        cadence = when {
            cadenceLow != null && cadenceHigh != null && cadenceLow != 0.0 && cadenceHigh != 0.0 ->
                round((cadenceLow + cadenceHigh) / 2)
            cadenceLow != null && cadenceLow != 0.0 -> cadenceLow
            cadenceHigh != null && cadenceHigh != 0.0 -> cadenceHigh
            else -> estimateCadenceFromPower(power).toDouble()
        }
    }
    var type = rawInterval.type
    if (type == null) type = "SteadyState"
    return Interval(
        duration = duration,
        power = power,
        cadence = cadence,
        type = type
    )

}


fun rawToWorkout(rawWorkout: RawWorkout): Workout {
    return Workout(
        id = rawWorkout.id,
        name = rawWorkout.name ?: "Unnamed Workout",
        author = rawWorkout.author ?: "Unknown Author",
        sportType = rawWorkout.sportType ?: true,
        tags = rawWorkout.tags ?: emptyList(),
        description = rawWorkout.description ?: "No description",
        intervals = rawWorkout.intervals?.map { rawToInterval(it) } ?: emptyList()
    )
}

fun rawToSong(rawSong: RawSong): Song {
    return Song(
        id = rawSong.id,
        title = rawSong.title ?: "Unknown Title",
        artist = rawSong.artist ?: "Unknown Artist",
        releaseDate = rawSong.releaseDate ?: "Unknown Date",
        genre = rawSong.genre ?: emptyList(),
        instruments = rawSong.instruments ?: emptyList(),
        vartags = rawSong.vartags ?: emptyList(),
        duration = rawSong.duration ?: 0,
        speed = rawSong.speed ?: 0,
        bpm = rawSong.bpm ?: 0,
        bpmConfidence = rawSong.bpmConfidence ?: 0,
        audioUrl = rawSong.audioUrl ?: "Unknown URL",
        startTime = rawSong.startTime ?: 0.0f,
        endTime = rawSong.endTime ?: 0.0f,
        image = rawSong.image ?: "Unknown Image",
        shareUrl = rawSong.shareUrl ?: "Unknown Share URL",
        lang = rawSong.lang ?: "Unknown Language",
        gender = rawSong.gender ?: "Unknown Gender",
        isInstrumental = rawSong.isInstrumental ?: false,
        isElectric = rawSong.isElectric ?: false
    )
}

fun rawToStat(rawUserSongStats: RawUserSongStats): UserSongStats {
    return UserSongStats(
        songId = rawUserSongStats.songId ?: 0,
        userId = rawUserSongStats.userId ?: 0,
        playCount = rawUserSongStats.playCount ?: 0,
        skipCount = rawUserSongStats.skipCount ?: 0,
        isLiked = rawUserSongStats.isLiked ?: false,
        firstPlayedAt = rawUserSongStats.firstPlayedAt,
        lastPlayedAt = rawUserSongStats.lastPlayedAt,
        lastSkippedAt = rawUserSongStats.lastSkippedAt,
        totalPlayTime = rawUserSongStats.totalPlayTime ?: 0
    )
}
fun rawToUser(rawUser: RawUser): User {
    return User(
        id = rawUser.id ?: 0,
        name = rawUser.name ?: "Unknown Name",
        genrePreferences = rawUser.genrePreferences ?: emptyList()
    )
}