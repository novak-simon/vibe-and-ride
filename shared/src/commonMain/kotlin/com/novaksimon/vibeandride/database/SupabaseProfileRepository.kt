package com.novaksimon.vibeandride.database

import com.novaksimon.vibeandride.model.Profile
import com.novaksimon.vibeandride.model.ProfileSongStats
import com.novaksimon.vibeandride.recommendation.ProfileRepository
import com.novaksimon.vibeandride.database.getProfileForRecommendation as getProfileForRecommendationInternal
import com.novaksimon.vibeandride.database.getStatByProfileAndSongId as getStatByProfileAndSongIdInternal
import com.novaksimon.vibeandride.database.changeLikeState as changeLikeStateInternal
import com.novaksimon.vibeandride.database.incrementStatFloat as incrementStatInternalFloat
import com.novaksimon.vibeandride.database.incrementStatInt as incrementStatInternalInt
import com.novaksimon.vibeandride.database.setStatDatetime as setStatDatetimeInternal

class SupabaseProfileRepository : ProfileRepository {
    override suspend fun getProfileForRecommendation(profileId: String?): Profile {
        return getProfileForRecommendationInternal(profileId)
    }

    override suspend fun getStatByProfileAndSongId(songId: Int, profileId: String?): ProfileSongStats? {
        return getStatByProfileAndSongIdInternal(songId, profileId)
    }

    override suspend fun changeLikeState(songId: Int, targetState: Boolean, segmentIndex: Int?, profileId: String?) {
        changeLikeStateInternal(songId, targetState, profileId)
    }

    override suspend fun incrementStatFloat(statColumn: String, songId: Int, incrementBy: Float, profileId: String?) {
        incrementStatInternalFloat(statColumn, songId, profileId, incrementBy)
    }

    override suspend fun incrementStatInt(statColumn: String, songId: Int, incrementBy: Int, profileId: String?) {
        incrementStatInternalInt(statColumn, songId, profileId, incrementBy)
    }

    override suspend fun setStatDatetime(statColumn: String, songId: Int, profileId: String?) {
        setStatDatetimeInternal(statColumn, songId, profileId)
    }
}
