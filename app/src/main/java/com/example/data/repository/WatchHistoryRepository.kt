package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.HistorySyncStatus
import com.example.data.model.WatchHistoryItem
import com.example.data.remote.FirebaseService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository managing user-scoped Watch History with offline-first Room persistence
 * and cloud synchronization via Firestore: users/{userId}/watchHistory/{historyId}.
 *
 * Enforces strict user isolation:
 * - Local records are partitioned by userId
 * - Cloud sync targets only users/{authUid}/watchHistory
 * - Demo accounts and anonymous users operate purely locally without Firestore writes
 * - Logout immediately cancels pending sync operations and clears in-memory state
 */
class WatchHistoryRepository(
    private val context: Context,
    private val firebaseService: FirebaseService,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    private val isDebug: Boolean = com.example.BuildConfig.DEBUG
) {
    private val dao = database.watchHistoryDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _historyItems = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val historyItems: StateFlow<List<WatchHistoryItem>> = _historyItems.asStateFlow()

    private val _syncStatus = MutableStateFlow(HistorySyncStatus.LOCAL)
    val syncStatus: StateFlow<HistorySyncStatus> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    val syncErrorMessage: StateFlow<String?> = _syncErrorMessage.asStateFlow()

    @Volatile
    private var activeUserId: String? = null

    // Track last cloud write timestamp per contentId to prevent excessive writes (min 10s throttle)
    private val lastCloudWriteTimes = ConcurrentHashMap<String, Long>()
    private val MIN_CLOUD_WRITE_INTERVAL_MS = 10_000L

    private var activeSyncJob: Job? = null

    fun isDemoUser(uid: String?): Boolean {
        if (uid == null) return false
        return uid == "super_admin_01" || uid == "creator_demo_01" || uid == "viewer_demo_01"
    }

    /**
     * Called when a user logs in or auth state changes.
     */
    fun onUserLogin(userId: String) {
        if (userId.isBlank()) return
        if (activeUserId == userId && _historyItems.value.isNotEmpty()) return

        // If switching accounts, cancel active operations and clear in-memory state
        if (activeUserId != null && activeUserId != userId) {
            clearActiveUser()
        }

        activeUserId = userId
        lastCloudWriteTimes.clear()

        scope.launch {
            // 1. Load local history immediately (offline-first)
            loadLocalHistory(userId)

            // 2. If genuine Firebase user (not demo), trigger cloud synchronization
            if (!isDemoUser(userId)) {
                syncWithCloud(userId)
            } else {
                _syncStatus.value = HistorySyncStatus.LOCAL
            }
        }
    }

    /**
     * Called on logout or account switch.
     */
    fun onUserLogout() {
        clearActiveUser()
    }

    /**
     * Resets repository memory state on account switch or logout.
     */
    fun clearActiveUser() {
        activeSyncJob?.cancel()
        activeSyncJob = null
        activeUserId = null
        lastCloudWriteTimes.clear()
        _historyItems.value = emptyList()
        _syncStatus.value = HistorySyncStatus.LOCAL
        _isSyncing.value = false
        _syncErrorMessage.value = null
    }

    /**
     * Loads user-scoped history records from local Room cache.
     */
    suspend fun loadLocalHistory(userId: String): List<WatchHistoryItem> {
        val entities = dao.getHistoryForUser(userId)
        val items = entities.map { it.toDomainModel() }
        if (activeUserId == userId) {
            val current = _historyItems.value.filter { it.userId == userId }
            val merged = (current + items).distinctBy { it.historyId }.sortedByDescending { it.watchedAt }
            _historyItems.value = merged
            return merged
        }
        return items
    }

    /**
     * Records or updates a watch event (Video or Short).
     *
     * Validates inputs, saves immediately to local Room cache, and conditionally throttles
     * cloud sync writes to Firestore.
     */
    fun recordWatch(
        userId: String,
        contentType: String,
        contentId: String,
        progressMs: Long,
        durationMs: Long,
        title: String = "",
        channelName: String = "",
        thumbnailUrl: String = "",
        videoUrl: String = "",
        forceCloudSync: Boolean = false
    ) {
        // Reject invalid inputs or nonexistent content markers
        if (userId.isBlank() || contentId.isBlank() || contentType.isBlank()) {
            Log.w("WatchHistoryRepo", "Rejected invalid watch record: userId=$userId, contentId=$contentId, contentType=$contentType")
            return
        }
        val normalizedType = contentType.trim().uppercase()
        if (normalizedType != "VIDEO" && normalizedType != "SHORT") {
            Log.w("WatchHistoryRepo", "Rejected invalid contentType: $normalizedType")
            return
        }

        val historyId = WatchHistoryItem.createDeterministicId(normalizedType, contentId)
        val now = System.currentTimeMillis()
        val safeProgress = progressMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val isCompleted = safeDuration > 0 && safeProgress >= (safeDuration * 0.9f).toLong()

        val isDemo = isDemoUser(userId)
        val initialStatus = if (isDemo) HistorySyncStatus.LOCAL else HistorySyncStatus.SYNCING

        val item = WatchHistoryItem(
            historyId = historyId,
            userId = userId,
            contentType = normalizedType,
            contentId = contentId,
            watchedAt = now,
            progressMs = safeProgress,
            durationMs = safeDuration,
            completed = isCompleted,
            updatedAt = now,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            videoUrl = videoUrl,
            syncStatus = initialStatus
        )

        // 1. Immediately update in-memory state synchronously if active user
        if (activeUserId == userId) {
            val currentList = _historyItems.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.historyId == historyId }
            if (existingIndex >= 0) {
                currentList[existingIndex] = item
            } else {
                currentList.add(0, item)
            }
            currentList.sortByDescending { it.watchedAt }
            _historyItems.value = currentList
        }

        scope.launch {
            // 2. Persist to Room local database
            dao.upsertHistory(item.toEntity())

            // 3. Cloud write handling:
            if (!isDemo && activeUserId == userId) {
                val lastWrite = lastCloudWriteTimes[contentId] ?: 0L
                val elapsed = now - lastWrite

                if (forceCloudSync || elapsed >= MIN_CLOUD_WRITE_INTERVAL_MS || isCompleted) {
                    lastCloudWriteTimes[contentId] = now
                    val success = firebaseService.syncWatchHistoryItem(userId, item)
                    if (success) {
                        dao.updateSyncStatus(userId, historyId, HistorySyncStatus.SYNCED.name)
                        updateItemSyncStatusInMemory(historyId, HistorySyncStatus.SYNCED)
                        _syncStatus.value = HistorySyncStatus.SYNCED
                    } else {
                        dao.updateSyncStatus(userId, historyId, HistorySyncStatus.SYNC_FAILED.name)
                        updateItemSyncStatusInMemory(historyId, HistorySyncStatus.SYNC_FAILED)
                        _syncStatus.value = HistorySyncStatus.SYNC_FAILED
                    }
                }
            }
        }
    }

    /**
     * Gets saved progress (in milliseconds) for a piece of content.
     */
    suspend fun getProgress(userId: String, contentType: String, contentId: String): Long {
        if (userId.isBlank() || contentId.isBlank()) return 0L
        val inMemory = _historyItems.value.firstOrNull {
            it.userId == userId && it.contentType.equals(contentType, ignoreCase = true) && it.contentId == contentId
        }
        if (inMemory != null) return inMemory.progressMs
        val entity = dao.getHistoryItem(userId, contentType.trim().uppercase(), contentId.trim())
        return entity?.progressMs ?: 0L
    }

    /**
     * Full bidirectional synchronization with Firestore cloud subcollection.
     *
     * Merge rules:
     * - Scoped strictly to `users/{userId}/watchHistory`
     * - Newest `watchedAt` wins for matching items
     * - Cloud items missing locally are written to Room as SYNCED
     * - Local items missing or newer in cloud are uploaded
     * - Zero fake records generated
     * - Gracefully falls back to LOCAL on network error
     */
    suspend fun syncWithCloud(userId: String): Boolean {
        if (userId.isBlank() || isDemoUser(userId)) {
            _syncStatus.value = HistorySyncStatus.LOCAL
            return false
        }

        activeSyncJob?.cancel()
        return coroutineScope {
            val job = async {
                _isSyncing.value = true
                _syncStatus.value = HistorySyncStatus.SYNCING
                _syncErrorMessage.value = null

                try {
                    // 1. Fetch cloud records (limited to 50 items for Spark quota efficiency)
                    val cloudResult = firebaseService.fetchWatchHistory(userId, pageSize = 50)
                    val cloudItems = cloudResult.items

                    // 2. Fetch local records from Room
                    val localEntities = dao.getHistoryForUser(userId, limit = 100)
                    val localMap = localEntities.associateBy { it.historyId }.toMutableMap()

                    val mergedMap = HashMap<String, WatchHistoryItem>()

                    // Process cloud records
                    for (cloudItem in cloudItems) {
                        val localEntity = localMap[cloudItem.historyId]
                        if (localEntity == null) {
                            // Cloud item not present locally -> insert to Room
                            val itemToSave = cloudItem.copy(syncStatus = HistorySyncStatus.SYNCED)
                            dao.upsertHistory(itemToSave.toEntity())
                            mergedMap[cloudItem.historyId] = itemToSave
                        } else {
                            // Both exist: compare timestamps to determine winner
                            if (cloudItem.watchedAt >= localEntity.watchedAt) {
                                // Cloud record is newer or equal
                                val resolvedItem = cloudItem.copy(
                                    title = if (cloudItem.title.isNotBlank()) cloudItem.title else localEntity.title,
                                    channelName = if (cloudItem.channelName.isNotBlank()) cloudItem.channelName else localEntity.channelName,
                                    thumbnailUrl = if (cloudItem.thumbnailUrl.isNotBlank()) cloudItem.thumbnailUrl else localEntity.thumbnailUrl,
                                    videoUrl = if (cloudItem.videoUrl.isNotBlank()) cloudItem.videoUrl else localEntity.videoUrl,
                                    syncStatus = HistorySyncStatus.SYNCED
                                )
                                dao.upsertHistory(resolvedItem.toEntity())
                                mergedMap[cloudItem.historyId] = resolvedItem
                            } else {
                                // Local record is newer (e.g. offline watch) -> upload to cloud
                                val localModel = localEntity.toDomainModel()
                                firebaseService.syncWatchHistoryItem(userId, localModel)
                                dao.updateSyncStatus(userId, localEntity.historyId, HistorySyncStatus.SYNCED.name)
                                mergedMap[cloudItem.historyId] = localModel.copy(syncStatus = HistorySyncStatus.SYNCED)
                            }
                            localMap.remove(cloudItem.historyId)
                        }
                    }

                    // Process remaining local-only items
                    for ((_, unsyncedEntity) in localMap) {
                        val unsyncedModel = unsyncedEntity.toDomainModel()
                        val uploadSuccess = firebaseService.syncWatchHistoryItem(userId, unsyncedModel)
                        if (uploadSuccess) {
                            dao.updateSyncStatus(userId, unsyncedEntity.historyId, HistorySyncStatus.SYNCED.name)
                            mergedMap[unsyncedEntity.historyId] = unsyncedModel.copy(syncStatus = HistorySyncStatus.SYNCED)
                        } else {
                            dao.updateSyncStatus(userId, unsyncedEntity.historyId, HistorySyncStatus.SYNC_FAILED.name)
                            mergedMap[unsyncedEntity.historyId] = unsyncedModel.copy(syncStatus = HistorySyncStatus.SYNC_FAILED)
                        }
                    }

                    // Update memory state with merged, deduplicated, sorted list
                    val sortedList = mergedMap.values.sortedByDescending { it.watchedAt }
                    _historyItems.value = sortedList
                    _syncStatus.value = HistorySyncStatus.SYNCED
                    true
                } catch (e: Exception) {
                    Log.w("WatchHistoryRepo", "Sync with cloud failed: ${e.message}")
                    _syncStatus.value = HistorySyncStatus.SYNC_FAILED
                    _syncErrorMessage.value = "Failed to sync history with cloud: ${e.message ?: "Network error"}"
                    // Local items remain active and accessible
                    loadLocalHistory(userId)
                    false
                } finally {
                    _isSyncing.value = false
                }
            }
            activeSyncJob = job
            job.await()
        }
    }

    /**
     * Retry failed sync.
     */
    fun retrySync() {
        val uid = activeUserId ?: return
        scope.launch {
            syncWithCloud(uid)
        }
    }

    /**
     * Deletes an individual history item from local Room and cloud.
     */
    suspend fun deleteItem(userId: String, historyId: String): Boolean {
        if (userId.isBlank() || historyId.isBlank()) return false
        dao.deleteHistoryItem(userId, historyId)

        val currentList = _historyItems.value.filterNot { it.historyId == historyId }
        _historyItems.value = currentList

        if (!isDemoUser(userId)) {
            scope.launch {
                firebaseService.deleteWatchHistoryItem(userId, historyId)
            }
        }
        return true
    }

    /**
     * Clears all watch history for the user locally and on cloud.
     *
     * If cloud deletion fails:
     * - local history is cleared
     * - does NOT falsely report complete synchronization
     * - sets sync state to SYNC_FAILED with error message
     */
    suspend fun clearHistory(userId: String): Boolean {
        if (userId.isBlank()) return false

        // 1. Clear local Room database for this user
        dao.clearHistoryForUser(userId)
        _historyItems.value = emptyList()

        // 2. Clear cloud documents if authenticated real user
        if (!isDemoUser(userId)) {
            _syncStatus.value = HistorySyncStatus.SYNCING
            val cloudSuccess = firebaseService.clearWatchHistory(userId)
            if (cloudSuccess) {
                _syncStatus.value = HistorySyncStatus.SYNCED
                _syncErrorMessage.value = null
                return true
            } else {
                _syncStatus.value = HistorySyncStatus.SYNC_FAILED
                _syncErrorMessage.value = "Local history cleared, but cloud deletion encountered an error. Tap to retry."
                return false
            }
        } else {
            _syncStatus.value = HistorySyncStatus.LOCAL
            return true
        }
    }

    private fun updateItemSyncStatusInMemory(historyId: String, status: HistorySyncStatus) {
        val current = _historyItems.value.toMutableList()
        val idx = current.indexOfFirst { it.historyId == historyId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(syncStatus = status)
            _historyItems.value = current
        }
    }

    private fun WatchHistoryEntity.toDomainModel(): WatchHistoryItem = WatchHistoryItem(
        historyId = historyId,
        userId = userId,
        contentType = contentType,
        contentId = contentId,
        watchedAt = watchedAt,
        progressMs = progressMs,
        durationMs = durationMs,
        completed = completed,
        updatedAt = updatedAt,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        videoUrl = videoUrl,
        syncStatus = try { HistorySyncStatus.valueOf(syncStatus) } catch (e: Exception) { HistorySyncStatus.LOCAL }
    )

    private fun WatchHistoryItem.toEntity(): WatchHistoryEntity = WatchHistoryEntity(
        historyId = historyId,
        userId = userId,
        contentType = contentType,
        contentId = contentId,
        watchedAt = watchedAt,
        progressMs = progressMs,
        durationMs = durationMs,
        completed = completed,
        updatedAt = updatedAt,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        videoUrl = videoUrl,
        syncStatus = syncStatus.name
    )
}
