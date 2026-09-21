package com.example.data.repository

import android.content.Context
import com.example.data.model.*
import com.example.data.remote.IombgBackendService
import com.example.data.remote.IombgBackendServiceImpl
import com.example.data.remote.RecommendationEngine
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * RecommendationEventRepository: High-performance event ingestion buffer & ranking engine.
 *
 * Implements client-side batching and debouncing of analytics telemetry (impressions, clicks,
 * watch time, video plays) before flushing to server-authoritative backend functions.
 * This avoids excessive Firestore document writes and ensures low latency.
 */
class RecommendationEventRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore? = null,
    val engine: RecommendationEngine = RecommendationEngine(context),
    private val backendService: IombgBackendService = IombgBackendServiceImpl()
) {
    val categoryWeights: StateFlow<Map<String, Float>> = engine.categoryAffinities
    val creatorAffinities: StateFlow<Map<String, Float>> = engine.creatorAffinities
    val userInterests: StateFlow<Set<String>> = engine.userInterests
    val eventCount: StateFlow<Int> = engine.eventCount
    val weights: StateFlow<RecommendationWeights> = engine.weights
    val shortsWeights: StateFlow<ShortsRecommendationWeights> = engine.shortsWeights

    private val eventQueue = ConcurrentLinkedQueue<Map<String, Any>>()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private var lastFlushTime = System.currentTimeMillis()

    fun recordEvent(
        userId: String,
        contentId: String,
        contentType: String = "VIDEO",
        eventType: EventType,
        watchDurationSeconds: Long = 0,
        totalDurationSeconds: Long = 0,
        searchQuery: String = "",
        category: String = "",
        creatorId: String = "",
        tags: List<String> = emptyList()
    ) {
        // 1. Update on-device recommendation scoring model
        engine.recordEvent(
            userId = userId,
            contentId = contentId,
            contentType = contentType,
            eventType = eventType,
            watchDurationSeconds = watchDurationSeconds,
            totalDurationSeconds = totalDurationSeconds,
            searchQuery = searchQuery,
            category = category,
            creatorId = creatorId,
            tags = tags
        )

        // 2. Queue event for batched backend ingestion
        val eventPayload = hashMapOf<String, Any>(
            "userId" to userId,
            "contentId" to contentId,
            "contentType" to contentType,
            "isShort" to (contentType.equals("SHORT", ignoreCase = true) || contentId.contains("short", ignoreCase = true)),
            "eventType" to eventType.name,
            "watchDurationSeconds" to watchDurationSeconds,
            "channelId" to creatorId,
            "timestamp" to System.currentTimeMillis()
        )
        eventQueue.add(eventPayload)

        // 3. Debounced batch flush (flushes if queue reaches 10 items or every 30 seconds)
        if (eventQueue.size >= 10 || System.currentTimeMillis() - lastFlushTime > 30000) {
            flushBatchToBackend()
        }
    }

    private fun flushBatchToBackend() {
        val batchList = mutableListOf<Map<String, Any>>()
        while (batchList.size < 25 && eventQueue.isNotEmpty()) {
            val item = eventQueue.poll()
            if (item != null) batchList.add(item)
        }
        if (batchList.isNotEmpty()) {
            lastFlushTime = System.currentTimeMillis()
            repositoryScope.launch {
                try {
                    backendService.ingestAnalyticsBatch(batchList)
                } catch (_: Exception) {}
            }
        }
    }

    fun setUserInterests(categories: Set<String>) {
        engine.setUserInterests(categories)
    }

    fun toggleUserInterest(category: String) {
        engine.toggleUserInterest(category)
    }

    fun scoreAndRankVideos(
        videos: List<Video>,
        followedChannels: Set<String> = emptySet(),
        notInterestedIds: Set<String> = emptySet(),
        hiddenChannelIds: Set<String> = emptySet(),
        blockedUserIds: Set<String> = emptySet(),
        filterCategory: String = "All",
        searchQuery: String = ""
    ): List<Video> {
        return engine.scoreAndRankVideos(
            videos = videos,
            followedChannels = followedChannels,
            notInterestedIds = notInterestedIds,
            hiddenChannelIds = hiddenChannelIds,
            blockedUserIds = blockedUserIds,
            filterCategory = filterCategory,
            searchQuery = searchQuery
        )
    }

    fun scoreAndRankShorts(
        shorts: List<ShortItem>,
        followedChannels: Set<String> = emptySet(),
        notInterestedIds: Set<String> = emptySet(),
        hiddenChannelIds: Set<String> = emptySet(),
        blockedUserIds: Set<String> = emptySet(),
        filterCategory: String = "All"
    ): List<ShortItem> {
        return engine.scoreAndRankShorts(
            shorts = shorts,
            followedChannels = followedChannels,
            notInterestedIds = notInterestedIds,
            hiddenChannelIds = hiddenChannelIds,
            blockedUserIds = blockedUserIds,
            filterCategory = filterCategory
        )
    }

    fun recommendCreators(
        channels: List<Channel>,
        followedChannels: Set<String> = emptySet(),
        blockedUserIds: Set<String> = emptySet()
    ): List<CreatorRecommendation> {
        return engine.recommendCreators(channels, followedChannels, blockedUserIds)
    }
}
