package com.novaksimon.vibeandride.model

import com.novaksimon.vibeandride.database.getArtistNameById

fun estimateCadenceFromPower(power: Float?): Int {
    val p = power ?: return 80 // matches Python's except-branch default
    val cadence = 70 + 30 * p
    return cadence.coerceIn(75f, 115f).toInt()
}

suspend fun songToDisplaySong(song: SongV2): DisplaySongV2 {
    return DisplaySongV2(
        id = song.id,
        title = song.title,
        artistId = song.artistId,
        artist = getArtistNameById(song.artistId) ?: "Unknown Artist",
        releaseDate = song.releaseDate,
        duration = song.duration,
        description = song.description,
        genre = song.genre,
        bpm = song.bpm,
        tags = song.tags,
        mood = song.mood,
        startTime = song.startTime,
        endTime = song.endTime,
        image = song.image,
        shareUrl = song.shareUrl,
        provider = song.provider,
        audiusProviderId = song.audiusProviderId,
        jamendoProviderId = song.jamendoProviderId

    )
}