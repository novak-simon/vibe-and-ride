package com.novaksimon.vibeandride.recommendation

import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.util.VibeLog

private const val TAG = "CandidateProvider"
class CandidateProvider(
    private val songRepository: SongRepository
) {
    /**
     * Provides a list of song candidates that are near the target BPM and vector embedding.
     * The search starts with a +/- 2 BPM range and expands if fewer than [minimumLimit] songs are found.
     * The results are ordered by the distance between the song's closest segment and the [targetVector].
     *
     * @param minimumLimit The minimum number of songs to find before expanding the BPM boundary.
     * @param limit The maximum number of songs to return.
     * @param targetBpm The target BPM for the songs.
     * @param targetVector The 512-D vector embedding to match against song segments.
     * @return A list of [Song] candidates.
     */
    suspend fun getCandidates(
        minimumLimit: Int,
        limit: Int,
        targetBpm: Int,
        targetVector: List<Float>
    ): List<Song> {
        VibeLog.d(TAG, "Fetching candidates by embedding and bpm")
        return songRepository.searchSongsByEmbedding(
            targetEmbedding = targetVector,
            targetBpm = targetBpm,
            minLimit = minimumLimit,
            maxLimit = limit
        ).getOrElse { emptyList() }
    }
}
