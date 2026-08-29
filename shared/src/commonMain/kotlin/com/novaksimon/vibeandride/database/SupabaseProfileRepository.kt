package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.recommendation.ProfileRepository
import com.novaksimon.vibeandride.database.getProfileForRecommendation as getProfileForRecommendationInternal
import com.novaksimon.vibeandride.database.getStatByProfileAndSongId as getStatByProfileAndSongIdInternal
import com.novaksimon.vibeandride.database.changeLikeState as changeLikeStateInternal
import com.novaksimon.vibeandride.database.incrementStat as incrementStatInternal
import com.novaksimon.vibeandride.database.setStatDatetime as setStatDatetimeInternal

class SupabaseProfileRepository : ProfileRepository {
    override suspend fun getProfileForRecommendation(profileId: String?): Profile {
        return getProfileForRecommendationInternal(profileId)
    }

    override suspend fun getStatByProfileAndSongId(songId: Int, profileId: String?): ProfileSongStats? {
        return getStatByProfileAndSongIdInternal(songId, profileId)
    }

    override suspend fun changeLikeState(songId: Int, targetState: Boolean, segmentIndex: Int?, profileId: String?) {
        changeLikeStateInternal(songId, targetState, segmentIndex, profileId)
    }

    override suspend fun incrementStat(statColumn: String, songId: Int, incrementBy: Int, profileId: String?) {
        incrementStatInternal(statColumn, songId, profileId, incrementBy)
    }

    override suspend fun setStatDatetime(statColumn: String, songId: Int, profileId: String?) {
        setStatDatetimeInternal(statColumn, songId, profileId)
    }
}
