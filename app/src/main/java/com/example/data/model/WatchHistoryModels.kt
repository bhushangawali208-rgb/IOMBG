package com.example.data.model

/**
 * Watch History synchronization models for IOMBG.
 *
 * Scoped strictly to the authenticated user's private cloud subcollection:
 * users/{uid}/watchHistory/{historyId}
 */

enum class HistorySyncStatus {
    LOCAL,
    SYNCING,
    SYNCED,
    SYNC_FAILED
}

data class WatchHistoryItem(
    val historyId: String = "",
    val userId: String = "",
    val contentType: String = "VIDEO", // "VIDEO" or "SHORT"
    val contentId: String = "",
    val watchedAt: Long = 0L,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val completed: Boolean = false,
    val updatedAt: Long = 0L,
    // Content presentation metadata cached locally and in doc for fast listing
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val videoUrl: String = "",
    val syncStatus: HistorySyncStatus = HistorySyncStatus.SYNCED
) {
    companion object {
        fun createDeterministicId(contentType: String, contentId: String): String {
            val sanitizedType = contentType.trim().lowercase()
            val sanitizedId = contentId.trim()
            return "${sanitizedType}_${sanitizedId}"
        }
    }
}

sealed class WatchHistoryUiState {
    object Loading : WatchHistoryUiState()
    data class Success(
        val items: List<WatchHistoryItem>,
        val syncStatus: HistorySyncStatus,
        val errorMessage: String? = null
    ) : WatchHistoryUiState()
    object Empty : WatchHistoryUiState()
    data class Error(val message: String) : WatchHistoryUiState()
}
