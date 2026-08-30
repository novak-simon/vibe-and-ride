package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.RawInterval
import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.model.RawProfile
import com.novaksimon.vibeandride.model.RawProfileSongStats
import com.novaksimon.vibeandride.model.RawSongV2
import com.novaksimon.vibeandride.model.RawWorkout
import com.novaksimon.vibeandride.model.SongV2
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.model.estimateCadenceFromPower
import kotlin.math.round

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
        chosenCount = rawWorkout.chosenCount ?: 0,
        intervals = rawWorkout.intervals?.map { rawToInterval(it) } ?: emptyList()
    )
}
fun rawToSongV2(rawSong: RawSongV2): SongV2 {
    return SongV2(
        id = rawSong.id.toInt(),
        title = rawSong.title ?: "Unknown Title",
        artistId = rawSong.artistId?.toInt() ?: 0,
        releaseDate = rawSong.releaseDate ?: "Unknown Date",
        genre = rawSong.genre ?: "",
        tags = rawSong.tags ?: emptyList(),
        duration = rawSong.duration ?: 0,
        bpm = rawSong.bpm ?: 0,
        startTime = rawSong.startTime ?: 0.0f,
        endTime = rawSong.endTime ?: 0.0f,
        image = rawSong.image ?: "Unknown Image",
        shareUrl = rawSong.shareUrl ?: "Unknown Share URL",
        description = rawSong.description ?: "No description",
        mood = rawSong.mood ?: "Unknown Mood",
        provider = rawSong.provider,
        audiusProviderId = rawSong.audiusProviderId,
        jamendoProviderId = rawSong.jamendoProviderId?.toInt(),
    )
}
fun rawToStat(rawProfileSongStats: RawProfileSongStats): ProfileSongStats {
    return ProfileSongStats(
        songId = rawProfileSongStats.songId ?: 0,
        profileId = rawProfileSongStats.profileId ?: "",
        playCount = rawProfileSongStats.playCount ?: 0,
        skipCount = rawProfileSongStats.skipCount ?: 0,
        isLiked = rawProfileSongStats.isLiked ?: false,
        firstPlayedAt = rawProfileSongStats.firstPlayedAt,
        lastPlayedAt = rawProfileSongStats.lastPlayedAt,
        lastSkippedAt = rawProfileSongStats.lastSkippedAt,
        totalPlayTime = rawProfileSongStats.totalPlayTime ?: 0
    )
}
fun rawToProfile(rawProfile: RawProfile): Profile {
    return Profile(
        id = rawProfile.id ?: "",
        genrePreferences = rawProfile.genrePreferences ?: emptyList()
    )
}
