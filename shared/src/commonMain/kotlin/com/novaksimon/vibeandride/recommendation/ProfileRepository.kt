package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats

interface ProfileRepository {
    suspend fun getProfileForRecommendation(profileId: String? = null): Profile
    suspend fun getStatByProfileAndSongId(songId: Int, profileId: String? = null): ProfileSongStats?
    suspend fun changeLikeState(songId: Int, targetState: Boolean, segmentIndex: Int? = null, profileId: String? = null)
    suspend fun incrementStatFloat(statColumn: String, songId: Int, incrementBy: Float = 1.0f, profileId: String? = null)
    suspend fun incrementStatInt(statColumn: String, songId: Int, incrementBy: Int = 1, profileId: String? = null)

    suspend fun setStatDatetime(statColumn: String, songId: Int, profileId: String? = null)
}
