package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.OfflineEventEntity
import com.example.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

/**
 * Intelligent Personalized Recommendation Engine for IOMBG.
 * Supports dynamic configurable weights, multi-signal candidate scoring,
 * category & creator affinities, diversity interleaving, cold-start handling,
 * emerging creator discovery, and dedicated Shorts ranking.
 */
class RecommendationEngine(private val context: Context) {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.d("RecommendationEngine", "Firestore fallback: ${e.message}")
            null
        }
    }
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val scope = CoroutineScope(Dispatchers.IO)

    // Configurable ranking weights (tunable at runtime or from backend)
    private val _weights = MutableStateFlow(RecommendationWeights())
    val weights: StateFlow<RecommendationWeights> = _weights.asStateFlow()

    private val _shortsWeights = MutableStateFlow(ShortsRecommendationWeights())
    val shortsWeights: StateFlow<ShortsRecommendationWeights> = _shortsWeights.asStateFlow()

    // Dynamic User Affinities
    private val _categoryAffinities = MutableStateFlow<Map<String, Float>>(emptyMap())
    val categoryAffinities: StateFlow<Map<String, Float>> = _categoryAffinities.asStateFlow()

    private val _creatorAffinities = MutableStateFlow<Map<String, Float>>(emptyMap())
    val creatorAffinities: StateFlow<Map<String, Float>> = _creatorAffinities.asStateFlow()

    // Cold Start & User Chosen Topics
    private val _userInterests = MutableStateFlow<Set<String>>(emptySet())
    val userInterests: StateFlow<Set<String>> = _userInterests.asStateFlow()

    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount.asStateFlow()

    // History counters
    private val watchCountMap = mutableMapOf<String, Int>() // contentId -> count
    private val categorySkipCount = mutableMapOf<String, Int>() // category -> consecutive skips
    private val creatorSkipCount = mutableMapOf<String, Int>() // creatorId -> consecutive skips

    init {
        loadConfigFromRemote()
    }

    private fun loadConfigFromRemote() {
        scope.launch {
            try {
                firestore?.collection("platform_config")?.document("recommendation_weights")
                    ?.addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null && snapshot.exists()) {
                            val remoteWeights = snapshot.toObject(RecommendationWeights::class.java)
                            if (remoteWeights != null) {
                                _weights.value = remoteWeights
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.d("RecommendationEngine", "Using default recommendation weights: ${e.message}")
            }
        }
    }

    fun updateWeights(newWeights: RecommendationWeights) {
        _weights.value = newWeights
    }

    fun updateShortsWeights(newShortsWeights: ShortsRecommendationWeights) {
        _shortsWeights.value = newShortsWeights
    }

    fun setUserInterests(categories: Set<String>) {
        _userInterests.value = categories
        val currentCats = _categoryAffinities.value.toMutableMap()
        categories.forEach { cat ->
            currentCats[cat] = max(currentCats[cat] ?: 1.0f, 4.0f)
        }
        _categoryAffinities.value = currentCats
    }

    fun toggleUserInterest(category: String) {
        val current = _userInterests.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        setUserInterests(current)
    }

    /**
     * Record interaction event and update real-time recommendation signals.
     */
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
        val completionRatio = if (totalDurationSeconds > 0) {
            (watchDurationSeconds.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val isRewatch = (watchCountMap[contentId] ?: 0) > 1

        val event = RecommendationEvent(
            eventId = "evt_${UUID.randomUUID()}",
            userId = userId.ifBlank { "anonymous" },
            contentId = contentId,
            contentType = contentType,
            eventType = eventType,
            timestamp = System.currentTimeMillis(),
            watchDuration = watchDurationSeconds,
            watchDurationSeconds = watchDurationSeconds,
            totalDurationSeconds = totalDurationSeconds,
            completionPercentage = completionRatio,
            contentCategory = category,
            category = category,
            creatorId = creatorId,
            searchQuery = searchQuery,
            tags = tags,
            isRewatch = isRewatch
        )

        // Update in-memory signals & affinities
        processEventSignals(event)

        // Buffer locally in Room & sync to Firestore
        scope.launch {
            try {
                db.eventDao().insertEvent(
                    OfflineEventEntity(
                        eventId = event.eventId,
                        userId = event.userId,
                        contentId = event.contentId,
                        contentType = event.contentType,
                        eventType = event.eventType.name,
                        watchDurationSeconds = event.watchDurationSeconds,
                        totalDurationSeconds = event.totalDurationSeconds,
                        completionPercentage = event.completionPercentage,
                        searchQuery = event.searchQuery,
                        category = event.category,
                        timestamp = event.timestamp
                    )
                )

                firestore?.collection("recommendationEvents")?.document(event.eventId)?.set(event)
            } catch (e: Exception) {
                Log.d("RecommendationEngine", "Buffering event: ${e.message}")
            }
        }
    }

    private fun processEventSignals(event: RecommendationEvent) {
        _eventCount.value = _eventCount.value + 1
        val w = _weights.value
        val cat = event.category.ifBlank { "Entertainment" }
        val creator = event.creatorId

        val currentCatMap = _categoryAffinities.value.toMutableMap()
        val currentCreatorMap = _creatorAffinities.value.toMutableMap()

        when (event.eventType) {
            EventType.COMPLETION -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + (w.completionWeight * 1.5f)
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.completionWeight * 1.2f)
                categorySkipCount[cat] = 0
                if (creator.isNotBlank()) creatorSkipCount[creator] = 0
            }
            EventType.WATCH_TIME -> {
                val durationDelta = (event.watchDuration / 20f).coerceAtMost(4.0f) * (w.watchTimeWeight / 3.0f)
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + durationDelta
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (durationDelta * 0.8f)
                categorySkipCount[cat] = 0
                if (creator.isNotBlank()) creatorSkipCount[creator] = 0
            }
            EventType.LIKE -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.likeWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.likeWeight * 1.5f)
            }
            EventType.UNLIKE, EventType.DISLIKE -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) - (w.likeWeight * 0.8f)
            }
            EventType.COMMENT, EventType.REPLY -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.commentWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.commentWeight * 1.2f)
            }
            EventType.FOLLOW -> {
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.followWeight * 2.5f)
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + (w.followWeight * 1.5f)
            }
            EventType.UNFOLLOW -> {
                if (creator.isNotBlank()) currentCreatorMap[creator] = max(0f, (currentCreatorMap[creator] ?: 0f) - (w.followWeight * 2.0f))
            }
            EventType.SHARE -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.shareWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.shareWeight * 1.2f)
            }
            EventType.SAVE -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.saveWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.saveWeight * 1.2f)
            }
            EventType.SEARCH -> {
                if (event.searchQuery.isNotBlank()) {
                    currentCatMap.keys.forEach { c ->
                        if (c.contains(event.searchQuery, ignoreCase = true)) {
                            currentCatMap[c] = (currentCatMap[c] ?: 1.0f) + w.searchMatchWeight
                        }
                    }
                }
            }
            EventType.SKIP -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.skipPenaltyWeight
                categorySkipCount[cat] = (categorySkipCount[cat] ?: 0) + 1
                if (creator.isNotBlank()) {
                    creatorSkipCount[creator] = (creatorSkipCount[creator] ?: 0) + 1
                    currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.skipPenaltyWeight * 0.8f)
                }
            }
            EventType.NOT_INTERESTED -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.notInterestedPenaltyWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + w.notInterestedPenaltyWeight
            }
            EventType.REPORT -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.reportPenaltyWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + w.reportPenaltyWeight
            }
            EventType.PLAY, EventType.VIDEO_OPEN, EventType.WATCH_START -> {
                watchCountMap[event.contentId] = (watchCountMap[event.contentId] ?: 0) + 1
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + 0.8f
            }
            EventType.CLICK, EventType.IMPRESSION, EventType.LIVE_IMPRESSION -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + 0.05f
            }
            EventType.LIVE_JOIN, EventType.LIVE_OPEN, EventType.LIVE_WATCH -> {
                watchCountMap[event.contentId] = (watchCountMap[event.contentId] ?: 0) + 1
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + 1.0f
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + 1.5f
            }
            EventType.LIVE_LIKE -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.likeWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.likeWeight * 1.5f)
            }
            EventType.LIVE_CHAT -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.commentWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.commentWeight * 1.5f)
            }
            EventType.LIVE_SUPER_SUPPORT -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + 3.0f
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + 5.0f
            }
            EventType.LIVE_SHARE -> {
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + w.shareWeight
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (w.shareWeight * 1.5f)
            }
            EventType.LIVE_WATCH_TIME -> {
                val durationDelta = (event.watchDuration / 30f).coerceAtMost(5.0f) * (w.watchTimeWeight / 2.5f)
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + durationDelta
                if (creator.isNotBlank()) currentCreatorMap[creator] = (currentCreatorMap[creator] ?: 0f) + (durationDelta * 1.0f)
            }
            else -> {
                // Generic engagement event
                currentCatMap[cat] = (currentCatMap[cat] ?: 1.0f) + 0.1f
            }
        }

        _categoryAffinities.value = currentCatMap
        _creatorAffinities.value = currentCreatorMap
    }

    /**
     * Score, personalize, rank, and apply diversity interleaving to candidate videos.
     */
    fun scoreAndRankVideos(
        videos: List<Video>,
        followedChannels: Set<String> = emptySet(),
        notInterestedIds: Set<String> = emptySet(),
        hiddenChannelIds: Set<String> = emptySet(),
        blockedUserIds: Set<String> = emptySet(),
        filterCategory: String = "All",
        searchQuery: String = ""
    ): List<Video> {
        val w = _weights.value
        val catAffinities = _categoryAffinities.value
        val creatAffinities = _creatorAffinities.value
        val interests = _userInterests.value
        val isColdStart = _eventCount.value < 3 && interests.isEmpty()

        // 1. Filter out safety violations and hidden content
        val eligibleVideos = videos.filter { v ->
            !notInterestedIds.contains(v.videoId) &&
            !hiddenChannelIds.contains(v.channelId) &&
            !blockedUserIds.contains(v.ownerUid)
        }

        // 2. Direct category filter if user explicitly selected a category tab
        val filtered = if (filterCategory.isBlank() || filterCategory == "All") {
            eligibleVideos
        } else {
            eligibleVideos.filter { it.category.equals(filterCategory, ignoreCase = true) }
        }

        if (filtered.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()

        // 3. Score each video using multi-signal formula
        val scoredList = filtered.map { video ->
            val catAffinity = catAffinities[video.category] ?: if (interests.contains(video.category)) 3.5f else 1.0f
            val creatorAffinity = creatAffinities[video.channelId] ?: 0.0f
            val isFollowed = followedChannels.contains(video.channelId)

            // Signal 1: Category Preference Affinity
            val categoryScore = catAffinity * w.categoryAffinityWeight

            // Signal 2: Creator Preference & Follows
            val creatorScore = creatorAffinity * w.creatorAffinityWeight + (if (isFollowed) w.followWeight * 6.0f else 0.0f)

            // Signal 3: Trending Velocity (Views, Likes, Comments, Shares)
            val velocity = (video.viewCount * 0.04f) +
                    (video.likeCount * w.likeWeight * 0.5f) +
                    (video.commentCount * w.commentWeight * 0.6f) +
                    (video.shareCount * w.shareWeight * 0.8f)
            val trendingScore = velocity * (w.trendingVelocityWeight * 0.05f)

            // Signal 4: Freshness / Recency Boost (decay with age in hours)
            val ageHours = max(0.1, (now - video.createdAt) / 3600000.0)
            val recencyScore = (100.0f / (ageHours.toFloat() + 2.0f)) * w.recencyFreshnessWeight

            // Signal 5: Small Creator Discovery Opportunity (boost emerging channels)
            val isEmergingCreator = video.viewCount < 100000L
            val emergingBoost = if (isEmergingCreator) w.smallCreatorBoostWeight * 12.0f else 0.0f

            // Signal 6: Repeated Viewing / Rewatch re-engagement
            val rewatchCount = watchCountMap[video.videoId] ?: 0
            val rewatchScore = if (rewatchCount in 1..2) w.repeatedViewWeight * 3.0f else 0.0f

            // Signal 7: Search Query Match (if active)
            val searchScore = if (searchQuery.isNotBlank()) {
                var s = 0f
                if (video.title.contains(searchQuery, ignoreCase = true)) s += w.searchMatchWeight * 15f
                if (video.description.contains(searchQuery, ignoreCase = true)) s += w.searchMatchWeight * 8f
                if (video.tags.any { it.contains(searchQuery, ignoreCase = true) }) s += w.searchMatchWeight * 10f
                s
            } else 0f

            // Signal 8: Consecutive Skip Penalties
            val catSkips = categorySkipCount[video.category] ?: 0
            val creatorSkips = creatorSkipCount[video.channelId] ?: 0
            val skipPenalty = (catSkips * 3.0f + creatorSkips * 5.0f)

            // Cold Start Blend
            val compositeScore = if (isColdStart) {
                // Cold start: prioritize trending velocity, freshness, emerging creators, balanced popularity
                (trendingScore * 1.5f) + (recencyScore * 1.8f) + emergingBoost + (if (isFollowed) 30f else 0f)
            } else {
                // Personalized: full hybrid scoring
                categoryScore + creatorScore + trendingScore + recencyScore + emergingBoost + rewatchScore + searchScore - skipPenalty
            }

            val finalScore = compositeScore * (if (video.isBoosted) 2.2f else 1.0f)
            Pair(video, finalScore)
        }

        // 4. Sort candidates by score
        val sortedCandidates = scoredList.sortedByDescending { it.second }.map { it.first }

        // If category is strictly filtered or search active, return directly without interleaving
        if ((filterCategory.isNotBlank() && filterCategory != "All") || searchQuery.isNotBlank()) {
            return sortedCandidates
        }

        // 5. Diversity & Exploration Re-ranking Pass (prevent category monopolies)
        return applyDiversityInterleaving(sortedCandidates, w.consecutiveSameCategoryLimit)
    }

    /**
     * Interleaves diverse categories to guarantee that no single category dominates the feed
     * and users get exploration opportunities.
     */
    private fun applyDiversityInterleaving(candidates: List<Video>, maxConsecutive: Int): List<Video> {
        if (candidates.size <= 2) return candidates

        val result = mutableListOf<Video>()
        val pool = candidates.toMutableList()
        var lastCategory = ""
        var consecutiveCount = 0

        while (pool.isNotEmpty()) {
            var selectedIndex = 0

            if (consecutiveCount >= maxConsecutive && lastCategory.isNotEmpty()) {
                // Find next candidate with a different category
                val diffIndex = pool.indexOfFirst { !it.category.equals(lastCategory, ignoreCase = true) }
                if (diffIndex != -1) {
                    selectedIndex = diffIndex
                }
            }

            val item = pool.removeAt(selectedIndex)
            result.add(item)

            if (item.category.equals(lastCategory, ignoreCase = true)) {
                consecutiveCount++
            } else {
                lastCategory = item.category
                consecutiveCount = 1
            }
        }

        return result
    }

    /**
     * Dedicated Shorts Recommendation Algorithm.
     * Evaluates completion, watch duration, rewatches, quick skips, likes, shares, and follows.
     */
    fun scoreAndRankShorts(
        shorts: List<ShortItem>,
        followedChannels: Set<String> = emptySet(),
        notInterestedIds: Set<String> = emptySet(),
        hiddenChannelIds: Set<String> = emptySet(),
        blockedUserIds: Set<String> = emptySet(),
        filterCategory: String = "All"
    ): List<ShortItem> {
        val sw = _shortsWeights.value
        val catAffinities = _categoryAffinities.value
        val creatAffinities = _creatorAffinities.value

        val eligible = shorts.filter { short ->
            !notInterestedIds.contains(short.shortId) &&
            !hiddenChannelIds.contains(short.channelId) &&
            !blockedUserIds.contains(short.ownerUid) &&
            (filterCategory == "All" || filterCategory.isBlank() || short.category.equals(filterCategory, ignoreCase = true))
        }

        if (eligible.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()

        return eligible.sortedByDescending { short ->
            val catAffinity = catAffinities[short.category] ?: 1.0f
            val creatorAffinity = creatAffinities[short.channelId] ?: 0f
            val isFollowed = followedChannels.contains(short.channelId)

            // Category & Creator Signals
            val catScore = catAffinity * sw.categoryAffinityWeight
            val creatorScore = creatorAffinity * sw.creatorAffinityWeight + (if (isFollowed) sw.followWeight * 5f else 0f)

            // Engagement metrics
            val engagement = (short.viewCount * 0.05f) +
                    (short.likeCount * sw.likeWeight * 0.6f) +
                    (short.shareCount * sw.shareWeight * 0.8f) +
                    (short.commentCount * 2.0f)

            // Recency & Small creator discovery
            val ageHours = max(0.1, (now - short.createdAt) / 3600000.0)
            val recency = (80.0f / (ageHours.toFloat() + 2.0f))
            val emergingBoost = if (short.viewCount < 50000L) sw.smallCreatorBoostWeight * 10f else 0f

            // Skip penalties
            val catSkips = categorySkipCount[short.category] ?: 0
            val creatorSkips = creatorSkipCount[short.channelId] ?: 0
            val skipPenalty = (catSkips * 4.0f + creatorSkips * 6.0f)

            val totalScore = catScore + creatorScore + engagement + recency + emergingBoost - skipPenalty
            totalScore * (if (short.isBoosted) 2.0f else 1.0f)
        }
    }

    /**
     * Recommend creators for discovery based on category affinity, engagement,
     * watched history, and emerging-creator exposure.
     */
    fun recommendCreators(
        channels: List<Channel>,
        followedChannels: Set<String> = emptySet(),
        blockedUserIds: Set<String> = emptySet()
    ): List<CreatorRecommendation> {
        val catAffinities = _categoryAffinities.value
        val topCategories = catAffinities.entries.sortedByDescending { it.value }.map { it.key }

        return channels
            .filter { ch -> !followedChannels.contains(ch.channelId) && !blockedUserIds.contains(ch.ownerUid) }
            .map { channel ->
                val catAffinity = catAffinities[channel.category] ?: 1.0f
                val isEmerging = channel.subscriberCount < 5000L

                // Score channels based on topic match + engagement + emerging creator discovery bonus
                val matchScore = (catAffinity * 20.0f) +
                        (channel.subscriberCount * 0.005f) +
                        (channel.totalViews * 0.0001f) +
                        (if (isEmerging) 25.0f else 5.0f)

                val reason = when {
                    isEmerging -> "Rising Star Creator"
                    topCategories.contains(channel.category) -> "Top Creator in ${channel.category}"
                    else -> "Trending Creator"
                }

                CreatorRecommendation(
                    channel = channel,
                    matchScore = matchScore,
                    matchedCategory = channel.category,
                    isEmergingCreator = isEmerging,
                    reason = reason
                )
            }
            .sortedByDescending { it.matchScore }
            .take(10)
    }
}
