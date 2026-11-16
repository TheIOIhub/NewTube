package com.example.newtube.repository

/**
 * Remembers where the user stopped watching each video so playback can resume
 * on the next visit.
 */
interface PlaybackPositionRepository {
    suspend fun positionFor(videoId: String): Long

    suspend fun savePosition(videoId: String, positionMs: Long)

    /** Deletes every stored resume position; called when the user clears history. */
    suspend fun clearAll()
}
