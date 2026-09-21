package com.example.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_events")
data class OfflineEventEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val eventId: String,
    val userId: String,
    val contentId: String,
    val contentType: String,
    val eventType: String,
    val watchDurationSeconds: Long,
    val totalDurationSeconds: Long,
    val completionPercentage: Float,
    val searchQuery: String,
    val category: String,
    val timestamp: Long,
    val isSynced: Boolean = false
)

@Dao
interface OfflineEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: OfflineEventEntity): Long

    @Query("SELECT * FROM offline_events WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT 50")
    suspend fun getUnsyncedEvents(): List<OfflineEventEntity>

    @Query("UPDATE offline_events SET isSynced = 1 WHERE localId IN (:ids)")
    suspend fun markEventsSynced(ids: List<Long>)

    @Query("DELETE FROM offline_events WHERE isSynced = 1")
    suspend fun clearSyncedEvents()
}

@Entity(tableName = "cached_videos")
data class CachedVideoEntity(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val channelName: String,
    val channelHandle: String,
    val channelAvatarUrl: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val category: String,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface FeedCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<CachedVideoEntity>)

    @Query("SELECT * FROM cached_videos ORDER BY cachedAt DESC LIMIT 30")
    fun getCachedFeed(): Flow<List<CachedVideoEntity>>

    @Query("DELETE FROM cached_videos")
    suspend fun clearCache()
}

@Entity(
    tableName = "watch_history",
    primaryKeys = ["userId", "contentType", "contentId"]
)
data class WatchHistoryEntity(
    val historyId: String,
    val userId: String,
    val contentType: String,
    val contentId: String,
    val watchedAt: Long,
    val progressMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val syncStatus: String // "LOCAL", "SYNCING", "SYNCED", "SYNC_FAILED"
)

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(item: WatchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistoryList(items: List<WatchHistoryEntity>)

    @Query("SELECT * FROM watch_history WHERE userId = :userId ORDER BY watchedAt DESC LIMIT :limit")
    suspend fun getHistoryForUser(userId: String, limit: Int = 100): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE userId = :userId AND contentType = :contentType AND contentId = :contentId LIMIT 1")
    suspend fun getHistoryItem(userId: String, contentType: String, contentId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE userId = :userId AND syncStatus IN ('LOCAL', 'SYNC_FAILED')")
    suspend fun getUnsyncedItems(userId: String): List<WatchHistoryEntity>

    @Query("UPDATE watch_history SET syncStatus = :status WHERE userId = :userId AND historyId = :historyId")
    suspend fun updateSyncStatus(userId: String, historyId: String, status: String)

    @Query("DELETE FROM watch_history WHERE userId = :userId")
    suspend fun clearHistoryForUser(userId: String)

    @Query("DELETE FROM watch_history WHERE userId = :userId AND historyId = :historyId")
    suspend fun deleteHistoryItem(userId: String, historyId: String)
}

@Database(entities = [OfflineEventEntity::class, CachedVideoEntity::class, WatchHistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): OfflineEventDao
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iombg_local_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
