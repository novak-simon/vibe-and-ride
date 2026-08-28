package com.novaksimon.vibeandride.model

sealed class VibeAndRideError(
    override val message: String,
    val technicalDetails: String? = null,
    val throwable: Throwable? = null
) : Exception(message, throwable) {

    sealed class MusicError(message: String, technicalDetails: String? = null, throwable: Throwable? = null) :
        VibeAndRideError(message, technicalDetails, throwable) {
        
        class MetadataFetchFailed(technicalDetails: String? = null, throwable: Throwable? = null) :
            MusicError("Failed to fetch song metadata", technicalDetails, throwable)
            
        class AudioUrlNotFound(val songId: String, technicalDetails: String? = null, throwable: Throwable? = null) :
            MusicError("Audio URL not found for song $songId", technicalDetails, throwable)
            
        class RecommendationFailed(technicalDetails: String? = null, throwable: Throwable? = null) :
            MusicError("Failed to get music recommendations", technicalDetails, throwable)
    }

    sealed class WorkoutError(message: String, technicalDetails: String? = null, throwable: Throwable? = null) :
        VibeAndRideError(message, technicalDetails, throwable) {
        
        class ParsingFailed(val fileName: String, technicalDetails: String? = null, throwable: Throwable? = null) :
            WorkoutError("Failed to parse workout file: $fileName", technicalDetails, throwable)
            
        class LoadFailed(technicalDetails: String? = null, throwable: Throwable? = null) :
            WorkoutError("Failed to load workouts from server", technicalDetails, throwable)
            
        class CadenceSourceError(technicalDetails: String? = null, throwable: Throwable? = null) :
            WorkoutError("Bluetooth cadence source error", technicalDetails, throwable)
    }

    sealed class DataError(message: String, technicalDetails: String? = null, throwable: Throwable? = null) :
        VibeAndRideError(message, technicalDetails, throwable) {
        
        class NetworkUnavailable(throwable: Throwable? = null) :
            DataError("Network unavailable. Please check your connection.", null, throwable)
            
        class DatabaseError(technicalDetails: String? = null, throwable: Throwable? = null) :
            DataError("Database operation failed", technicalDetails, throwable)
            
        class Unauthorized(technicalDetails: String? = null, throwable: Throwable? = null) :
            DataError("Unauthorized access to data", technicalDetails, throwable)
    }
}
