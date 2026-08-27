package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.recommendation.ProfileRepository

class SupabaseProfileRepository : ProfileRepository {
    override suspend fun getProfileForRecommendation(profileId: String?): Profile {
        return com.novaksimon.vibeandride.database.getProfileForRecommendation(profileId)
    }

    override suspend fun getStatByProfileAndSongId(songId: Int, profileId: String?): ProfileSongStats? {
        return com.novaksimon.vibeandride.database.getStatByProfileAndSongId(songId, profileId)
    }

    override suspend fun changeLikeState(songId: Int, targetState: Boolean, profileId: String?) {
        com.novaksimon.vibeandride.database.changeLikeState(songId, targetState, profileId)
    }

    override suspend fun incrementStat(statColumn: String, songId: Int, incrementBy: Int, profileId: String?) {
        com.novaksimon.vibeandride.database.incrementStat(statColumn, songId, profileId, incrementBy)
    }

    override suspend fun setStatDatetime(statColumn: String, songId: Int, profileId: String?) {
        com.novaksimon.vibeandride.database.setStatDatetime(statColumn, songId, profileId)
    }
}
